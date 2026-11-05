package ru.severnoyesiyanieback.user.entity

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("users")
data class User(
    @Id @Column("id_user")
    val idUser: Int? = null,
    @field:NotBlank @field:Size(max = 50)
    val name: String,
    @field:NotBlank @field:Email @field:Size(max = 100)
    val email: String,
    val role: UserRole = UserRole.CLIENT
)
