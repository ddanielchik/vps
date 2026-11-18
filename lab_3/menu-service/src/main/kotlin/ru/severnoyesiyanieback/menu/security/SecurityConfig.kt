package ru.severnoyesiyanieback.menu.security

import feign.FeignException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.menu.client.UserAccessClient
import ru.severnoyesiyanieback.security.JwtKeys

@Configuration(proxyBeanMethods = false)
class SecurityConfig(@Value("\${app.jwt.secret}") secret: String) {
    private val jwtKeys = JwtKeys(secret)

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder = jwtKeys.reactiveDecoder()

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity, users: UserAccessClient): SecurityWebFilterChain =
        http
            .csrf { csrf -> csrf.disable() }
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange { exchanges ->
                exchanges.pathMatchers(HttpMethod.GET, "/api/v1/menu", "/api/v1/menu/**", "/v3/api-docs/**", "/actuator/health")
                    .permitAll()
                    .pathMatchers("/api/v1/menu", "/api/v1/menu/**")
                    .access { authentication, _ ->
                        authentication.ofType(JwtAuthenticationToken::class.java)
                            .mapNotNull { jwtAuthentication -> jwtAuthentication.name.toIntOrNull() }
                            .flatMap(users::getUserAccess)
                            .map<AuthorizationResult> { currentUser ->
                                AuthorizationDecision(currentUser.role == "LORD" && currentUser.status == "ACTIVE")
                            }
                            .onErrorResume(FeignException.NotFound::class.java) { Mono.empty() }
                            .onErrorMap(FeignException::class.java) { failure ->
                                ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot check current user", failure)
                            }
                            .defaultIfEmpty(AuthorizationDecision(false))
                    }
                    .anyExchange().denyAll()
            }
            .oauth2ResourceServer { resourceServer -> resourceServer.jwt {} }
            .build()
}
