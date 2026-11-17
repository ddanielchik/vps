package ru.severnoyesiyanieBack.service

import java.math.BigDecimal
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieBack.client.MenuClient
import ru.severnoyesiyanieBack.client.UserClient
import ru.severnoyesiyanieBack.dto.CreateOrderRequest
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.dto.OrderItemResponse
import ru.severnoyesiyanieBack.dto.OrderResponse
import ru.severnoyesiyanieBack.entity.Order
import ru.severnoyesiyanieBack.entity.OrderItem
import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.entity.UserStatus
import ru.severnoyesiyanieBack.entity.Visit
import ru.severnoyesiyanieBack.entity.VisitStatus
import ru.severnoyesiyanieBack.exception.InvalidUserRoleException
import ru.severnoyesiyanieBack.exception.OrderNotFoundException
import ru.severnoyesiyanieBack.exception.VisitAlreadyClosedException
import ru.severnoyesiyanieBack.exception.VisitNotFoundException
import ru.severnoyesiyanieBack.repository.OrderRepository
import ru.severnoyesiyanieBack.repository.VisitRepository

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val visitRepository: VisitRepository,
    private val userClient: UserClient,
    private val menuClient: MenuClient
) {
    @Transactional(readOnly = true)
    fun isUserInUse(id: Int): Boolean =
        visitRepository.existsByClientId(id) || orderRepository.existsByWaiterIdOrWorkerId(id, id)

    @Transactional(readOnly = true)
    fun hasMenuItemsWithoutName(id: Int): Boolean = orderRepository.hasItemsWithoutName(id)

    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        val visit = visitRepository.findById(request.visitId)
            .orElseThrow { VisitNotFoundException(request.visitId) }

        if (visit.status != VisitStatus.OPEN) {
            throw VisitAlreadyClosedException(request.visitId)
        }

        val waiter = userClient.getUser(request.waiterId)
        val worker = userClient.getUser(request.workerId)

        if (waiter.status == UserStatus.BLOCKED || worker.status == UserStatus.BLOCKED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Blocked employees cannot create orders")
        }

        if (waiter.role != UserRole.WAITER) {
            throw InvalidUserRoleException(request.waiterId, UserRole.WAITER)
        }

        if (worker.role != UserRole.WORKER) {
            throw InvalidUserRoleException(request.workerId, UserRole.WORKER)
        }

        val products = request.items
            .map { it.menuItemId }
            .distinct()
            .associateWith(menuClient::getMenuItem)

        val order = orderRepository.save(buildOrder(request, visit, products))

        return toResponse(order)
    }

    @Transactional
    fun getOrderById(id: Int): OrderResponse {
        val order = orderRepository.findById(id)
            .orElseThrow { OrderNotFoundException(id) }

        val itemsWithoutName = order.items.filter { it.menuItemName == null }
        val missingNames = itemsWithoutName.map { it.menuItemId }.distinct()
            .associateWith { menuClient.getMenuItem(it).name }
        itemsWithoutName.forEach {
            it.menuItemName = missingNames.getValue(it.menuItemId)
        }

        return toResponse(order)
    }

    private fun buildOrder(
        request: CreateOrderRequest,
        visit: Visit,
        products: Map<Int, MenuItemResponse>
    ): Order {
        val order = Order(
            visit = visit,
            waiterId = request.waiterId,
            workerId = request.workerId,
            totalAmount = BigDecimal.ZERO
        )
        order.items = request.items.map { item ->
            val product = products.getValue(item.menuItemId)
            OrderItem(
                order = order,
                menuItemId = item.menuItemId,
                menuItemName = product.name,
                unitPrice = product.price,
                quantity = item.quantity
            )
        }.toMutableList()
        order.totalAmount = order.items.sumOf { it.unitPrice * it.quantity.toBigDecimal() }
        return order
    }

    private fun toResponse(order: Order) = OrderResponse.Builder()
        .idOrder(requireNotNull(order.idOrder))
        .visitId(requireNotNull(order.visit.idVisit))
        .waiterId(order.waiterId)
        .workerId(order.workerId)
        .items(order.items.map { item ->
            OrderItemResponse.Builder()
                .menuItemId(item.menuItemId)
                .name(requireNotNull(item.menuItemName))
                .quantity(item.quantity)
                .unitPrice(item.unitPrice)
                .subtotal(item.unitPrice * item.quantity.toBigDecimal())
                .build()
        })
        .totalAmount(order.totalAmount)
        .createdAt(order.createdAt)
        .build()
}
