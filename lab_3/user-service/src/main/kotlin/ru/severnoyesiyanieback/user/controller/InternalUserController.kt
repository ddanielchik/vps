package ru.severnoyesiyanieback.user.controller

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.user.entity.UserRole
import ru.severnoyesiyanieback.user.entity.UserStatus
import ru.severnoyesiyanieback.user.service.UserService

@Hidden
@RestController
@RequestMapping("/internal/v1/users")
class InternalUserController(private val userService: UserService) {
    @GetMapping("/{userId}")
    fun getUserAccess(@PathVariable userId: Int): Mono<UserAccessResponse> =
        userService.getUserById(userId).map { user -> UserAccessResponse(user.idUser, user.role, user.status) }
}

data class UserAccessResponse(val idUser: Int, val role: UserRole, val status: UserStatus)
