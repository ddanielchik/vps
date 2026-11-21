package ru.severnoyesiyanieBack.client

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ResponseStatusException
import reactivefeign.spring.config.ReactiveFeignClient
import reactor.core.publisher.Mono
import ru.severnoyesiyanieBack.dto.UserResponse
import ru.severnoyesiyanieBack.entity.UserStatus

@ReactiveFeignClient(name = "user-service", url = "\${clients.user.url:}")
interface UserClient {
    @GetMapping("/internal/v1/users/{userId}")
    fun getUser(@PathVariable("userId") userId: Int): Mono<UserResponse>
}

fun UserClient.getActiveUser(currentUserId: Int): UserResponse {
    val currentUser = getUser(currentUserId).awaitResponse()
    if (currentUser.status != UserStatus.ACTIVE) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "The account is blocked")
    }
    return currentUser
}
