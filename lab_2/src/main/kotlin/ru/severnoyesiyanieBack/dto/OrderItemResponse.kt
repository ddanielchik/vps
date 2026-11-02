package ru.severnoyesiyanieBack.dto

import java.math.BigDecimal

data class OrderItemResponse(
    val menuItemId: Int,
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)