package ru.severnoyesiyanieBack.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.severnoyesiyanieBack.entity.User

interface UserRepository : JpaRepository<User, Int> {
    fun existsByEmail(email: String): Boolean

}