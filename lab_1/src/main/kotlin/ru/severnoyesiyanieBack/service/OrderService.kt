package ru.severnoyesiyanieBack.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.severnoyesiyanieBack.dto.CreateOrderRequest
import ru.severnoyesiyanieBack.dto.OrderItemResponse
import ru.severnoyesiyanieBack.dto.OrderResponse
import ru.severnoyesiyanieBack.entity.Order
import ru.severnoyesiyanieBack.entity.OrderItem
import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.entity.VisitStatus
import ru.severnoyesiyanieBack.exception.*
import ru.severnoyesiyanieBack.repository.OrderRepository
import ru.severnoyesiyanieBack.repository.UserRepository
import ru.severnoyesiyanieBack.repository.VisitRepository
import java.math.BigDecimal

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val visitRepository: VisitRepository,
    private val userRepository: UserRepository,
    private val menuItemService: MenuItemService
) {

    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderResponse {

        val visit = visitRepository.findById(request.visitId)
            .orElseThrow { VisitNotFoundException(request.visitId) }

        if (visit.status != VisitStatus.OPEN) {
            throw VisitAlreadyClosedException(request.visitId)
        }

        val waiter = userRepository.findById(request.waiterId)
            .orElseThrow { UserNotFoundException(request.waiterId) }

        val worker = userRepository.findById(request.workerId)
            .orElseThrow { UserNotFoundException(request.workerId) }

        if (waiter.role != UserRole.WAITER) {
            throw InvalidUserRoleException(
                request.waiterId,
                UserRole.WAITER
            )
        }

        if (worker.role != UserRole.WORKER) {
            throw InvalidUserRoleException(
                request.workerId,
                UserRole.WORKER
            )
        }

        val orderedProducts = request.items.map { item ->

            val product = menuItemService.getMenuItemEntityById(
                item.menuItemId
            )

            product to item.quantity
        }

        val totalAmount = orderedProducts.sumOf { (product, quantity) ->
            product.price * BigDecimal.valueOf(quantity.toLong())
        }

        val order = Order(
            visit = visit,
            waiter = waiter,
            worker = worker,
            totalAmount = totalAmount
        )

        orderedProducts.forEach { (product, quantity) ->

            val orderItem = OrderItem(
                order = order,
                product = product,
                unitPrice = product.price,
                quantity = quantity
            )

            order.items.add(orderItem)
        }

        val savedOrder = orderRepository.save(order)

        return toResponse(savedOrder)
    }

    @Transactional(readOnly = true)
    fun getOrderById(id: Int): OrderResponse {

        val order = orderRepository.findById(id)
            .orElseThrow { OrderNotFoundException(id) }

        return toResponse(order)
    }

    private fun toResponse(order: Order): OrderResponse {

        return OrderResponse(
            idOrder = requireNotNull(order.idOrder),
            visitId = requireNotNull(order.visit.idVisit),
            waiterId = order.waiter?.idUser,
            workerId = order.worker?.idUser,

            items = order.items.map { item ->
                OrderItemResponse(
                    menuItemId = requireNotNull(item.product.idPosition),
                    name = item.product.name,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    subtotal = item.unitPrice *
                            BigDecimal.valueOf(item.quantity.toLong())
                )
            },

            totalAmount = order.totalAmount,
            createdAt = order.createdAt
        )
    }
}