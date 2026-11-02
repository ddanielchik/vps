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
        val user = User(name = "A".repeat(50), email = "guest@example.com")
        val menu = MenuItem(name = "A".repeat(50), price = BigDecimal.ZERO, ingredients = "A".repeat(500))
        val validModels = listOf(
            CreateUserRequest(user.name, user.email),
            UpdateUserRequest(user.name, user.email),
            CreateMenuItemRequest(menu.name, menu.price, menu.ingredients!!, setOf(1)),
            CreateVisitRequest(1, 1),
            UpdateVisitRequest(1),
            CreateOrderRequest(1, 2, 3, listOf(CreateOrderItemRequest(1, 1))),
            user, menu, TasteTag(name = "A".repeat(50)), Visit(user = user, tableNumber = 1)
        )
        validModels.forEach { model ->
            assertThat(validator.validate(model)).describedAs(model.javaClass.simpleName).isEmpty()
        }
    }

    companion object {
        @JvmStatic
        fun invalidModels(): Stream<Arguments> {
            val user = User(name = "Guest", email = "guest@example.com")
            val visit = Visit(user = user)
            val menu = MenuItem(name = "Cocktail", price = BigDecimal.TEN)
            val order = Order(visit = visit, totalAmount = BigDecimal.TEN)
            return Stream.of(
                Arguments.of(CreateUserRequest(" ", "guest@example.com"), "name"),
                Arguments.of(CreateUserRequest("A".repeat(51), "guest@example.com"), "name"),
                Arguments.of(CreateUserRequest("Guest", "invalid"), "email"),
                Arguments.of(CreateUserRequest("Guest", " "), "email"),
                Arguments.of(CreateUserRequest("Guest", "a".repeat(100) + "@example.com"), "email"),
                Arguments.of(UpdateUserRequest(" ", "guest@example.com"), "name"),
                Arguments.of(UpdateUserRequest("Guest", "invalid"), "email"),
                Arguments.of(CreateVisitRequest(0, 1), "clientId"),
                Arguments.of(CreateVisitRequest(1, 0), "tableNumber"),
                Arguments.of(UpdateVisitRequest(-1), "tableNumber"),
                Arguments.of(CreateMenuItemRequest(" ", BigDecimal.ZERO, "Rum", setOf(1)), "name"),
                Arguments.of(CreateMenuItemRequest("Drink", BigDecimal("-0.01"), "Rum", setOf(1)), "price"),
                Arguments.of(CreateMenuItemRequest("Drink", BigDecimal.ONE, " ", setOf(1)), "ingredients"),
                Arguments.of(CreateMenuItemRequest("Drink", BigDecimal.ONE, "A".repeat(501), setOf(1)), "ingredients"),
                Arguments.of(CreateMenuItemRequest("Drink", BigDecimal.ONE, "Rum", emptySet()), "tasteTagIds"),
                Arguments.of(CreateOrderRequest(0, 2, 3, listOf(CreateOrderItemRequest(1, 1))), "visitId"),
                Arguments.of(CreateOrderRequest(1, 0, 3, listOf(CreateOrderItemRequest(1, 1))), "waiterId"),
                Arguments.of(CreateOrderRequest(1, 2, 0, listOf(CreateOrderItemRequest(1, 1))), "workerId"),
                Arguments.of(CreateOrderRequest(1, 2, 3, emptyList()), "items"),
                Arguments.of(CreateOrderRequest(1, 2, 3, listOf(CreateOrderItemRequest(0, 1))), "items[0].menuItemId"),
                Arguments.of(CreateOrderRequest(1, 2, 3, listOf(CreateOrderItemRequest(1, 0))), "items[0].quantity"),
                Arguments.of(User(name = " ", email = "guest@example.com"), "name"),
                Arguments.of(User(name = "Guest", email = "invalid"), "email"),
                Arguments.of(TasteTag(name = " "), "name"),
                Arguments.of(TasteTag(name = "A".repeat(51)), "name"),
                Arguments.of(MenuItem(name = " ", price = BigDecimal.ZERO), "name"),
                Arguments.of(MenuItem(name = "Drink", price = BigDecimal("-0.01")), "price"),
                Arguments.of(MenuItem(name = "Drink", price = BigDecimal.ONE, ingredients = "A".repeat(501)), "ingredients"),
                Arguments.of(Visit(user = user, tableNumber = 0), "tableNumber"),
                Arguments.of(Order(visit = visit, totalAmount = BigDecimal("-0.01")), "totalAmount"),
                Arguments.of(OrderItem(order = order, product = menu, unitPrice = BigDecimal.ONE, quantity = 0), "quantity")
            )
        }
    }
}
