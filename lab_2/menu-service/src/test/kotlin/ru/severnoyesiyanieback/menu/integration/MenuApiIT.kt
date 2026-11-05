package ru.severnoyesiyanieback.menu.integration

import jakarta.validation.ConstraintViolationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.severnoyesiyanieback.menu.dto.CreateMenuItemRequest
import ru.severnoyesiyanieback.menu.dto.MenuItemResponse
import ru.severnoyesiyanieback.menu.dto.TasteTagResponse
import ru.severnoyesiyanieback.menu.entity.MenuItem
import ru.severnoyesiyanieback.menu.repository.MenuItemRepository
import java.math.BigDecimal
import java.time.Duration

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.cloud.config.enabled=false", "spring.config.name=application-test", "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false"
    ]
)
@Import(MenuApiIT.Database::class)
class MenuApiIT {
    @TestConfiguration(proxyBeanMethods = false)
    class Database {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("menu_test").withUsername("test").withPassword("test")
    }

    @LocalServerPort
    private var port: Int = 0
    @Autowired
    private lateinit var jdbc: JdbcTemplate
    @Autowired
    private lateinit var repository: MenuItemRepository
    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager
    private lateinit var client: WebTestClient

    @BeforeEach
    fun connectToServer() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(10)).build()
    }

    @Test
    fun `Flyway preserves the original twenty eight cocktails and five tastes`() {
        assertEquals(28, jdbc.queryForObject("SELECT count(*) FROM menu WHERE id_position <= 28", Int::class.java))
        assertEquals(listOf("BITTER", "SALTY", "SOUR", "SPICY", "SWEET"), jdbc.queryForList("SELECT name FROM taste_tags ORDER BY name", String::class.java))
        client.get().uri("/api/menu/1").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.name").isEqualTo("Mojito")
            .jsonPath("$.price").isEqualTo(490.0)
            .jsonPath("$.ingredients").isEqualTo("Ром, лимон, коричневый сахар, содовая, мята")
            .jsonPath("$.tasteTags[0]").isEqualTo("SOUR")
            .jsonPath("$.tasteTags[1]").isEqualTo("SWEET")
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Int::class.java))
    }

    @Test
    fun `create read update and internal delete retain the REST contract`() {
        val created = create("Integration cocktail")
        assertEquals(listOf("SOUR", "SWEET"), created.tasteTags)
        client.get().uri("/api/menu/${created.idPosition}").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.name").isEqualTo("Integration cocktail")
        val update = CreateMenuItemRequest("Changed cocktail", BigDecimal("250.00"), "Tonic", setOf(3))
        client.put().uri("/api/menu/${created.idPosition}").bodyValue(update).exchange().expectStatus().isOk
            .expectBody().jsonPath("$.name").isEqualTo("Changed cocktail")
            .jsonPath("$.price").isEqualTo(250.0)
            .jsonPath("$.ingredients").isEqualTo("Tonic")
            .jsonPath("$.tasteTags[0]").isEqualTo("BITTER")
            .jsonPath("$.tasteTags.length()").isEqualTo(1)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM menu_taste_tags WHERE id_position = ?", Int::class.java, created.idPosition))
        client.delete().uri("/internal/menu/${created.idPosition}").exchange().expectStatus().isNoContent
            .expectBody().isEmpty
        client.get().uri("/api/menu/${created.idPosition}").exchange().expectStatus().isNotFound
            .expectBody().jsonPath("$.message").isEqualTo("Cocktail with id ${created.idPosition} not found")
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM menu_taste_tags WHERE id_position = ?", Int::class.java, created.idPosition))
    }

    @Test
    fun `menu pagination is a slice of ten sorted entries without total count`() {
        client.get().uri("/api/menu?page=0").exchange().expectStatus().isOk
            .expectHeader().doesNotExist("X-Total-Count")
            .expectBody().jsonPath("$.content.length()").isEqualTo(10)
            .jsonPath("$.content[0].idPosition").isEqualTo(1)
            .jsonPath("$.content[9].idPosition").isEqualTo(10)
            .jsonPath("$.last").isEqualTo(false)
            .jsonPath("$.totalElements").doesNotExist()
        client.get().uri("/api/menu?page=1000").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.last").isEqualTo(true)
        client.get().uri("/api/menu?page=-1").exchange().expectStatus().isBadRequest
            .expectBody().jsonPath("$.message").isEqualTo("Page must be non-negative")
    }

    @Test
    fun `invalid request fields and malformed input produce readable errors`() {
        val valid = CreateMenuItemRequest("Valid", BigDecimal.ONE, "Lime", setOf(1))
        for (request in listOf(
            valid.copy(name = ""), valid.copy(name = "x".repeat(51)),
            valid.copy(price = BigDecimal("-0.01")), valid.copy(ingredients = ""),
            valid.copy(ingredients = "x".repeat(501)), valid.copy(tasteTagIds = emptySet())
        )) {
            client.post().uri("/api/menu").bodyValue(request).exchange().expectStatus().isBadRequest
                .expectBody().jsonPath("$.message").isNotEmpty
        }
        client.post().uri("/api/menu").contentType(MediaType.APPLICATION_JSON).bodyValue("{")
            .exchange().expectStatus().isBadRequest.expectBody().jsonPath("$.message").isNotEmpty
        client.get().uri("/api/menu?page=bad").exchange().expectStatus().isBadRequest
            .expectBody().jsonPath("$.message").isNotEmpty
    }

    @Test
    fun `zero price and ingredients up to five hundred characters can be persisted`() {
        for (length in listOf(256, 500)) {
            val ingredients = "a".repeat(length)
            val created = requireNotNull(
                client.post().uri("/api/menu")
                    .bodyValue(CreateMenuItemRequest("Long recipe", BigDecimal.ZERO, ingredients, setOf(1)))
                    .exchange().expectStatus().isCreated
                    .expectBody(MenuItemResponse::class.java).returnResult().responseBody
            )
            assertEquals(0, created.price.compareTo(BigDecimal.ZERO))
            assertEquals(ingredients, created.ingredients)
            assertEquals(ingredients, jdbc.queryForObject(
                "SELECT ingredients FROM menu WHERE id_position = ?", String::class.java, created.idPosition
            ))
        }
    }

    @Test
    fun `unknown tags reject creation and updates without changing stored state`() {
        val created = create("Rollback cocktail")
        val countBefore = jdbc.queryForObject("SELECT count(*) FROM menu", Int::class.java)
        val invalid = CreateMenuItemRequest("Invalid tags", BigDecimal.TEN, "Changed ingredients", setOf(99999))
        client.post().uri("/api/menu").bodyValue(invalid).exchange().expectStatus().isBadRequest
            .expectBody(String::class.java).isEqualTo("Some specified taste tags were not found")
        assertEquals(countBefore, jdbc.queryForObject("SELECT count(*) FROM menu", Int::class.java))
        client.put().uri("/api/menu/${created.idPosition}").bodyValue(invalid).exchange().expectStatus().isBadRequest
        client.get().uri("/api/menu/${created.idPosition}").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.name").isEqualTo(created.name)
            .jsonPath("$.ingredients").isEqualTo(created.ingredients)
            .jsonPath("$.tasteTags.length()").isEqualTo(2)
    }

    @Test
    fun `database failure rolls back modified item and taste relationships`() {
        val created = create("Transaction cocktail")
        // NUMERIC(10, 2) overflow happens at flush after the entity and collection were changed.
        val overflow = CreateMenuItemRequest("Must roll back", BigDecimal("999999999999"), "Changed", setOf(3))
        client.put().uri("/api/menu/${created.idPosition}").bodyValue(overflow).exchange()
            .expectStatus().is5xxServerError
        client.get().uri("/api/menu/${created.idPosition}").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.name").isEqualTo(created.name)
            .jsonPath("$.price").isEqualTo(125.5)
            .jsonPath("$.tasteTags[0]").isEqualTo("SOUR")
            .jsonPath("$.tasteTags[1]").isEqualTo("SWEET")
    }

    @Test
    fun `entity constraints also reject invalid persistence outside controller`() {
        val countBefore = jdbc.queryForObject("SELECT count(*) FROM menu", Int::class.java)
        assertThrows(ConstraintViolationException::class.java) {
            TransactionTemplate(transactionManager).execute {
                repository.saveAndFlush(MenuItem(name = "", price = BigDecimal.ONE, ingredients = "Lime"))
            }
        }
        assertEquals(countBefore, jdbc.queryForObject("SELECT count(*) FROM menu", Int::class.java))
    }

    @Test
    fun `internal lookups validate tag identities and return distinct sorted recommendations`() {
        val tags = requireNotNull(client.get().uri("/internal/menu/tags?ids=2&ids=1").exchange().expectStatus().isOk
            .expectBodyList(TasteTagResponse::class.java).returnResult().responseBody)
        assertEquals(listOf(TasteTagResponse(1, "SWEET"), TasteTagResponse(2, "SOUR")), tags)
        client.get().uri("/internal/menu/tags").exchange().expectStatus().isOk.expectBody().json("[]")
        client.get().uri("/internal/menu/tags?ids=1&ids=99999").exchange().expectStatus().isBadRequest
            .expectBody(String::class.java).isEqualTo("Some specified taste tags were not found")
        client.get().uri("/internal/menu/recommendations").exchange().expectStatus().isOk.expectBody().json("[]")
        val recommendations = requireNotNull(client.get().uri("/internal/menu/recommendations?tastes=SWEET&tastes=SOUR")
            .exchange().expectStatus().isOk.expectBodyList(MenuItemResponse::class.java).returnResult().responseBody)
        val ids = recommendations.map { it.idPosition }
        assertTrue(ids.isNotEmpty())
        assertTrue(ids.size <= 50)
        assertEquals(ids.sorted().distinct(), ids)
        assertTrue(recommendations.all { item -> item.tasteTags.any { it in setOf("SWEET", "SOUR") } })
    }

    @Test
    fun `recommendation query enforces the fifty item limit`() {
        jdbc.execute("""
            WITH inserted AS (
                INSERT INTO menu (name_product, price, ingredients)
                SELECT 'Limit cocktail ' || n, 1, 'Lime' FROM generate_series(1, 55) n
                RETURNING id_position
            )
            INSERT INTO menu_taste_tags (id_position, id_tag) SELECT id_position, 5 FROM inserted
        """.trimIndent())
        client.get().uri("/internal/menu/recommendations?tastes=SPICY").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.length()").isEqualTo(50)
    }

    @Test
    fun `missing resources return 404 for update and internal delete`() {
        val update = CreateMenuItemRequest("Missing", BigDecimal.ONE, "Lime", setOf(1))
        client.put().uri("/api/menu/99999").bodyValue(update).exchange().expectStatus().isNotFound
            .expectBody().jsonPath("$.message").isEqualTo("Cocktail with id 99999 not found")
        client.delete().uri("/internal/menu/99999").exchange().expectStatus().isNotFound
    }

    @Test
    fun `OpenAPI exposes public menu API with relative server and hides internal operations`() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.servers[0].url").isEqualTo("/")
            .jsonPath("$.paths['/api/menu']").exists()
            .jsonPath("$.paths['/internal/menu/tags']").doesNotExist()
            .jsonPath("$.paths['/internal/menu/{idPosition}']").doesNotExist()
    }

    private fun create(name: String): MenuItemResponse = requireNotNull(
        client.post().uri("/api/menu")
            .bodyValue(CreateMenuItemRequest(name, BigDecimal("125.50"), "Gin, lime", setOf(1, 2)))
            .exchange().expectStatus().isCreated.expectBody(MenuItemResponse::class.java).returnResult().responseBody
    )
}
