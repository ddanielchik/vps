package ru.severnoyesiyanieback.user

import jakarta.validation.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import ru.severnoyesiyanieback.user.dto.CreateUserRequest
import ru.severnoyesiyanieback.user.dto.LoginRequest
import ru.severnoyesiyanieback.user.dto.LoginResponse
import ru.severnoyesiyanieback.user.dto.UpdateUserRequest
import ru.severnoyesiyanieback.user.dto.UserResponse
import ru.severnoyesiyanieback.user.entity.User
import ru.severnoyesiyanieback.user.entity.UserRole
import ru.severnoyesiyanieback.user.entity.UserStatus
import ru.severnoyesiyanieback.user.exception.UserAlreadyExistsException
import ru.severnoyesiyanieback.user.repository.UserRepository
import ru.severnoyesiyanieback.user.security.LordBootstrap
import ru.severnoyesiyanieback.user.service.UserService
import java.time.Duration

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.config.name=application-test", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "app.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "app.lord.email=supervisor@example.com", "app.lord.password=integration supervisor password"
    ]
)
class UserApiIT {
    @Autowired private lateinit var environment: Environment
    @Autowired private lateinit var repository: UserRepository
    @Autowired private lateinit var service: UserService
    @Autowired private lateinit var database: DatabaseClient
    @Autowired private lateinit var bootstrap: LordBootstrap
    @Autowired private lateinit var encoder: PasswordEncoder
    @Autowired private lateinit var jwtDecoder: ReactiveJwtDecoder
    private lateinit var guest: WebTestClient
    private lateinit var client: WebTestClient

    @BeforeEach
    fun resetUsers() {
        repository.deleteAll().block(Duration.ofSeconds(10))
        bootstrap.run(DefaultApplicationArguments())
        guest = WebTestClient.bindToServer()
            .baseUrl("http://localhost:${environment.getRequiredProperty("local.server.port")}")
            .responseTimeout(Duration.ofSeconds(10)).build()
        client = guest.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${login(LORD_EMAIL, LORD_PASSWORD)}").build()
    }

    @Test
    fun `create read and update retain response fields and client role`() {
        val created = create("anna@example.com")
        assertEquals(UserRole.CLIENT, created.role)
        client.get().uri("/api/v1/users/${created.idUser}").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.name").isEqualTo("Anna")
        client.put().uri("/api/v1/users/${created.idUser}").bodyValue(UpdateUserRequest("Renamed", "new@example.com"))
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.name").isEqualTo("Renamed")
            .jsonPath("$.email").isEqualTo("new@example.com").jsonPath("$.role").isEqualTo("CLIENT")
    }

    @Test
    fun `delete blocks the same account and PUT can restore it`() {
        val user = create("anna@example.com")
        val userPath = "/api/v1/users/${user.idUser}"
        assertEquals(UserStatus.ACTIVE, user.status)

        repeat(2) {
            client.delete().uri(userPath).exchange().expectStatus().isNoContent.expectBody().isEmpty
        }

        val blocked = user.copy(status = UserStatus.BLOCKED)
        client.get().uri(userPath).exchange().expectStatus().isOk
            .expectBody(UserResponse::class.java).isEqualTo(blocked)
        val listedUsers = client.get().uri("/api/v1/users").exchange().expectStatus().isOk
            .expectBodyList(UserResponse::class.java).returnResult().responseBody
        assertEquals(listOf(blocked), listedUsers?.filter { it.role != UserRole.LORD })
        assertEquals(UserStatus.BLOCKED, repository.findById(user.idUser).block()?.status)
        client.post().uri("/api/v1/users").bodyValue(CreateUserRequest("Other", user.email, CLIENT_PASSWORD))
            .exchange().expectStatus().isEqualTo(409)

        client.put().uri(userPath).bodyValue(UpdateUserRequest(user.name, user.email))
            .exchange().expectStatus().isOk.expectBody(UserResponse::class.java).isEqualTo(blocked)
        client.put().uri(userPath).bodyValue(UpdateUserRequest(user.name, user.email, UserStatus.ACTIVE))
            .exchange().expectStatus().isOk.expectBody(UserResponse::class.java).isEqualTo(user)
        assertEquals(UserStatus.ACTIVE, repository.findById(user.idUser).block()?.status)
        assertEquals(1L, countClients())
    }

    @Test
    fun `duplicate emails return conflict and failed update leaves the user unchanged`() {
        create("taken@example.com")
        val other = create("other@example.com")
        client.post().uri("/api/v1/users").bodyValue(CreateUserRequest("Other", "taken@example.com", CLIENT_PASSWORD))
            .exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.message").exists()
        client.put().uri("/api/v1/users/${other.idUser}").bodyValue(UpdateUserRequest("Changed", "taken@example.com"))
            .exchange().expectStatus().isEqualTo(409)
        client.get().uri("/api/v1/users/${other.idUser}").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.name").isEqualTo("Anna").jsonPath("$.email").isEqualTo("other@example.com")
        assertEquals(2L, countClients())
    }

    @Test
    fun `missing resources return the original error body`() {
        client.get().uri("/api/v1/users/2147483647").exchange().expectStatus().isNotFound
            .expectBody().jsonPath("$.message").isEqualTo("User with id 2147483647 does not exist")
        client.put().uri("/api/v1/users/2147483647").bodyValue(UpdateUserRequest("Anna", "anna@example.com"))
            .exchange().expectStatus().isNotFound
        client.delete().uri("/api/v1/users/2147483647").exchange().expectStatus().isNotFound
    }

    @Test
    fun `invalid requests return readable errors and create no rows`() {
        for (request in listOf(CreateUserRequest("", "valid@example.com", CLIENT_PASSWORD), CreateUserRequest("Anna", "broken", CLIENT_PASSWORD),
            CreateUserRequest("x".repeat(51), "valid@example.com", CLIENT_PASSWORD))) {
            client.post().uri("/api/v1/users").bodyValue(request).exchange().expectStatus().isBadRequest
                .expectBody().jsonPath("$.message").exists()
        }
        for (body in listOf("{", "{}", """{"name":"Anna","email":"anna@example.com"}""")) {
            client.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isBadRequest.expectBody().jsonPath("$.message").exists()
        }
        client.get().uri("/api/v1/users/not-a-number").exchange().expectStatus().isBadRequest
            .expectBody().jsonPath("$.message").exists()
        assertEquals(0L, countClients())
    }

    @Test
    fun `employee update preserves role and enums are stored as strings`() {
        val employee = repository.save(User(name = "Worker", email = "worker@example.com", role = UserRole.WORKER)).block()!!
        client.put().uri("/api/v1/users/${employee.idUser}").bodyValue(UpdateUserRequest("Changed", employee.email))
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.role").isEqualTo("WORKER")
        val storedRole = database.sql("SELECT role FROM users WHERE id_user = :id").bind("id", employee.idUser!!)
            .map { row, _ -> row.get("role", String::class.java)!! }.one().block()
        assertEquals("WORKER", storedRole)
    }

    @Test
    fun `concurrent inserts enforce unique email using the database constraint`() {
        val signals = Flux.merge(
            service.createUser(CreateUserRequest("One", "race@example.com", CLIENT_PASSWORD)).materialize(),
            service.createUser(CreateUserRequest("Two", "race@example.com", CLIENT_PASSWORD)).materialize()
        ).collectList().block(Duration.ofSeconds(10))!!
        assertEquals(1, signals.count { it.isOnNext })
        assertEquals(1, signals.count { it.throwable is UserAlreadyExistsException })
        assertEquals(1L, countClients())
    }

    @Test
    fun `entity validation runs for direct repository writes`() {
        StepVerifier.create(repository.save(User(name = "", email = "broken")))
            .expectError(ConstraintViolationException::class.java).verify()
        assertEquals(0L, countClients())
    }

    @Test
    fun `OpenAPI uses the gateway origin and exposes deletion`() {
        val body = client.get().uri("/v3/api-docs").exchange().expectStatus().isOk.expectBody(String::class.java)
            .returnResult().responseBody!!
        assertTrue(body.contains("/api/v1/users"))
        assertTrue(body.contains("/api/v1/auth/login"))
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.servers[0].url").isEqualTo("/")
            .jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer")
            .jsonPath("$['paths']['/api/v1/users']['post']['security'][0]['bearerAuth']").exists()
    }

    @Test
    fun `only an active LORD with a valid token can create a user`() {
        val request = CreateUserRequest("Guest", "guest@example.com", CLIENT_PASSWORD)
        guest.post().uri("/api/v1/users").bodyValue(request).exchange().expectStatus().isUnauthorized

        client.post().uri("/api/v1/users").bodyValue(request).exchange().expectStatus().isCreated
            .expectBody().jsonPath("$.role").isEqualTo("CLIENT")
            .jsonPath("$.password").doesNotExist().jsonPath("$.passwordHash").doesNotExist()
        val saved = repository.findByEmail(request.email).block()!!
        val hash = requireNotNull(saved.passwordHash)
        assertNotEquals(CLIENT_PASSWORD, hash)
        assertTrue(encoder.matches(CLIENT_PASSWORD, hash))

        val customerToken = login(saved.email, CLIENT_PASSWORD)
        val customerJwt = jwtDecoder.decode(customerToken).block()!!
        assertEquals(saved.idUser.toString(), customerJwt.subject)
        assertEquals("CLIENT", customerJwt.getClaimAsString("role"))
        val another = request.copy(email = "second@example.com")
        guest.post().uri("/api/v1/users").headers { it.setBearerAuth(customerToken) }
            .bodyValue(another).exchange().expectStatus().isForbidden

        val supervisorToken = login(LORD_EMAIL, LORD_PASSWORD)
        val signature = supervisorToken.substringAfterLast('.')
        val changedCharacter = if (signature.first() == 'A') 'B' else 'A'
        val tamperedToken = supervisorToken.substringBeforeLast('.') + "." + changedCharacter + signature.drop(1)
        guest.post().uri("/api/v1/users").headers { it.setBearerAuth(tamperedToken) }
            .bodyValue(another).exchange().expectStatus().isUnauthorized

        val supervisor = repository.findByEmail(LORD_EMAIL).block()!!
        repository.save(supervisor.copy(status = UserStatus.BLOCKED)).block()
        guest.post().uri("/api/v1/users").headers { it.setBearerAuth(supervisorToken) }
            .bodyValue(another).exchange().expectStatus().isForbidden
        assertEquals(null, repository.findByEmail(another.email).block())
    }

    @Test
    fun `a lord creates staff and only the owner or a lord reads the profile`() {
        val request = CreateUserRequest("Waiter", "waiter@example.com", CLIENT_PASSWORD, UserRole.WAITER)
        val employee = client.post().uri("/api/v1/users").bodyValue(request)
            .exchange().expectStatus().isCreated.expectBody(UserResponse::class.java).returnResult().responseBody!!
        assertEquals(UserRole.WAITER, employee.role)
        val token = login(employee.email, CLIENT_PASSWORD)
        val employeeClient = guest.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token").build()

        guest.get().uri("/api/v1/users").exchange().expectStatus().isUnauthorized
        employeeClient.get().uri("/api/v1/users").exchange().expectStatus().isForbidden
        employeeClient.get().uri("/api/v1/users/${employee.idUser}")
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.passwordHash").doesNotExist()
        val supervisorId = repository.findByEmail(LORD_EMAIL).block()!!.idUser
        employeeClient.get().uri("/api/v1/users/$supervisorId").exchange().expectStatus().isForbidden
        employeeClient.delete().uri("/api/v1/users/$supervisorId").exchange().expectStatus().isForbidden
    }

    @Test
    fun `login rejects unknown addresses incorrect passwords and blocked accounts`() {
        val customer = create("anna@example.com")
        fun rejected(email: String, password: String) {
            guest.post().uri("/api/v1/auth/login").bodyValue(LoginRequest(email, password))
                .exchange().expectStatus().isUnauthorized.expectBody()
                .jsonPath("$.message").isEqualTo("Invalid email or password")
                .jsonPath("$.accessToken").doesNotExist()
        }

        rejected("absent@example.com", CLIENT_PASSWORD)
        rejected(customer.email, "incorrect example password")
        val stored = repository.findByEmail(customer.email).block()!!
        repository.save(stored.copy(status = UserStatus.BLOCKED)).block()
        rejected(customer.email, CLIENT_PASSWORD)
        repository.save(User(name = "Legacy", email = "legacy@example.com")).block()
        rejected("legacy@example.com", CLIENT_PASSWORD)
    }

    private fun countClients(): Long = database.sql("SELECT count(*) FROM users WHERE role <> 'LORD'")
        .map { row, _ -> row.get(0, Long::class.javaObjectType)!! }.one().block()!!

    private fun login(email: String, password: String): String = guest.post().uri("/api/v1/auth/login")
        .bodyValue(LoginRequest(email, password)).exchange().expectStatus().isOk
        .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
        .expectBody(LoginResponse::class.java).returnResult().responseBody!!.accessToken

    private fun create(email: String): UserResponse = client.post().uri("/api/v1/users")
        .bodyValue(CreateUserRequest("Anna", email, CLIENT_PASSWORD)).exchange().expectStatus().isCreated
        .expectBody(UserResponse::class.java).returnResult().responseBody!!

    companion object {
        private const val LORD_EMAIL = "supervisor@example.com"
        private const val LORD_PASSWORD = "integration supervisor password"
        private const val CLIENT_PASSWORD = "integration customer password"

        @Container @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("users_test").withUsername("test").withPassword("test")

        @DynamicPropertySource @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}" }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
        }
    }
}
