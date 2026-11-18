package ru.severnoyesiyanieBack.controller

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.severnoyesiyanieBack.service.OrderService

@Hidden
@RestController
@RequestMapping("/internal/v1/orders/references")
class InternalOrderController(private val orderService: OrderService) {
    @GetMapping("/users/{userId}")
    fun isUserInUse(@PathVariable userId: Int): Boolean = orderService.isUserInUse(userId)

    @GetMapping("/menu/{menuItemId}")
    fun hasMenuItemsWithoutName(@PathVariable menuItemId: Int): Boolean = orderService.hasMenuItemsWithoutName(menuItemId)
}
