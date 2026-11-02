package ru.severnoyesiyanieBack.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

class VisitAndOrderApiIT : ApiIntegrationSupport() {
    @Test
    fun `visit can be opened read moved given preferences and closed`() {
        val clientId = createClient()
        val visitId = createVisit(clientId)
        mockMvc.perform(get("/api/visits/$visitId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.clientId").value(clientId))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.openedAt").isNotEmpty)
            .andExpect(jsonPath("$.closedAt").isEmpty)
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM visits WHERE id_visit = ?", String::class.java, visitId))

        mockMvc.perform(
            put("/api/visits/$visitId").contentType(MediaType.APPLICATION_JSON).content("""{"tableNumber":9}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.tableNumber").value(9))
        mockMvc.perform(
            put("/api/visits/$visitId/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("""{"tasteTagIds":[1,2]}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.tastePreferences.length()").value(2))
        assertEquals(2, count("visit_taste_preferences"))

        mockMvc.perform(post("/api/visits/$visitId/close"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.closedAt").isNotEmpty)
            .andExpect(jsonPath("$.tastePreferences").isEmpty)
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM visits WHERE id_visit = ?", String::class.java, visitId))
        assertEquals(0, count("visit_taste_preferences"))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{\"clientId\":0,\"tableNumber\":4}",
        "{\"clientId\":1,\"tableNumber\":0}",
        "{\"clientId\":1,\"tableNumber\":-1}"
    ])
    fun `invalid visit input returns bad request`(body: String) {
        mockMvc.perform(post("/api/visits").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").isNotEmpty)
        assertEquals(0, count("visits"))
    }

    @Test
    fun `invalid table update leaves the saved table unchanged`() {
        val id = createVisit()
        mockMvc.perform(
            put("/api/visits/$id").contentType(MediaType.APPLICATION_JSON).content("""{"tableNumber":0}""")
        ).andExpect(status().isBadRequest)
        assertEquals(4, jdbc.queryForObject("SELECT table_number FROM visits WHERE id_visit = ?", Int::class.java, id))
    }

    @Test
    fun `closed visits reject repeated close updates preferences and recommendations`() {
        val id = createVisit()
        mockMvc.perform(post("/api/visits/$id/close")).andExpect(status().isOk)
        val closedAt = jdbc.queryForObject("SELECT closed_at FROM visits WHERE id_visit = ?", java.sql.Timestamp::class.java, id)

        mockMvc.perform(post("/api/visits/$id/close")).andExpect(status().isConflict)
        mockMvc.perform(
            put("/api/visits/$id").contentType(MediaType.APPLICATION_JSON).content("""{"tableNumber":9}""")
        ).andExpect(status().isConflict)
        mockMvc.perform(
            put("/api/visits/$id/preferences").contentType(MediaType.APPLICATION_JSON).content("""{"tasteTagIds":[1]}""")
        ).andExpect(status().isConflict)
        mockMvc.perform(get("/api/visits/$id/recommendations")).andExpect(status().isConflict)
        assertEquals(closedAt, jdbc.queryForObject("SELECT closed_at FROM visits WHERE id_visit = ?", java.sql.Timestamp::class.java, id))
        assertEquals(4, jdbc.queryForObject("SELECT table_number FROM visits WHERE id_visit = ?", Int::class.java, id))
    }

    @Test
    fun `unknown visit and client references return not found`() {
        mockMvc.perform(get("/api/visits/999")).andExpect(status().isNotFound)
        mockMvc.perform(post("/api/visits/999/close")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/visits/999/recommendations")).andExpect(status().isNotFound)
        mockMvc.perform(
            put("/api/visits/999").contentType(MediaType.APPLICATION_JSON).content("""{"tableNumber":1}""")
        ).andExpect(status().isNotFound)
        mockMvc.perform(
            put("/api/visits/999/preferences").contentType(MediaType.APPLICATION_JSON).content("""{"tasteTagIds":[1]}""")
        ).andExpect(status().isNotFound)
        mockMvc.perform(
            post("/api/visits").contentType(MediaType.APPLICATION_JSON).content("""{"clientId":999,"tableNumber":1}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `preferences replace old values and can be cleared but unknown tags preserve them`() {
        val id = createVisit()
        fun setPreferences(tags: String) = mockMvc.perform(
            put("/api/visits/$id/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("""{"tasteTagIds":[$tags]}""")
        )
        setPreferences("1,2").andExpect(status().isOk)
        setPreferences("3").andExpect(status().isOk)
            .andExpect(jsonPath("$.tastePreferences[0]").value("BITTER"))
        assertEquals(listOf(3), jdbc.queryForList("SELECT id_tag FROM visit_taste_preferences", Int::class.java))

        setPreferences("1,999").andExpect(status().isBadRequest)
            .andExpect(content().string("Some specified taste tags were not found"))
        assertEquals(listOf(3), jdbc.queryForList("SELECT id_tag FROM visit_taste_preferences", Int::class.java))
        setPreferences("").andExpect(status().isOk).andExpect(jsonPath("$.tastePreferences").isEmpty)
        assertEquals(0, count("visit_taste_preferences"))
    }

    @Test
    fun `visits pagination includes total header and enforces fifty record limit`() {
        val clientId = createClient()
        jdbc.update(
            """
            INSERT INTO visits (id_user, table_number, status)
            SELECT ?, n, 'OPEN' FROM generate_series(1, 53) AS n
            """.trimIndent(),
            clientId
        )
        mockMvc.perform(get("/api/visits").param("size", "100"))
            .andExpect(status().isOk).andExpect(header().string("X-Total-Count", "53"))
            .andExpect(jsonPath("$.length()").value(50))
            .andExpect(jsonPath("$[0].idVisit").value(1))
        mockMvc.perform(get("/api/visits").param("page", "1").param("size", "50"))
            .andExpect(status().isOk).andExpect(header().string("X-Total-Count", "53"))
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].idVisit").value(51))
        mockMvc.perform(get("/api/visits").param("page", "2").param("size", "50"))
            .andExpect(status().isOk).andExpect(jsonPath("$").isEmpty)
        mockMvc.perform(get("/api/visits").param("size", "0"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
        mockMvc.perform(get("/api/visits").param("page", "-1")).andExpect(status().isBadRequest)
    }

    @Test
    fun `recommendations match at least one preference and exclude unrelated drinks`() {
        val visitId = createVisit()
        val matching = createMenuItem(tags = "1,2")
        createMenuItem(name = "Bitter", tags = "3")
        mockMvc.perform(get("/api/visits/$visitId/recommendations"))
            .andExpect(status().isOk).andExpect(jsonPath("$").isEmpty)
        mockMvc.perform(
            put("/api/visits/$visitId/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("""{"tasteTagIds":[1,2]}""")
        ).andExpect(status().isOk)
        mockMvc.perform(get("/api/visits/$visitId/recommendations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].idPosition").value(matching))
    }

    @Test
    fun `recommendation query limits results to fifty distinct matching drinks`() {
        val visitId = createVisit()
        jdbc.update("INSERT INTO visit_taste_preferences (id_visit, id_tag) VALUES (?, 1), (?, 2)", visitId, visitId)
        jdbc.execute("INSERT INTO menu (name_product, price, ingredients) SELECT 'Drink ' || n, 100, 'Lime' FROM generate_series(1, 60) AS n")
        jdbc.execute("INSERT INTO menu_taste_tags (id_position, id_tag) SELECT id_position, t FROM menu CROSS JOIN generate_series(1, 2) AS t")
        createMenuItem(name = "Unrelated", tags = "3")

        val result = json(mockMvc.perform(get("/api/visits/$visitId/recommendations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(50)))

        val drinks = (0 until result.size()).map { result[it] }
        val ids = drinks.map { it["idPosition"].asInt() }
        assertEquals(50, ids.toSet().size)
        assertTrue(ids.all { it in 1..60 })
        assertTrue(drinks.all { drink ->
            val tags = drink["tasteTags"]
            (0 until tags.size()).map { tags[it].asText() }.toSet() == setOf("SWEET", "SOUR")
        })
    }

    @Test
    fun `order persists quantities totals staff and price snapshots`() {
        val visit = createVisit()
        val waiter = createStaff("WAITER")
        val worker = createStaff("WORKER")
        val first = createMenuItem(price = "125.50")
        val second = createMenuItem(name = "Second drink", price = "99.90")
        val body = """{"visitId":$visit,"waiterId":$waiter,"workerId":$worker,"items":[{"menuItemId":$first,"quantity":2},{"menuItemId":$second,"quantity":3}]}"""
        val result = mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.visitId").value(visit))
            .andExpect(jsonPath("$.waiterId").value(waiter))
            .andExpect(jsonPath("$.workerId").value(worker))
            .andExpect(jsonPath("$.totalAmount").value(550.7))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unitPrice").value(125.5))
            .andExpect(jsonPath("$.items[0].subtotal").value(251.0))
            .andExpect(jsonPath("$.items[1].subtotal").value(299.7))
            .andExpect(jsonPath("$.createdAt").isNotEmpty)
        val orderId = json(result)["idOrder"].asInt()
        assertEquals(1, count("orders"))
        assertEquals(2, count("order_items"))

        mockMvc.perform(
            put("/api/menu/$first").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Northern Lights","price":999.99,"ingredients":"Gin, lime","tasteTagIds":[1,2]}""")
        ).andExpect(status().isOk)
        val savedOrder = json(mockMvc.perform(get("/api/orders/$orderId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalAmount").value(550.7)))
        val savedItem = savedOrder["items"].single { it["menuItemId"].asInt() == first }
        assertEquals(0, BigDecimal("125.50").compareTo(savedItem["unitPrice"].decimalValue()))
        assertEquals(0, BigDecimal("251.00").compareTo(savedItem["subtotal"].decimalValue()))
        assertEquals(BigDecimal("125.50"), jdbc.queryForObject("SELECT unit_price FROM order_items WHERE id_product = ?", BigDecimal::class.java, first))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{\"visitId\":0,\"waiterId\":1,\"workerId\":2,\"items\":[{\"menuItemId\":1,\"quantity\":1}]}",
        "{\"visitId\":1,\"waiterId\":0,\"workerId\":2,\"items\":[{\"menuItemId\":1,\"quantity\":1}]}",
        "{\"visitId\":1,\"waiterId\":1,\"workerId\":0,\"items\":[{\"menuItemId\":1,\"quantity\":1}]}",
        "{\"visitId\":1,\"waiterId\":1,\"workerId\":2,\"items\":[]}",
        "{\"visitId\":1,\"waiterId\":1,\"workerId\":2,\"items\":[{\"menuItemId\":0,\"quantity\":1}]}",
        "{\"visitId\":1,\"waiterId\":1,\"workerId\":2,\"items\":[{\"menuItemId\":1,\"quantity\":0}]}"
    ])
    fun `order request validates IDs empty items and nested quantities`(body: String) {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").isNotEmpty)
        assertEquals(0, count("orders"))
    }

    @Test
    fun `orders reject wrong staff roles missing products and closed visits without writing rows`() {
        val client = createClient()
        val visit = createVisit(client)
        val waiter = createStaff("WAITER")
        val worker = createStaff("WORKER")
        val item = createMenuItem()
        fun order(visitId: Int = visit, waiterId: Int = waiter, workerId: Int = worker, itemId: Int = item) = mockMvc.perform(
            post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("""{"visitId":$visitId,"waiterId":$waiterId,"workerId":$workerId,"items":[{"menuItemId":$itemId,"quantity":1}]}""")
        )
        order(waiterId = client).andExpect(status().isConflict)
        order(workerId = client).andExpect(status().isConflict)
        order(waiterId = 999).andExpect(status().isNotFound)
        order(workerId = 999).andExpect(status().isNotFound)
        order(itemId = 999).andExpect(status().isNotFound)
        order(visitId = 999).andExpect(status().isNotFound)
        mockMvc.perform(post("/api/visits/$visit/close")).andExpect(status().isOk)
        order().andExpect(status().isConflict)
        mockMvc.perform(get("/api/orders/999")).andExpect(status().isNotFound)
        assertEquals(0, count("orders"))
        assertEquals(0, count("order_items"))
    }
}
