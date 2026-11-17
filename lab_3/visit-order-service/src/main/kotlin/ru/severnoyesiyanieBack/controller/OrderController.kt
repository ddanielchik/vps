package ru.severnoyesiyanieBack.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.severnoyesiyanieBack.dto.CreateOrderRequest
import ru.severnoyesiyanieBack.dto.OrderResponse
import ru.severnoyesiyanieBack.service.OrderService

@RestController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(@Valid @RequestBody request: CreateOrderRequest): OrderResponse =
        orderService.createOrder(request)

    @GetMapping("/{id}")
    fun getOrderById(@PathVariable id: Int): OrderResponse = orderService.getOrderById(id)

    @GetMapping("/references/users/{id}")
    fun isUserInUse(@PathVariable id: Int): Boolean = orderService.isUserInUse(id)

    @GetMapping("/references/menu/{id}")
    fun hasMenuItemsWithoutName(@PathVariable id: Int): Boolean = orderService.hasMenuItemsWithoutName(id)
}
