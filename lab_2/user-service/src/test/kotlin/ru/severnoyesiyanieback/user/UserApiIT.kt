package ru.severnoyesiyanieback.user

import jakarta.validation.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import ru.severnoyesiyanieback.user.dto.CreateUserRequest
import ru.severnoyesiyanieback.user.dto.UpdateUserRequest
import ru.severnoyesiyanieback.user.dto.UserResponse
import ru.severnoyesiyanieback.user.entity.User
import ru.severnoyesiyanieback.user.entity.UserRole
import ru.severnoyesiyanieback.user.exception.UserAlreadyExistsException
import ru.severnoyesiyanieback.user.repository.UserRepository
import ru.severnoyesiyanieback.user.service.UserService
import java.time.Duration

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.config.name=application-test", "spring.cloud.config.enabled=false", "eureka.client.enabled=false"]
)
class UserApiIT {
    @Autowired private lateinit var environment: Environment
    @Autowired private lateinit var repository: UserRepository
    @Autowired private lateinit var service: UserService
    @Autowired private lateinit var database: DatabaseClient
    private lateinit var client: WebTestClient

    @BeforeEach
    fun resetUsers() {
        repository.deleteAll().block(Duration.ofSeconds(10))
        client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:${environment.getRequiredProperty("local.server.port")}")
            .responseTimeout(Duration.ofSeconds(10)).build()
    }

    @Test
    fun `user CRUD retains response fields status codes and client role`() {
        val created = create("anna@example.com")
        assertEquals(UserRole.CLIENT, created.role)
        client.get().uri("/api/users/${created.idUser}").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.name").isEqualTo("Anna")
        client.put().uri("/api/users/${created.idUser}").bodyValue(UpdateUserRequest("Renamed", "new@example.com"))
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.name").isEqualTo("Renamed")
            .jsonPath("$.email").isEqualTo("new@example.com").jsonPath("$.role").isEqualTo("CLIENT")
        client.delete().uri("/internal/users/${created.idUser}").exchange().expectStatus().isNoContent
        client.get().uri("/api/users/${created.idUser}").exchange().expectStatus().isNotFound
    }

    @Test
    fun `duplicate emails return conflict and failed update leaves the user unchanged`() {
        create("taken@example.com")
        val other = create("other@example.com")
        client.post().uri("/api/users").bodyValue(CreateUserRequest("Other", "taken@example.com"))
            .exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.message").exists()
        client.put().uri("/api/users/${other.idUser}").bodyValue(UpdateUserRequest("Changed", "taken@example.com"))
            .exchange().expectStatus().isEqualTo(409)
        client.get().uri("/api/users/${other.idUser}").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.name").isEqualTo("Anna").jsonPath("$.email").isEqualTo("other@example.com")
        assertEquals(2L, repository.count().block())
    }

    @Test
    fun `missing resources return the original error body`() {
        client.get().uri("/api/users/2147483647").exchange().expectStatus().isNotFound
            .expectBody().jsonPath("$.message").isEqualTo("User with id 2147483647 does not exist")
        client.put().uri("/api/users/2147483647").bodyValue(UpdateUserRequest("Anna", "anna@example.com"))
            .exchange().expectStatus().isNotFound
        client.delete().uri("/internal/users/2147483647").exchange().expectStatus().isNotFound
    }

    @Test
    fun `invalid requests return readable errors and create no rows`() {
        for (request in listOf(CreateUserRequest("", "valid@example.com"), CreateUserRequest("Anna", "broken"),
            CreateUserRequest("x".repeat(51), "valid@example.com"))) {
            client.post().uri("/api/users").bodyValue(request).exchange().expectStatus().isBadRequest
                .expectBody().jsonPath("$.message").exists()
        }
        for (body in listOf("{", "{}", """{"name":"Anna","email":null}""")) {
            client.post().uri("/api/users").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isBadRequest.expectBody().jsonPath("$.message").exists()
        }
        client.get().uri("/api/users/not-a-number").exchange().expectStatus().isBadRequest
            .expectBody().jsonPath("$.message").exists()
        assertEquals(0L, repository.count().block())
    }

    @Test
    fun `employee update preserves role and enums are stored as strings`() {
        val employee = repository.save(User(name = "Worker", email = "worker@example.com", role = UserRole.WORKER)).block()!!
        client.put().uri("/api/users/${employee.idUser}").bodyValue(UpdateUserRequest("Changed", employee.email))
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.role").isEqualTo("WORKER")
        val storedRole = database.sql("SELECT role FROM users WHERE id_user = :id").bind("id", employee.idUser!!)
            .map { row, _ -> row.get("role", String::class.java)!! }.one().block()
        assertEquals("WORKER", storedRole)
    }

    @Test
    fun `concurrent inserts enforce unique email using the database constraint`() {
        val signals = Flux.merge(
            service.createUser(CreateUserRequest("One", "race@example.com")).materialize(),
            service.createUser(CreateUserRequest("Two", "race@example.com")).materialize()
        ).collectList().block(Duration.ofSeconds(10))!!
        assertEquals(1, signals.count { it.isOnNext })
        assertEquals(1, signals.count { it.throwable is UserAlreadyExistsException })
        assertEquals(1L, repository.count().block())
    }

    @Test
    fun `entity validation runs for direct repository writes`() {
        StepVerifier.create(repository.save(User(name = "", email = "broken")))
            .expectError(ConstraintViolationException::class.java).verify()
        assertEquals(0L, repository.count().block())
    }

    @Test
    fun `OpenAPI uses the gateway origin and hides internal deletion`() {
        val body = client.get().uri("/v3/api-docs").exchange().expectStatus().isOk.expectBody(String::class.java)
            .returnResult().responseBody!!
        assertFalse(body.contains("/internal/users"))
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.servers[0].url").isEqualTo("/")
    }

    private fun create(email: String): UserResponse = client.post().uri("/api/users")
        .bodyValue(CreateUserRequest("Anna", email)).exchange().expectStatus().isCreated
        .expectBody(UserResponse::class.java).returnResult().responseBody!!

    companion object {
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
