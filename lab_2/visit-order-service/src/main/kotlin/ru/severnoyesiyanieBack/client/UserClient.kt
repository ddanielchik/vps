package ru.severnoyesiyanieBack.client

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import reactivefeign.spring.config.ReactiveFeignClient
import reactor.core.publisher.Mono
import ru.severnoyesiyanieBack.dto.UserResponse

@ReactiveFeignClient(name = "user-service", url = "\${clients.user.url:}")
interface UserClient {
    @GetMapping("/api/v1/users/{userId}")
    fun getUser(@PathVariable("userId") userId: Int): Mono<UserResponse>
}
