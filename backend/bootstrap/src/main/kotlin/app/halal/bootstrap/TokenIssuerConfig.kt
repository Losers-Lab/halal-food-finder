package app.halal.bootstrap

import app.halal.application.account.TokenIssuer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64

/**
 * Wires the [TokenIssuer] from configuration. The RS256 signing key is loaded
 * from `app.jwt.rsa-private-key-base64` (base64 of a PKCS#8 DER private key)
 * when provided; otherwise an ephemeral key is generated for local dev.
 *
 * Security note: an ephemeral key is regenerated on every boot, which
 * invalidates previously-issued access tokens after restart and is only for
 * local development. Production must supply a stable `app.jwt.*` key. This is
 * flagged for Omar's security review; the Spring Security resource-server
 * filter enforcement and proper key management are follow-up work.
 */
@Configuration
class TokenIssuerConfig {

    @Bean
    fun tokenIssuer(
        @Value("\${app.jwt.issuer:halal-food-finder}") issuer: String,
        @Value("\${app.jwt.access-ttl-seconds:900}") accessTtlSeconds: Long,
        @Value("\${app.jwt.rsa-private-key-base64:}") privateKeyB64: String,
    ): TokenIssuer = JwtTokenIssuer(
        signingKey = loadOrGeneratePrivateKey(privateKeyB64),
        issuer = issuer,
        accessTokenTtl = Duration.ofSeconds(accessTtlSeconds),
    )

    private fun loadOrGeneratePrivateKey(privateKeyB64: String): RSAPrivateKey {
        if (privateKeyB64.isNotBlank()) {
            val der = Base64.getDecoder().decode(privateKeyB64)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
        }
        val keyGen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        return keyGen.generateKeyPair().private as RSAPrivateKey
    }
}