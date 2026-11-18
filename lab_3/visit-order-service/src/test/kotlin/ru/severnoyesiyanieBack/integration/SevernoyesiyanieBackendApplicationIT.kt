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
    @Autowired private lateinit var flyway: Flyway

    @Test
    fun `application owns only visits orders and their local associations`() {
        assertEquals(listOf("1", "2"), flyway.info().applied().map { it.version.toString() })
        assertTrue(flyway.validateWithResult().validationSuccessful)
        assertEquals(4, jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'
            AND table_name IN ('visits', 'orders', 'order_items', 'visit_taste_preferences')
        """.trimIndent(), Int::class.java))
        assertEquals(0, jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'
            AND table_name IN ('users', 'menu', 'taste_tags')
        """.trimIndent(), Int::class.java))
    }

    @Test
    fun `OpenAPI exposes v1 visit order and reference check routes`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/v1/visits']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/orders']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/orders/references/users/{id}'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/orders/references/menu/{id}'].get").exists())
            .andExpect(jsonPath("$.servers[0].url").value("/"))
    }
}
