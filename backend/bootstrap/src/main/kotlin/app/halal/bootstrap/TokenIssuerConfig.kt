package app.halal.bootstrap

import app.halal.application.account.TokenIssuer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.Duration
import java.util.Base64

/**
 * Wires the [TokenIssuer] and (via the shared [KeyPair]) the OAuth2 resource
 * server decoder from one RSA key pair/source.
 *
 * The RS256 signing key is loaded from `app.jwt.rsa-private-key-base64` (base64
 * of a PKCS#8 DER private key) when provided; otherwise an ephemeral pair is
 * generated for local dev. Because only the private key is configured, the
 * matching public key is reconstructed from its CRT parameters, so the issuer
 * (signs with the private key) and the resource server (verifies with the
 * public key) always agree on the pair.
 *
 * Security note: an ephemeral key is regenerated on every boot, which
 * invalidates previously-issued access tokens after restart and is only for
 * local development. Production must supply a stable `app.jwt.*` key — the
 * OAuth2 resource server requires the JWT to verify against the SAME pair the
 * issuer used, so a stable configured key is mandatory outside local dev.
 */
@Configuration
class TokenIssuerConfig {

    /** The single RSA key pair shared by the token issuer and the resource server verifier. */
    @Bean
    fun jwtRsaKeyPair(
        @Value("\${app.jwt.rsa-private-key-base64:}") privateKeyB64: String,
    ): KeyPair = loadOrGenerateKeyPair(privateKeyB64)

    @Bean
    fun tokenIssuer(
        jwtRsaKeyPair: KeyPair,
        @Value("\${app.jwt.issuer:halal-food-finder}") issuer: String,
        @Value("\${app.jwt.access-ttl-seconds:900}") accessTtlSeconds: Long,
    ): TokenIssuer = JwtTokenIssuer(
        signingKey = jwtRsaKeyPair.private as RSAPrivateKey,
        issuer = issuer,
        accessTokenTtl = Duration.ofSeconds(accessTtlSeconds),
    )

    private fun loadOrGenerateKeyPair(privateKeyB64: String): KeyPair {
        if (privateKeyB64.isNotBlank()) {
            val der = Base64.getDecoder().decode(privateKeyB64)
            val keyFactory = KeyFactory.getInstance("RSA")
            val private = keyFactory.generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateCrtKey
            val public = keyFactory.generatePublic(
                RSAPublicKeySpec(private.modulus, private.publicExponent),
            ) as RSAPublicKey
            return KeyPair(public, private)
        }
        val keyGen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        return keyGen.generateKeyPair()
    }
}