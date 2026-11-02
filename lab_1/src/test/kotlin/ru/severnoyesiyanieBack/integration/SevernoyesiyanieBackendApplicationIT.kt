package ru.severnoyesiyanieBack.integration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SevernoyesiyanieBackendApplicationIT : ApiIntegrationSupport() {
    @Autowired
    private lateinit var flyway: Flyway

    @Test
    fun `application starts against PostgreSQL with all Flyway migrations applied`() {
        assertEquals("cocktail_bar_test", jdbc.queryForObject("SELECT current_database()", String::class.java))
        assertEquals(listOf("1", "2"), flyway.info().applied().map { it.version.toString() })
        assertTrue(flyway.validateWithResult().validationSuccessful)
        assertEquals(500, jdbc.queryForObject(
            "SELECT character_maximum_length FROM information_schema.columns WHERE table_name = 'menu' AND column_name = 'ingredients'",
            Int::class.java
        ))
        assertEquals(8, jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'
            AND table_name IN ('users', 'visits', 'menu', 'taste_tags', 'orders', 'order_items',
                               'menu_taste_tags', 'visit_taste_preferences')
            """.trimIndent(),
            Int::class.java
        ))
    }

    @Test
    fun `shared OpenAPI document exposes every controller`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openapi").isNotEmpty)
            .andExpect(jsonPath("$.paths['/api/users']").exists())
            .andExpect(jsonPath("$.paths['/api/menu']").exists())
            .andExpect(jsonPath("$.paths['/api/visits']").exists())
            .andExpect(jsonPath("$.paths['/api/orders']").exists())
    }
}
