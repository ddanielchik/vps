package ru.severnoyesiyanieBack.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.severnoyesiyanieBack.dto.CreateOrderRequest
import ru.severnoyesiyanieBack.dto.OrderResponse
import ru.severnoyesiyanieBack.service.OrderService

@RestController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {

    @PostMapping
    fun createOrder(
        @Valid @RequestBody request: CreateOrderRequest
    ): ResponseEntity<OrderResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request))
    }

    @GetMapping("/{id}")
    fun getOrderById(@PathVariable id: Int): ResponseEntity<OrderResponse> {
        return ResponseEntity.ok(orderService.getOrderById(id))
    }
}
