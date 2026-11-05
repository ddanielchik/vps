package ru.severnoyesiyanieback.user.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.user.dto.CreateUserRequest
import ru.severnoyesiyanieback.user.dto.UpdateUserRequest
import ru.severnoyesiyanieback.user.dto.UserResponse
import ru.severnoyesiyanieback.user.service.UserService

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@Valid @RequestBody request: CreateUserRequest): Mono<UserResponse> =
        userService.createUser(request)

    @GetMapping("/{userId}")
    fun getUserById(@PathVariable userId: Int): Mono<UserResponse> = userService.getUserById(userId)

    @PutMapping("/{userId}")
    fun updateUser(@PathVariable userId: Int, @Valid @RequestBody request: UpdateUserRequest): Mono<UserResponse> =
        userService.updateUser(userId, request)
}
