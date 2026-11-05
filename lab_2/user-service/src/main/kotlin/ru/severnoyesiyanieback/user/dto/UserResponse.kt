package ru.severnoyesiyanieback.user.dto

import ru.severnoyesiyanieback.user.entity.UserRole

data class UserResponse(val idUser: Int, val name: String, val email: String, val role: UserRole)
