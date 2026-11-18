package ru.severnoyesiyanieback.menu.client

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import reactivefeign.spring.config.ReactiveFeignClient
import reactor.core.publisher.Mono

@ReactiveFeignClient(name = "visit-order-service", url = "\${clients.visits.url:}")
interface VisitOrderClient {
    @GetMapping("/internal/v1/orders/references/menu/{id}")
    fun hasMenuItemsWithoutName(@PathVariable("id") id: Int): Mono<Boolean>
}
