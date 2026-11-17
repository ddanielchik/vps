package ru.severnoyesiyanieBack.dto

import java.math.BigDecimal

data class OrderItemResponse(
    val menuItemId: Int,
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
) {
    class Builder {
        private var menuItemId: Int? = null
        private var name: String? = null
        private var quantity: Int? = null
        private var unitPrice: BigDecimal? = null
        private var subtotal: BigDecimal? = null

        fun menuItemId(value: Int) = apply { menuItemId = value }
        fun name(value: String) = apply { name = value }
        fun quantity(value: Int) = apply { quantity = value }
        fun unitPrice(value: BigDecimal) = apply { unitPrice = value }
        fun subtotal(value: BigDecimal) = apply { subtotal = value }

        fun build() = OrderItemResponse(
            menuItemId = requireNotNull(menuItemId) { "menuItemId is required" },
            name = requireNotNull(name) { "name is required" },
            quantity = requireNotNull(quantity) { "quantity is required" },
            unitPrice = requireNotNull(unitPrice) { "unitPrice is required" },
            subtotal = requireNotNull(subtotal) { "subtotal is required" }
        )
    }
}
