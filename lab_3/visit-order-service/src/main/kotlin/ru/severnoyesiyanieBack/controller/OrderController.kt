package ru.severnoyesiyanieBack.controller

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.severnoyesiyanieBack.dto.CreateOrderRequest
import ru.severnoyesiyanieBack.dto.OrderResponse
import ru.severnoyesiyanieBack.security.currentUserId
import ru.severnoyesiyanieBack.service.OrderService

@RestController
@RequestMapping("/api/v1/orders")
@SecurityRequirement(name = "bearerAuth")
class OrderController(private val orderService: OrderService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(
        @Valid @RequestBody request: CreateOrderRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): OrderResponse {
        val currentUserId = jwt.currentUserId()
        return orderService.createOrder(request, currentUserId)
    }

    @GetMapping
    fun getOrders(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<List<OrderResponse>> {
        val orders = orderService.getOrders(jwt.currentUserId(), page, size)
        return ResponseEntity.ok().header("X-Total-Count", orders.totalElements.toString()).body(orders.content)
    }

    @GetMapping("/{orderId}")
    fun getOrderById(@PathVariable orderId: Int, @AuthenticationPrincipal jwt: Jwt): OrderResponse =
        orderService.getOrderById(orderId, jwt.currentUserId())

    @PostMapping("/{orderId}/start")
    fun startPreparingOrder(@PathVariable orderId: Int, @AuthenticationPrincipal jwt: Jwt): OrderResponse =
        orderService.startPreparingOrder(orderId, jwt.currentUserId())
}
