package ru.severnoyesiyanieback.user.service

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.severnoyesiyanieback.user.dto.LoginRequest
import ru.severnoyesiyanieback.user.dto.LoginResponse
import ru.severnoyesiyanieback.user.entity.User
import ru.severnoyesiyanieback.user.entity.UserStatus
import ru.severnoyesiyanieback.user.repository.UserRepository
import ru.severnoyesiyanieback.user.security.JWT_ISSUER
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtEncoder: JwtEncoder
) {
    private val dummyHash = passwordEncoder.encode(UUID.randomUUID().toString())

    fun login(request: LoginRequest): Mono<LoginResponse> = users.findByEmail(request.email)
        .singleOptional()
        .flatMap { result ->
            Mono.fromCallable {
                val user = result.orElse(null)
                val hash = user?.passwordHash
                val passwordMatches = passwordEncoder.matches(request.password, hash ?: dummyHash)
                if (user == null || hash == null || !passwordMatches || user.status != UserStatus.ACTIVE) {
                    throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
                }
                issueToken(user)
            }.subscribeOn(Schedulers.boundedElastic())
        }

    private fun issueToken(user: User): LoginResponse {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(JWT_ISSUER)
            .subject(requireNotNull(user.idUser).toString())
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(30)))
            .claim("role", user.role.name)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return LoginResponse(jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue)
    }
}
