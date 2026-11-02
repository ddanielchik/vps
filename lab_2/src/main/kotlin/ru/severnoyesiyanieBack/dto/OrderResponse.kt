package ru.severnoyesiyanieBack.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderResponse(
    val idOrder: Int,
    val visitId: Int,
    val waiterId: Int?,
    val workerId: Int?,
    val items: List<OrderItemResponse>,
    val totalAmount: BigDecimal,
    val createdAt: LocalDateTime
)