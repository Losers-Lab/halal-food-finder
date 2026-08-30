package app.halal.bootstrap

import app.halal.domain.account.Account
import app.halal.domain.account.Email
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.SignedJWT
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * sc-135 Gap 4 (QA gap R1): JwtTokenIssuer has no dedicated unit tests. These
 * pin the access-token claims (sub/iss/email/role) and the TTL contract that
 * the resource server and any authenticated endpoint depend on, so a future
 * change to expiry or claims surfaces here before it breaks consumers.
 */
class JwtTokenIssuerTest : FunSpec({

    val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()
    val issuerName = "halal-food-finder"
    val ttlSeconds = 900L
    val issuer = JwtTokenIssuer(
        signingKey = keyPair.private as RSAPrivateKey,
        issuer = issuerName,
        accessTokenTtl = Duration.ofSeconds(ttlSeconds),
    )

    fun signedJwt(account: Account, tokens: app.halal.application.account.SessionTokens): SignedJWT =
        SignedJWT.parse(tokens.accessToken)

    test("issued access token carries the account sub, issuer, email and role claims") {
        val account = Account.new(email = Email("alice@example.com"), passwordHash = "argon2id\$h")
        val tokens = issuer.issue(account)
        val jwt = signedJwt(account, tokens)

        jwt.header.algorithm shouldBe JWSAlgorithm.RS256
        jwt.jwtClaimsSet.subject shouldBe account.id.toString()
        jwt.jwtClaimsSet.issuer shouldBe issuerName
        jwt.jwtClaimsSet.getStringClaim("email") shouldBe "alice@example.com"
        jwt.jwtClaimsSet.getStringClaim("role") shouldBe "USER"
    }

    test("access token TTL matches the configured lifetime in seconds") {
        val account = Account.new(email = Email("bob@example.com"), passwordHash = "argon2id\$h")
        val tokens = issuer.issue(account)

        tokens.accessTokenExpiresInSeconds shouldBe ttlSeconds
        tokens.tokenType shouldBe "Bearer"

        val jwt = signedJwt(account, tokens)
        val iat = jwt.jwtClaimsSet.issueTime.toInstant()
        val exp = jwt.jwtClaimsSet.expirationTime.toInstant()
        Duration.between(iat, exp).seconds shouldBe ttlSeconds
        exp.isAfter(Instant.now()) shouldBe true
    }

    test("refresh token is opaque, random, and differs across issues") {
        val account = Account.new(email = Email("carol@example.com"), passwordHash = "argon2id\$h")
        val first = issuer.issue(account)
        val second = issuer.issue(account)

        first.refreshToken shouldNotBe second.refreshToken
        first.refreshToken shouldBe first.refreshToken
        // A 256-bit random token url-base64-padded encodes to 43 chars.
        first.refreshToken.length shouldBe 43
        // Access tokens carry exp/iat set at issue-time; for the same account and
        // the same issuer/TTL they are deterministic, so no inequality is asserted here.
    }

    test("issued token sub equals the account id used at issuance") {
        val id = UUID.fromString("2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e")
        val account = Account.fromStorage(
            id = id,
            email = Email("dave@example.com"),
            passwordHash = "argon2id\$h",
            role = app.halal.domain.account.Role.RESTAURANT_OWNER,
        )
        val tokens = issuer.issue(account)
        val jwt = signedJwt(account, tokens)

        jwt.jwtClaimsSet.subject shouldBe id.toString()
        jwt.jwtClaimsSet.getStringClaim("role") shouldBe "RESTAURANT_OWNER"
    }
})