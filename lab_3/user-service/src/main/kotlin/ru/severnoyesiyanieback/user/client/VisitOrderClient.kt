package ru.severnoyesiyanieback.user.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(name = "visit-order-service", url = "\${clients.visits.url:}")
interface VisitOrderClient {
    @GetMapping("/api/orders/references/users/{id}")
    fun isUserInUse(@PathVariable("id") id: Int): Boolean
}
