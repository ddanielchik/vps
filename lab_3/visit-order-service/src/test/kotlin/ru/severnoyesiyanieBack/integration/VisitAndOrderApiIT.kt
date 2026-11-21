package ru.severnoyesiyanieBack.integration

import org.junit.jupiter.api.Assertions.assertEquals
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
        performAsWaiter(get("/api/v1/visits/$visitId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.clientId").value(clientId))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.openedAt").isNotEmpty)
            .andExpect(jsonPath("$.closedAt").isEmpty)
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM visits WHERE id_visit = ?", String::class.java, visitId))

        performAsWaiter(
            put("/api/v1/visits/$visitId").contentType(MediaType.APPLICATION_JSON).content("""{"tableNumber":9}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.tableNumber").value(9))
        performAsWaiter(
            put("/api/v1/visits/$visitId/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("""{"tasteTagIds":[1,2]}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.tastePreferences.length()").value(2))
        assertEquals(2, count("visit_taste_preferences"))

        performAsWaiter(post("/api/v1/visits/$visitId/close"))
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
        performAsWaiter(post("/api/v1/visits").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").isNotEmpty)
        assertEquals(0, count("visits"))
    }

    @Test
    fun `invalid table update leaves the saved table unchanged`() {
        val id = createVisit()
        performAsWaiter(
            put("/api/v1/visits/$id").contentType(MediaType.APPLICATION_JSON).content("""{"tableNumber":0}""")
        ).andExpect(status().isBadRequest)
        assertEquals(4, jdbc.queryForObject("SELECT table_number FROM visits WHERE id_visit = ?", Int::class.java, id))
    }

    @Test
    fun `closed visits reject repeated close updates preferences and recommendations`() {
        val id = createVisit()
        performAsWaiter(post("/api/v1/visits/$id/close")).andExpect(status().isOk)
        val closedAt = jdbc.queryForObject("SELECT closed_at FROM visits WHERE id_visit = ?", java.sql.Timestamp::class.java, id)

        performAsWaiter(post("/api/v1/visits/$id/close")).andExpect(status().isConflict)
        performAsWaiter(
            put("/api/v1/visits/$id").contentType(MediaType.APPLICATION_JSON).content("""{"tableNumber":9}""")
        ).andExpect(status().isConflict)
        performAsWaiter(
            put("/api/v1/visits/$id/preferences").contentType(MediaType.APPLICATION_JSON).content("""{"tasteTagIds":[1]}""")
        ).andExpect(status().isConflict)
        performAsWaiter(get("/api/v1/visits/$id/recommendations")).andExpect(status().isConflict)
        assertEquals(closedAt, jdbc.queryForObject("SELECT closed_at FROM visits WHERE id_visit = ?", java.sql.Timestamp::class.java, id))
        assertEquals(4, jdbc.queryForObject("SELECT table_number FROM visits WHERE id_visit = ?", Int::class.java, id))
    }

    @Test
    fun `unknown visit and client references return not found`() {
        performAsWaiter(get("/api/v1/visits/999")).andExpect(status().isNotFound)
        performAsWaiter(post("/api/v1/visits/999/close")).andExpect(status().isNotFound)
        performAsWaiter(get("/api/v1/visits/999/recommendations")).andExpect(status().isNotFound)
        performAsWaiter(
            put("/api/v1/visits/999").contentType(MediaType.APPLICATION_JSON).content("""{"tableNumber":1}""")
        ).andExpect(status().isNotFound)
        performAsWaiter(
            put("/api/v1/visits/999/preferences").contentType(MediaType.APPLICATION_JSON).content("""{"tasteTagIds":[1]}""")
        ).andExpect(status().isNotFound)
        performAsWaiter(
            post("/api/v1/visits").contentType(MediaType.APPLICATION_JSON).content("""{"clientId":999,"tableNumber":1}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `preferences replace old values and can be cleared but unknown tags preserve them`() {
        val id = createVisit()
        fun setPreferences(tags: String) = performAsWaiter(
            put("/api/v1/visits/$id/preferences").contentType(MediaType.APPLICATION_JSON)
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
        performAsWaiter(get("/api/v1/visits").param("size", "100"))
            .andExpect(status().isOk).andExpect(header().string("X-Total-Count", "53"))
            .andExpect(jsonPath("$.length()").value(50))
            .andExpect(jsonPath("$[0].idVisit").value(1))
        performAsWaiter(get("/api/v1/visits").param("page", "1").param("size", "50"))
            .andExpect(status().isOk).andExpect(header().string("X-Total-Count", "53"))
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].idVisit").value(51))
        performAsWaiter(get("/api/v1/visits").param("page", "2").param("size", "50"))
            .andExpect(status().isOk).andExpect(jsonPath("$").isEmpty)
        performAsWaiter(get("/api/v1/visits").param("size", "0"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
        performAsWaiter(get("/api/v1/visits").param("page", "-1")).andExpect(status().isBadRequest)
    }

    @Test
    fun `recommendations forward visit tastes to the menu service`() {
        val visitId = createVisit()
        val matching = createMenuItem(tags = "1,2")
        performAsWaiter(get("/api/v1/visits/$visitId/recommendations"))
            .andExpect(status().isOk).andExpect(jsonPath("$").isEmpty)
        performAsWaiter(
            put("/api/v1/visits/$visitId/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("""{"tasteTagIds":[1,2]}""")
        ).andExpect(status().isOk)
        remote.recommendations = listOf(remote.menu.getValue(matching))
        performAsWaiter(get("/api/v1/visits/$visitId/recommendations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].idPosition").value(matching))
        assertEquals(setOf("SWEET", "SOUR"), remote.requestedTastes)
    }

    @Test
    fun `order persists quantities totals staff and price snapshots`() {
        val visit = createVisit()
        val first = createMenuItem(price = "125.50")
        val second = createMenuItem(name = "Second drink", price = "99.90")
        val body = """{"visitId":$visit,"items":[{"menuItemId":$first,"quantity":2},{"menuItemId":$second,"quantity":3}]}"""
        val result = performAsWaiter(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.visitId").value(visit))
            .andExpect(jsonPath("$.waiterId").value(waiterId))
            .andExpect(jsonPath("$.workerId").doesNotExist())
            .andExpect(jsonPath("$.totalAmount").value(550.7))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unitPrice").value(125.5))
            .andExpect(jsonPath("$.items[0].subtotal").value(251.0))
            .andExpect(jsonPath("$.items[1].subtotal").value(299.7))
            .andExpect(jsonPath("$.createdAt").isNotEmpty)
        val orderId = json(result)["idOrder"].asInt()
        assertEquals(1, count("orders"))
        assertEquals(2, count("order_items"))

        remote.menu[first] = remote.menu.getValue(first).copy(name = "Updated name", price = BigDecimal("999.99"))
        val savedOrder = json(performAsWaiter(get("/api/v1/orders/$orderId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalAmount").value(550.7)))
        val savedItem = savedOrder["items"].single { it["menuItemId"].asInt() == first }
        assertEquals("Northern Lights", savedItem["name"].asString())
        assertEquals(0, BigDecimal("125.50").compareTo(savedItem["unitPrice"].decimalValue()))
        assertEquals(0, BigDecimal("251.00").compareTo(savedItem["subtotal"].decimalValue()))
        assertEquals(BigDecimal("125.50"), jdbc.queryForObject("SELECT unit_price FROM order_items WHERE id_product = ?", BigDecimal::class.java, first))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{\"visitId\":0,\"items\":[{\"menuItemId\":1,\"quantity\":1}]}",
        "{\"visitId\":1,\"items\":[]}",
        "{\"visitId\":1,\"items\":[{\"menuItemId\":0,\"quantity\":1}]}",
        "{\"visitId\":1,\"items\":[{\"menuItemId\":1,\"quantity\":0}]}"
    ])
    fun `order request validates IDs empty items and nested quantities`(body: String) {
        performAsWaiter(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").isNotEmpty)
        assertEquals(0, count("orders"))
    }

    @Test
    fun `orders reject missing products and closed visits without writing rows`() {
        val visitId = createVisit()
        val menuItemId = createMenuItem()
        performAsWaiter(orderRequest(visitId, 999)).andExpect(status().isNotFound)
        performAsWaiter(orderRequest(999, menuItemId)).andExpect(status().isNotFound)
        performAsWaiter(post("/api/v1/visits/$visitId/close")).andExpect(status().isOk)
        performAsWaiter(orderRequest(visitId, menuItemId)).andExpect(status().isConflict)
        performAsWaiter(get("/api/v1/orders/999")).andExpect(status().isNotFound)
        assertEquals(0, count("orders"))
    }

    @Test
    fun `clients order for their own visit and the waiter can add another order`() {
        val clientId = createClient()
        val otherClientId = createClient()
        val visitId = createVisit(clientId)
        val menuItemId = createMenuItem()
        val workerId = createStaff("WORKER")

        mockMvc.perform(orderRequest(visitId, menuItemId)).andExpect(status().isUnauthorized)
        performAs(otherClientId, orderRequest(visitId, menuItemId)).andExpect(status().isForbidden)
        performAs(workerId, orderRequest(visitId, menuItemId)).andExpect(status().isForbidden)
        val orderId = json(performAs(clientId, orderRequest(visitId, menuItemId))
            .andExpect(status().isCreated).andExpect(jsonPath("$.waiterId").doesNotExist()))["idOrder"].asInt()
        performAsWaiter(orderRequest(visitId, menuItemId))
            .andExpect(status().isCreated).andExpect(jsonPath("$.waiterId").value(waiterId))

        performAs(otherClientId, get("/api/v1/orders/$orderId")).andExpect(status().isForbidden)
        performAs(otherClientId, get("/api/v1/orders")).andExpect(jsonPath("$.length()").value(0))
        performAs(clientId, get("/api/v1/orders")).andExpect(jsonPath("$.length()").value(2))
        performAs(workerId, get("/api/v1/orders")).andExpect(jsonPath("$.length()").value(2))
        performAs(otherClientId, get("/api/v1/visits/$visitId")).andExpect(status().isForbidden)
        performAs(otherClientId, get("/api/v1/visits")).andExpect(jsonPath("$.length()").value(0))
        performAs(clientId, get("/api/v1/visits")).andExpect(jsonPath("$.length()").value(1))
        performAs(clientId, post("/api/v1/visits/$visitId/close")).andExpect(status().isForbidden)
    }

    @Test
    fun `only a worker can take an order and a second worker cannot take it over`() {
        val visitId = createVisit()
        val menuItemId = createMenuItem()
        val orderId = json(performAsWaiter(orderRequest(visitId, menuItemId))
            .andExpect(status().isCreated))["idOrder"].asInt()
        val workerId = createStaff("WORKER")
        val otherWorkerId = createStaff("WORKER")

        performAsWaiter(post("/api/v1/orders/$orderId/start")).andExpect(status().isForbidden)
        repeat(2) {
            performAs(workerId, post("/api/v1/orders/$orderId/start"))
                .andExpect(status().isOk).andExpect(jsonPath("$.workerId").value(workerId))
        }
        performAs(otherWorkerId, post("/api/v1/orders/$orderId/start")).andExpect(status().isConflict)
        performAsWaiter(get("/api/v1/orders/$orderId"))
            .andExpect(status().isOk).andExpect(jsonPath("$.workerId").value(workerId))
    }

    private fun orderRequest(visitId: Int, menuItemId: Int) =
        post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
            .content("""{"visitId":$visitId,"items":[{"menuItemId":$menuItemId,"quantity":1}]}""")
}
