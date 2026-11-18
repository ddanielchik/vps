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
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import ru.severnoyesiyanieback.security.JwtKeys
import ru.severnoyesiyanieback.user.entity.UserRole
import ru.severnoyesiyanieback.user.entity.UserStatus
import ru.severnoyesiyanieback.user.repository.UserRepository

@Configuration(proxyBeanMethods = false)
class SecurityConfig(@Value("\${app.jwt.secret}") secret: String) {
    private val jwtKeys = JwtKeys(secret)

    @Bean
    fun passwordEncoder(): PasswordEncoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    @Bean
    fun jwtEncoder(): JwtEncoder = jwtKeys.encoder()

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder = jwtKeys.reactiveDecoder()

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity, users: UserRepository): SecurityWebFilterChain =
        http
            .csrf { csrf -> csrf.disable() }
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange { exchanges ->
                exchanges.pathMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .pathMatchers(HttpMethod.GET, "/internal/v1/users/{userId}", "/v3/api-docs/**", "/actuator/health")
                    .permitAll()
                    .pathMatchers("/api/v1/users", "/api/v1/users/**")
                    .access { authentication, context ->
                        authentication.ofType(JwtAuthenticationToken::class.java)
                            .mapNotNull { jwtAuthentication -> jwtAuthentication.name.toIntOrNull() }
                            .flatMap(users::findById)
                            .map<AuthorizationResult> { currentUser ->
                                val request = context.exchange.request
                                val canReadOwnProfile = request.method == HttpMethod.GET &&
                                    request.path.value() == "/api/v1/users/${currentUser.idUser}"
                                AuthorizationDecision(
                                    currentUser.status == UserStatus.ACTIVE &&
                                        (currentUser.role == UserRole.LORD || canReadOwnProfile)
                                )
                            }
                            .defaultIfEmpty(AuthorizationDecision(false))
                    }
                    .anyExchange().denyAll()
            }
            .oauth2ResourceServer { resourceServer -> resourceServer.jwt {} }
            .build()
}
