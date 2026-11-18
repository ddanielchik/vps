package ru.severnoyesiyanieback.security

import java.util.HexFormat
import javax.crypto.spec.SecretKeySpec
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder

const val JWT_ISSUER = "urn:severnoye-siyanie:lab3"

class JwtKeys(secret: String) {
    private val signingKey: SecretKeySpec

    init {
        require(secret.matches(Regex("[0-9a-fA-F]{64}"))) {
            "APP_JWT_SECRET must contain 64 hex characters"
        }
        signingKey = SecretKeySpec(HexFormat.of().parseHex(secret), "HmacSHA256")
    }

    fun encoder(): JwtEncoder =
        NimbusJwtEncoder.withSecretKey(signingKey).algorithm(MacAlgorithm.HS256).build()

    fun decoder(): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build()
            .apply { setJwtValidator(JwtValidators.createDefaultWithIssuer(JWT_ISSUER)) }

    fun reactiveDecoder(): ReactiveJwtDecoder =
        NimbusReactiveJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build()
            .apply { setJwtValidator(JwtValidators.createDefaultWithIssuer(JWT_ISSUER)) }
}
