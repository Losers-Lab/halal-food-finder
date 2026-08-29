package app.halal.bootstrap

import app.halal.application.account.SessionTokens
import app.halal.application.account.TokenIssuer
import app.halal.domain.account.Account
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * RS256 JWT access-token + opaque refresh-token issuer (ratified auth stack).
 *
 * The access token is a short-lived RSA-signed JWT carrying the account's RBAC
 * role as a claim (plus sub/email/iat/exp). The refresh token is a 256-bit
 * cryptographically-random opaque string; the infrastructure adapter persists
 * only its SHA-256 hash, so it is rotated as a single-use credential.
 */
class JwtTokenIssuer(
    private val signingKey: RSAPrivateKey,
    private val issuer: String,
    private val accessTokenTtl: Duration,
) : TokenIssuer {

    private val signer = RSASSASigner(signingKey)
    private val random = SecureRandom()

    override fun issue(account: Account): SessionTokens {
        val now = Instant.now()
        val accessToken = signAccessToken(account, now)
        return SessionTokens(
            accessToken = accessToken,
            refreshToken = generateRefreshToken(),
            tokenType = "Bearer",
            accessTokenExpiresInSeconds = accessTokenTtl.seconds,
        )
    }

    private fun signAccessToken(account: Account, now: Instant): String {
        val claims = JWTClaimsSet.Builder()
            .subject(account.id.toString())
            .issuer(issuer)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(accessTokenTtl)))
            .claim("email", account.email.value)
            .claim("role", account.role.name)
            .build()

        val signed = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
            claims,
        )
        signed.sign(signer)
        return signed.serialize()
    }

    private fun generateRefreshToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}