package ru.severnoyesiyanieBack.integration

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.severnoyesiyanieBack.support.TestcontainersConfiguration
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
abstract class ApiIntegrationSupport {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var jdbc: JdbcTemplate

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun resetFixtures() {
        // @ServiceConnection points this context exclusively at its disposable test container.
        check(jdbc.queryForObject("SELECT current_database()", String::class.java) == "cocktail_bar_test")
        jdbc.execute(
            """
            TRUNCATE order_items, orders, visit_taste_preferences, visits,
                     menu_taste_tags, menu, taste_tags, users RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        jdbc.update("INSERT INTO taste_tags (name) VALUES ('SWEET'), ('SOUR'), ('BITTER')")
    }

    protected fun json(result: ResultActions): JsonNode =
        objectMapper.readTree(result.andReturn().response.contentAsString)

    protected fun createClient(email: String = "client@example.com"): Int = json(
        mockMvc.perform(
            post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Guest","email":"$email"}""")
        ).andExpect(status().isCreated)
    )["idUser"].asInt()

    protected fun createVisit(clientId: Int = createClient()): Int = json(
        mockMvc.perform(
            post("/api/visits").contentType(MediaType.APPLICATION_JSON)
                .content("""{"clientId":$clientId,"tableNumber":4}""")
        ).andExpect(status().isCreated)
    )["idVisit"].asInt()

    protected fun createMenuItem(name: String = "Northern Lights", price: String = "125.50", tags: String = "1,2"): Int = json(
        mockMvc.perform(
            post("/api/menu").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","price":$price,"ingredients":"Gin, lime","tasteTagIds":[$tags]}""")
        ).andExpect(status().isCreated)
    )["idPosition"].asInt()

    protected fun createStaff(role: String): Int = requireNotNull(
        jdbc.queryForObject(
            "INSERT INTO users (name, email, role) VALUES (?, ?, ?) RETURNING id_user",
            Int::class.java,
            role,
            "${role.lowercase()}@example.com",
            role
        )
    )

    protected fun count(table: String): Int = requireNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java))
}
