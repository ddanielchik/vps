package ru.severnoyesiyanieBack.entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size


@Entity
@Table(name = "users")
class User (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user")
    var idUser: Int? = null,

    
    @field:NotBlank
    @field:Size(max=50)
    @Column(name="name", nullable = false, length = 50 )
    var name: String ="",

    @field:NotBlank
    @field:Size(max=100)
    @field:Email
    @Column(name="email", nullable = false, unique = true, length = 100)
    var email:String="",


    @Enumerated(EnumType.STRING)
    @Column(name="role", nullable = false, length= 15)
    var role: UserRole = UserRole.CLIENT
    )

