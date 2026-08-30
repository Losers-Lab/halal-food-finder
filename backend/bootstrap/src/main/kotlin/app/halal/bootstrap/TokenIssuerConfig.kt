package app.halal.bootstrap

import app.halal.application.account.TokenIssuer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.KeyPair
import java.security.interfaces.RSAPrivateKey
import java.time.Duration

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
 * Fail-fast (sc-134): if `app.jwt.rsa-private-key-base64` IS provided but is
 * invalid (not base64 / not a PKCS#8 RSA key), startup aborts with a clear,
 * secret-safe message instead of silently generating a key or failing later
 * with an obscure crypto exception. See [JwtRsaKeyPairLoader].
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
        @Value("\${spring.profiles.active:}") activeProfiles: String,
    ): KeyPair {
        // Prod-profile key guard (sc-134 follow-up): refuse to boot when a prod
        // profile is active and the signing key is blank or a known dev key.
        // "prod" is the agreed production profile name; anything else (dev, test,
        // unset local runs) keeps the old lenient behavior.
        val isProd = activeProfiles.split(",").map { it.trim() }.contains("prod")
        return JwtRsaKeyPairLoader.loadKeyPair(privateKeyB64, prodProfile = isProd)
    }

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
}