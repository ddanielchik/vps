package ru.severnoyesiyanieback.user.dto

import ru.severnoyesiyanieback.user.entity.UserRole
import ru.severnoyesiyanieback.user.entity.UserStatus

data class UserResponse(val idUser: Int, val name: String, val email: String, val role: UserRole, val status: UserStatus = UserStatus.ACTIVE) {
    class Builder {
        private var idUser: Int? = null
        private var name: String? = null
        private var email: String? = null
        private var role: UserRole? = null
        private var status: UserStatus? = UserStatus.ACTIVE

        fun idUser(value: Int) = apply { idUser = value }
        fun name(value: String) = apply { name = value }
        fun email(value: String) = apply { email = value }
        fun role(value: UserRole) = apply { role = value }
        fun status(value: UserStatus) = apply { status = value }

        fun build() = UserResponse(
            idUser = requireNotNull(idUser) { "idUser is required" },
            name = requireNotNull(name) { "name is required" },
            email = requireNotNull(email) { "email is required" },
            role = requireNotNull(role) { "role is required" },
            status = requireNotNull(status) { "status is required" }
        )
    }
}
