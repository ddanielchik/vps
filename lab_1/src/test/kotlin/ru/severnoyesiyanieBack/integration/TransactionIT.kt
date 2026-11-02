package ru.severnoyesiyanieBack.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import ru.severnoyesiyanieBack.dto.CreateOrderItemRequest
import ru.severnoyesiyanieBack.dto.CreateOrderRequest
import ru.severnoyesiyanieBack.dto.VisitPreferencesRequest
import ru.severnoyesiyanieBack.service.OrderService
import ru.severnoyesiyanieBack.service.VisitService

// Intentionally no test-level @Transactional: each service call must commit or roll back itself.
class TransactionIT : ApiIntegrationSupport() {
    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var visitService: VisitService

    @Test
    fun `failure inserting an order item rolls back the already inserted order header`() {
        val visit = createVisit()
        val waiter = createStaff("WAITER")
        val worker = createStaff("WORKER")
        val item = createMenuItem()
        val request = CreateOrderRequest(visit, waiter, worker, listOf(CreateOrderItemRequest(item, 3)))

        // A deterministic database fault occurs after the IDENTITY order header has been inserted.
        jdbc.execute("ALTER TABLE order_items ADD CONSTRAINT test_reject_quantity CHECK (quantity < 3)")
        try {
            assertThrows(DataIntegrityViolationException::class.java) { orderService.createOrder(request) }
            assertEquals(true, jdbc.queryForObject("SELECT is_called FROM orders_id_order_seq", Boolean::class.java))
            assertEquals(0, count("orders"))
            assertEquals(0, count("order_items"))
            assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM visits WHERE id_visit = ?", String::class.java, visit))
        } finally {
            jdbc.execute("ALTER TABLE order_items DROP CONSTRAINT test_reject_quantity")
        }

        // A subsequent real transaction can save the same otherwise valid request.
        orderService.createOrder(request)
        assertEquals(1, count("orders"))
        assertEquals(1, count("order_items"))
    }

    @Test
    fun `failed visit close preserves open status timestamp and taste preferences`() {
        val visit = createVisit()
        visitService.updateTastePreferences(visit, VisitPreferencesRequest(setOf(1, 2)))

        // The status update and removal of preferences belong to one atomic transaction.
        jdbc.execute("ALTER TABLE visits ADD CONSTRAINT test_keep_open CHECK (status = 'OPEN')")
        try {
            assertThrows(DataIntegrityViolationException::class.java) { visitService.closeVisit(visit) }
            assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM visits WHERE id_visit = ?", String::class.java, visit))
            assertNull(jdbc.queryForObject("SELECT closed_at FROM visits WHERE id_visit = ?", java.sql.Timestamp::class.java, visit))
            assertEquals(listOf(1, 2), jdbc.queryForList("SELECT id_tag FROM visit_taste_preferences ORDER BY id_tag", Int::class.java))
        } finally {
            jdbc.execute("ALTER TABLE visits DROP CONSTRAINT test_keep_open")
        }

        visitService.closeVisit(visit)
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM visits WHERE id_visit = ?", String::class.java, visit))
        assertEquals(0, count("visit_taste_preferences"))
    }

    @Test
    fun `failed replacement of preferences restores removed associations`() {
        val visit = createVisit()
        visitService.updateTastePreferences(visit, VisitPreferencesRequest(setOf(1, 2)))

        // The requested tag exists; this forces a failure while writing the association table.
        jdbc.execute("ALTER TABLE visit_taste_preferences ADD CONSTRAINT test_reject_bitter CHECK (id_tag <> 3)")
        try {
            assertThrows(DataIntegrityViolationException::class.java) {
                visitService.updateTastePreferences(visit, VisitPreferencesRequest(setOf(3)))
            }
            assertEquals(listOf(1, 2), jdbc.queryForList("SELECT id_tag FROM visit_taste_preferences ORDER BY id_tag", Int::class.java))
        } finally {
            jdbc.execute("ALTER TABLE visit_taste_preferences DROP CONSTRAINT test_reject_bitter")
        }

        visitService.updateTastePreferences(visit, VisitPreferencesRequest(setOf(3)))
        assertEquals(listOf(3), jdbc.queryForList("SELECT id_tag FROM visit_taste_preferences", Int::class.java))
    }
}
