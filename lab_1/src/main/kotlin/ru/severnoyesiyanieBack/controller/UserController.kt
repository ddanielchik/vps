package ru.severnoyesiyanieBack.controller

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.severnoyesiyanieBack.dto.CreateUserRequest
import ru.severnoyesiyanieBack.dto.UpdateUserRequest
import ru.severnoyesiyanieBack.dto.UserResponse
import ru.severnoyesiyanieBack.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping


@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    @PostMapping
    fun createUser(
        @Valid @RequestBody createUserRequest: CreateUserRequest
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(createUserRequest))
    }

    @GetMapping("/{userId}")
    fun getUserById(@PathVariable userId: Int): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getUserById(userId))

    }
    @PutMapping("/{userId}")
        fun updateUser(@PathVariable userId: Int, @Valid @RequestBody updateUserRequest: UpdateUserRequest): ResponseEntity<UserResponse> {
            return ResponseEntity.ok(userService.updateUser(userId, updateUserRequest))
    }

    @DeleteMapping("/{userId}")
    fun deleteUserById(@PathVariable userId: Int): ResponseEntity<Void> {
        userService.deleteUser(userId)
        return ResponseEntity.noContent().build()
    }


}
