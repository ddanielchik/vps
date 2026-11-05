package ru.severnoyesiyanieBack.dto

import ru.severnoyesiyanieBack.entity.UserRole

data class UserResponse(
    val idUser: Int,
    val name: String,
    val email: String,
    val role: UserRole
)