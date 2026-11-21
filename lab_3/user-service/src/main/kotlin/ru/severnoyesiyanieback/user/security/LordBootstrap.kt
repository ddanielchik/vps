package ru.severnoyesiyanieback.user.security

import jakarta.validation.Validator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.user.dto.CreateUserRequest
import ru.severnoyesiyanieback.user.entity.User
import ru.severnoyesiyanieback.user.entity.UserRole
import ru.severnoyesiyanieback.user.repository.UserRepository
import java.time.Duration

@Component
class LordBootstrap(
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
    private val validator: Validator,
    @param:Value("\${app.lord.email:}") private val email: String,
    @param:Value("\${app.lord.password:}") private val password: String
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (email.isEmpty() && password.isEmpty()) return

        val request = CreateUserRequest("Supervisor", email, password)
        require(validator.validate(request).isEmpty()) { "Set a valid APP_LORD_EMAIL and APP_LORD_PASSWORD" }
        val supervisor = users.findByEmail(email).switchIfEmpty(Mono.defer {
            users.save(User(name = request.name, email = email, role = UserRole.LORD, passwordHash = encoder.encode(password)))
                .onErrorResume(DuplicateKeyException::class.java) { users.findByEmail(email) }
        }).block(Duration.ofSeconds(15))

        check(supervisor?.role == UserRole.LORD && supervisor.passwordHash != null) {
            "APP_LORD_EMAIL is already assigned to another account"
        }
    }
}
