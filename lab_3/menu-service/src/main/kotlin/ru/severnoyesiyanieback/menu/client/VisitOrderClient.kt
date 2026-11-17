package ru.severnoyesiyanieback.menu.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(name = "visit-order-service", url = "\${clients.visits.url:}")
interface VisitOrderClient {
    @GetMapping("/api/orders/references/menu/{id}")
    fun hasMenuItemsWithoutName(@PathVariable("id") id: Int): Boolean
}
