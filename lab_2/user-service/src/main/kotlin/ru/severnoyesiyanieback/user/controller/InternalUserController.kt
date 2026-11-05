package ru.severnoyesiyanieback.user.controller

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.user.service.UserService

@Hidden
@RestController
@RequestMapping("/internal/users")
class InternalUserController(private val userService: UserService) {
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable userId: Int): Mono<Void> = userService.deleteUser(userId)
}
