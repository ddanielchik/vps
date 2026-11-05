package ru.severnoyesiyanieBack.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.severnoyesiyanieBack.entity.Order

interface OrderRepository : JpaRepository<Order, Int> {
    fun existsByWaiterIdOrWorkerId(waiterId: Int, workerId: Int): Boolean
    fun existsByItemsMenuItemId(menuItemId: Int): Boolean
}
