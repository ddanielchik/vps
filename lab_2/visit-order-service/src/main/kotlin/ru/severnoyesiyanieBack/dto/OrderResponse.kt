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
) {
    class Builder {
        private var idOrder: Int? = null
        private var visitId: Int? = null
        private var waiterId: Int? = null
        private var workerId: Int? = null
        private var items: List<OrderItemResponse>? = null
        private var totalAmount: BigDecimal? = null
        private var createdAt: LocalDateTime? = null

        fun idOrder(value: Int) = apply { idOrder = value }
        fun visitId(value: Int) = apply { visitId = value }
        fun waiterId(value: Int?) = apply { waiterId = value }
        fun workerId(value: Int?) = apply { workerId = value }
        fun items(value: List<OrderItemResponse>) = apply { items = value }
        fun totalAmount(value: BigDecimal) = apply { totalAmount = value }
        fun createdAt(value: LocalDateTime) = apply { createdAt = value }

        fun build() = OrderResponse(
            idOrder = requireNotNull(idOrder) { "idOrder is required" },
            visitId = requireNotNull(visitId) { "visitId is required" },
            waiterId = waiterId,
            workerId = workerId,
            items = requireNotNull(items) { "items is required" },
            totalAmount = requireNotNull(totalAmount) { "totalAmount is required" },
            createdAt = requireNotNull(createdAt) { "createdAt is required" }
        )
    }
}
