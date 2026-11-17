package ru.severnoyesiyanieBack.validation

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import ru.severnoyesiyanieBack.dto.*
import ru.severnoyesiyanieBack.entity.*
import java.math.BigDecimal
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelValidationTest {
    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    @AfterAll
    fun closeValidator() {
        factory.close()
    }

    @ParameterizedTest(name = "{index}: invalid {1}")
    @MethodSource("invalidModels")
    fun `invalid fields are rejected by bean validation`(model: Any, field: String) {
        assertThat(validator.validate(model).map { it.propertyPath.toString() }).contains(field)
    }

    @Test
    fun `valid boundary values are accepted by request and entity validation`() {
        val validModels = listOf(
            CreateVisitRequest(1, 1), UpdateVisitRequest(1),
            CreateOrderRequest(1, 2, 3, listOf(CreateOrderItemRequest(1, 1))),
            TastePreference(1, "A".repeat(50)), Visit(clientId = 1, tableNumber = 1)
        )
        validModels.forEach { model ->
            assertThat(validator.validate(model)).describedAs(model.javaClass.simpleName).isEmpty()
        }
    }

    companion object {
        @JvmStatic
        fun invalidModels(): Stream<Arguments> {
            val visit = Visit(clientId = 1)
            val order = Order(visit = visit, totalAmount = BigDecimal.TEN)
            return Stream.of(
                Arguments.of(CreateVisitRequest(0, 1), "clientId"),
                Arguments.of(CreateVisitRequest(1, 0), "tableNumber"),
                Arguments.of(UpdateVisitRequest(-1), "tableNumber"),
                Arguments.of(CreateOrderRequest(0, 2, 3, listOf(CreateOrderItemRequest(1, 1))), "visitId"),
                Arguments.of(CreateOrderRequest(1, 0, 3, listOf(CreateOrderItemRequest(1, 1))), "waiterId"),
                Arguments.of(CreateOrderRequest(1, 2, 0, listOf(CreateOrderItemRequest(1, 1))), "workerId"),
                Arguments.of(CreateOrderRequest(1, 2, 3, emptyList()), "items"),
                Arguments.of(CreateOrderRequest(1, 2, 3, listOf(CreateOrderItemRequest(0, 1))), "items[0].menuItemId"),
                Arguments.of(CreateOrderRequest(1, 2, 3, listOf(CreateOrderItemRequest(1, 0))), "items[0].quantity"),
                Arguments.of(Visit(clientId = 1, tableNumber = 0), "tableNumber"),
                Arguments.of(Order(visit = visit, totalAmount = BigDecimal("-0.01")), "totalAmount"),
                Arguments.of(OrderItem(order = order, menuItemId = 1, unitPrice = BigDecimal.ONE, quantity = 0), "quantity")
            )
        }
    }
}
