package ru.severnoyesiyanieBack.security

import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieback.security.JwtKeys

@Configuration(proxyBeanMethods = false)
class SecurityConfig(@Value("\${app.jwt.secret}") secret: String) {
    private val jwtKeys = JwtKeys(secret)

    @Bean
    fun jwtDecoder(): JwtDecoder = jwtKeys.decoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { requests ->
                requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/actuator/health", "/internal/v1/orders/references/**")
                    .permitAll()
                    .requestMatchers("/api/v1/visits", "/api/v1/visits/**", "/api/v1/orders", "/api/v1/orders/**")
                    .authenticated()
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { resourceServer -> resourceServer.jwt {} }
            .build()
}

internal fun Jwt.currentUserId(): Int =
    subject?.toIntOrNull()?.takeIf { userId -> userId > 0 }
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject")
