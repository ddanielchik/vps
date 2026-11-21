package ru.severnoyesiyanieBack.service

import java.math.BigDecimal
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieBack.client.MenuClient
import ru.severnoyesiyanieBack.client.UserClient
import ru.severnoyesiyanieBack.client.awaitResponse
import ru.severnoyesiyanieBack.client.getActiveUser
import ru.severnoyesiyanieBack.dto.CreateOrderRequest
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.dto.OrderItemResponse
import ru.severnoyesiyanieBack.dto.OrderResponse
import ru.severnoyesiyanieBack.dto.UserResponse
import ru.severnoyesiyanieBack.entity.Order
import ru.severnoyesiyanieBack.entity.OrderItem
import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.entity.Visit
import ru.severnoyesiyanieBack.entity.VisitStatus
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
    fun createOrder(request: CreateOrderRequest, currentUserId: Int): OrderResponse {
        val currentUser = userClient.getActiveUser(currentUserId)
        if (currentUser.role != UserRole.CLIENT && currentUser.role != UserRole.WAITER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only clients and waiters can place orders")
        }

        val visit = visitRepository.findById(request.visitId)
            .orElseThrow { VisitNotFoundException(request.visitId) }
        checkVisitAccess(currentUser, visit.clientId)
        if (visit.status != VisitStatus.OPEN) {
            throw VisitAlreadyClosedException(request.visitId)
        }

        val products = request.items
            .map { item -> item.menuItemId }
            .distinct()
            .associateWith { menuItemId -> menuClient.getMenuItem(menuItemId).awaitResponse() }

        val waiterId = currentUserId.takeIf { currentUser.role == UserRole.WAITER }
        return toResponse(orderRepository.save(buildOrder(request, visit, products, waiterId)))
    }

    @Transactional
    fun getOrderById(orderId: Int, currentUserId: Int): OrderResponse {
        val currentUser = userClient.getActiveUser(currentUserId)
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException(orderId) }
        checkVisitAccess(currentUser, order.visit.clientId)
        return toReadableResponse(order)
    }

    @Transactional
    fun getOrders(currentUserId: Int, page: Int, size: Int): Page<OrderResponse> {
        val currentUser = userClient.getActiveUser(currentUserId)
        if (page < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative")
        val pageable = PageRequest.of(page, size.coerceIn(1, 50), Sort.by("idOrder").descending())
        val orders = if (currentUser.role == UserRole.CLIENT) {
            orderRepository.findByVisitClientId(currentUserId, pageable)
        } else {
            orderRepository.findAll(pageable)
        }
        return orders.map(::toReadableResponse)
    }

    @Transactional
    fun startPreparingOrder(orderId: Int, currentUserId: Int): OrderResponse {
        val currentUser = userClient.getActiveUser(currentUserId)
        if (currentUser.role != UserRole.WORKER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only workers can start preparing orders")
        }
        val order = orderRepository.findLockedByIdOrder(orderId) ?: throw OrderNotFoundException(orderId)
        if (order.visit.status != VisitStatus.OPEN) {
            throw VisitAlreadyClosedException(requireNotNull(order.visit.idVisit))
        }
        if (order.workerId != null && order.workerId != currentUserId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Another worker is preparing this order")
        }
        order.workerId = currentUserId
        return toReadableResponse(order)
    }

    private fun checkVisitAccess(currentUser: UserResponse, visitOwnerId: Int) {
        if (currentUser.role == UserRole.CLIENT && currentUser.idUser != visitOwnerId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "This visit belongs to another client")
        }
    }

    private fun toReadableResponse(order: Order): OrderResponse {
        val itemsWithoutName = order.items.filter { item -> item.menuItemName == null }
        val namesByMenuItemId = itemsWithoutName.map { item -> item.menuItemId }.distinct()
            .associateWith { menuItemId -> menuClient.getMenuItem(menuItemId).awaitResponse().name }
        itemsWithoutName.forEach { item -> item.menuItemName = namesByMenuItemId.getValue(item.menuItemId) }
        return toResponse(order)
    }

    private fun buildOrder(
        request: CreateOrderRequest,
        visit: Visit,
        products: Map<Int, MenuItemResponse>,
        waiterId: Int?
    ): Order {
        val order = Order(visit = visit, waiterId = waiterId, totalAmount = BigDecimal.ZERO)
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
        order.totalAmount = order.items.sumOf { item -> item.unitPrice * item.quantity.toBigDecimal() }
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
