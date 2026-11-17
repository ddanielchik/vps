package ru.severnoyesiyanieBack.dto

import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.entity.UserStatus

data class UserResponse(
    val idUser: Int,
    val name: String,
    val email: String,
    val role: UserRole,
    val status: UserStatus = UserStatus.ACTIVE
)