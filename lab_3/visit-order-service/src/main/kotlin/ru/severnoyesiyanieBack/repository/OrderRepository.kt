package ru.severnoyesiyanieBack.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.severnoyesiyanieBack.entity.Order

interface OrderRepository : JpaRepository<Order, Int> {
    fun findByVisitClientId(clientId: Int, pageable: Pageable): Page<Order>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedByIdOrder(idOrder: Int): Order?

    fun existsByWaiterIdOrWorkerId(waiterId: Int, workerId: Int): Boolean
    @Query("select count(i) > 0 from OrderItem i where i.menuItemId = :id and i.menuItemName is null")
    fun hasItemsWithoutName(@Param("id") menuItemId: Int): Boolean
}
