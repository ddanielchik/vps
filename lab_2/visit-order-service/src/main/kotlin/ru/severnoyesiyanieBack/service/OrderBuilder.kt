package ru.severnoyesiyanieBack.service

import ru.severnoyesiyanieBack.entity.Order
import ru.severnoyesiyanieBack.entity.OrderItem
import ru.severnoyesiyanieBack.entity.Visit
import java.math.BigDecimal

class OrderBuilder(
    private val visit: Visit,
    private val waiterId: Int,
    private val workerId: Int
) {
    private data class ItemData(
        val menuItemId: Int,
        val name: String,
        val unitPrice: BigDecimal,
        val quantity: Int
    )

    private val items = mutableListOf<ItemData>()

    fun addItem(
        menuItemId: Int,
        name: String,
        unitPrice: BigDecimal,
        quantity: Int
    ) = apply {
        items.add(ItemData(menuItemId, name, unitPrice, quantity))
    }

    fun build(): Order {
        val total = items.sumOf {
            it.unitPrice * it.quantity.toBigDecimal()
        }

        val order = Order(
            visit = visit,
            waiterId = waiterId,
            workerId = workerId,
            totalAmount = total
        )

        items.forEach { item ->
            order.items.add(
                OrderItem(
                    order = order,
                    menuItemId = item.menuItemId,
                    menuItemName = item.name,
                    unitPrice = item.unitPrice,
                    quantity = item.quantity
                )
            )
        }

        return order
    }
}