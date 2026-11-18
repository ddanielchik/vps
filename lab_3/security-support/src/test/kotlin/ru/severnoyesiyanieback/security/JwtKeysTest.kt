package ru.severnoyesiyanieback.security

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException

class JwtKeysTest {
    private val jwtKeys = JwtKeys("a1".repeat(32))

    @Test
    fun `the same token is accepted by both types of service`() {
        val token = signedToken()
        assertEquals("42", jwtKeys.decoder().decode(token).subject)
        assertEquals("42", jwtKeys.reactiveDecoder().decode(token).block()?.subject)
    }

    @Test
    fun `wrong signing key expired token and wrong issuer are rejected`() {
        val differentKeys = JwtKeys("b2".repeat(32))
        assertThrows<JwtException> { differentKeys.decoder().decode(signedToken()) }
        assertThrows<JwtException> {
            jwtKeys.decoder().decode(signedToken(expiresAt = Instant.now().minusSeconds(180)))
        }
        assertThrows<JwtException> { jwtKeys.reactiveDecoder().decode(signedToken(issuer = "another-app")).block() }
    }

    @Test
    fun `signing key must contain exactly 64 hex digits`() {
        assertThrows<IllegalArgumentException> { JwtKeys("a1") }
        assertThrows<IllegalArgumentException> { JwtKeys("z1".repeat(32)) }
    }

    private fun signedToken(
        issuer: String = JWT_ISSUER,
        expiresAt: Instant = Instant.now().plusSeconds(180)
    ): String {
        val claims = JwtClaimsSet.builder()
            .issuer(issuer).subject("42").issuedAt(expiresAt.minusSeconds(300)).expiresAt(expiresAt).build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtKeys.encoder().encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }
}
