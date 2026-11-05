package ru.severnoyesiyanieBack.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import ru.severnoyesiyanieBack.dto.UserResponse

@FeignClient(name = "user-service", url = "\${clients.user.url:}")
interface UserClient {
    @GetMapping("/api/users/{userId}")
    fun getUser(@PathVariable("userId") userId: Int): UserResponse

    @DeleteMapping("/internal/users/{userId}")
    fun deleteUser(@PathVariable("userId") userId: Int)
}
