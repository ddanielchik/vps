package ru.severnoyesiyanieback.user.controller

import jakarta.validation.Valid
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.user.dto.LoginRequest
import ru.severnoyesiyanieback.user.dto.LoginResponse
import ru.severnoyesiyanieback.user.service.AuthService

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): Mono<ResponseEntity<LoginResponse>> =
        authService.login(request).map { loginResponse ->
            ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(loginResponse)
        }
}
