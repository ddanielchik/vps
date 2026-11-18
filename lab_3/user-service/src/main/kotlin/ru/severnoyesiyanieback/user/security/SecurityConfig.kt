package ru.severnoyesiyanieback.user.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import ru.severnoyesiyanieback.user.entity.UserRole
import ru.severnoyesiyanieback.user.entity.UserStatus
import ru.severnoyesiyanieback.user.repository.UserRepository
import java.util.HexFormat
import javax.crypto.spec.SecretKeySpec

internal const val JWT_ISSUER = "urn:severnoye-siyanie:lab3"

@Configuration(proxyBeanMethods = false)
class SecurityConfig(@Value("\${app.jwt.secret}") jwtSecret: String) {
    private val signingKey = SecretKeySpec(
        HexFormat.of().parseHex(jwtSecret.also {
            require(it.matches(Regex("[0-9a-fA-F]{64}"))) { "APP_JWT_SECRET must contain 64 hex characters" }
        }),
        "HmacSHA256"
    )

    @Bean
    fun passwordEncoder(): PasswordEncoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder.withSecretKey(signingKey)
        .algorithm(MacAlgorithm.HS256).build()

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder = NimbusReactiveJwtDecoder.withSecretKey(signingKey)
        .macAlgorithm(MacAlgorithm.HS256).build()
        .apply { setJwtValidator(JwtValidators.createDefaultWithIssuer(JWT_ISSUER)) }

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity, users: UserRepository): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange { exchanges ->
                exchanges.pathMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/users/**")
                    .access { authentication, _ ->
                        authentication.ofType(JwtAuthenticationToken::class.java)
                            .mapNotNull { it.name.toIntOrNull() }
                            .flatMap(users::findById)
                            .map<AuthorizationResult> { user ->
                                AuthorizationDecision(user.role == UserRole.LORD && user.status == UserStatus.ACTIVE)
                            }
                            .defaultIfEmpty(AuthorizationDecision(false))
                    }
                    .anyExchange().permitAll()
            }
            .oauth2ResourceServer { it.jwt {} }
            .build()
}
