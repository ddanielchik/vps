package ru.severnoyesiyanieBack.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.severnoyesiyanieBack.client.MenuClient
import ru.severnoyesiyanieBack.client.UserClient
import ru.severnoyesiyanieBack.dto.CreateOrderRequest
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.dto.OrderItemResponse
import ru.severnoyesiyanieBack.dto.OrderResponse
import ru.severnoyesiyanieBack.entity.Order
import ru.severnoyesiyanieBack.entity.OrderItem
import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.entity.VisitStatus
import ru.severnoyesiyanieBack.exception.InvalidUserRoleException
import ru.severnoyesiyanieBack.exception.OrderNotFoundException
import ru.severnoyesiyanieBack.exception.VisitAlreadyClosedException
import ru.severnoyesiyanieBack.exception.VisitNotFoundException
import ru.severnoyesiyanieBack.repository.OrderRepository
import ru.severnoyesiyanieBack.repository.VisitRepository
import java.math.BigDecimal

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val visitRepository: VisitRepository,
    private val userClient: UserClient,
    private val menuClient: MenuClient,
    private val referenceLock: ReferenceLock
) {
    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        val visit = visitRepository.findById(request.visitId)
            .orElseThrow { VisitNotFoundException(request.visitId) }
        if (visit.status != VisitStatus.OPEN) throw VisitAlreadyClosedException(request.visitId)

        referenceLock.acquire(ReferenceLock.Kind.USER, listOf(request.waiterId, request.workerId), shared = true)
        referenceLock.acquire(ReferenceLock.Kind.MENU_ITEM, request.items.map { it.menuItemId }, shared = true)
        val waiter = userClient.getUser(request.waiterId)
        val worker = userClient.getUser(request.workerId)
        if (waiter.role != UserRole.WAITER) throw InvalidUserRoleException(request.waiterId, UserRole.WAITER)
        if (worker.role != UserRole.WORKER) throw InvalidUserRoleException(request.workerId, UserRole.WORKER)

        val products = request.items.map { it.menuItemId }.distinct().associateWith(menuClient::getMenuItem)
        val total = request.items.sumOf { products.getValue(it.menuItemId).price * it.quantity.toBigDecimal() }
        val order = Order(visit = visit, waiterId = waiter.idUser, workerId = worker.idUser, totalAmount = total)
        request.items.forEach { item ->
            order.items.add(OrderItem(
                order = order,
                menuItemId = item.menuItemId,
                unitPrice = products.getValue(item.menuItemId).price,
                quantity = item.quantity
            ))
        }
        return toResponse(orderRepository.save(order), products)
    }

    @Transactional(readOnly = true)
    fun getOrderById(id: Int): OrderResponse {
        val order = orderRepository.findById(id).orElseThrow { OrderNotFoundException(id) }
        val products = order.items.map { it.menuItemId }.distinct().associateWith(menuClient::getMenuItem)
        return toResponse(order, products)
    }

    private fun toResponse(order: Order, products: Map<Int, MenuItemResponse>) = OrderResponse(
        idOrder = requireNotNull(order.idOrder),
        visitId = requireNotNull(order.visit.idVisit),
        waiterId = order.waiterId,
        workerId = order.workerId,
        items = order.items.map { item ->
            OrderItemResponse(
                menuItemId = item.menuItemId,
                name = products.getValue(item.menuItemId).name,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                subtotal = item.unitPrice * BigDecimal.valueOf(item.quantity.toLong())
            )
        },
        totalAmount = order.totalAmount,
        createdAt = order.createdAt
    )
}
