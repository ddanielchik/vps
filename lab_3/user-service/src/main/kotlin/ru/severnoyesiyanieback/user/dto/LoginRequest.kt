package ru.severnoyesiyanieback.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank @field:Email @field:Size(max = 100)
    val email: String,
    @field:NotBlank @field:Size(max = 128)
    val password: String
)
