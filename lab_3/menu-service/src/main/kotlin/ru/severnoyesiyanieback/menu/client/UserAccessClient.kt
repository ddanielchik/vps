package ru.severnoyesiyanieback.menu.client

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import reactivefeign.spring.config.ReactiveFeignClient
import reactor.core.publisher.Mono

@ReactiveFeignClient(name = "user-service", url = "\${clients.user.url:}")
interface UserAccessClient {
    @GetMapping("/internal/v1/users/{userId}")
    fun getUserAccess(@PathVariable("userId") userId: Int): Mono<UserAccessResponse>
}

data class UserAccessResponse(val role: String, val status: String)
