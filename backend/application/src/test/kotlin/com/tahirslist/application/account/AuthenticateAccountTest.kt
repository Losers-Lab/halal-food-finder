package com.tahirslist.application.account

import com.tahirslist.domain.account.Email
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Log In (sc-40): AuthenticateAccount use case. Verifies credentials against the
 * stored Argon2id hash, then issues a short access + rotating refresh session.
 * Each login mints the session's refresh token in a FRESH token family (sc-136),
 * so separate log-ins are never linkage-joined by a shared family id.
 */
class AuthenticateAccountTest : FunSpec({

    val repository = mockk<AccountRepository>()
    val hasher = mockk<PasswordHasher>()
    val tokenIssuer = mockk<TokenIssuer>()
    val refreshTokenStore = mockk<RefreshTokenStore>()
    val clock = Clock.systemUTC()

    val authenticate = AuthenticateAccount(
        repository = repository,
        hasher = hasher,
        tokenIssuer = tokenIssuer,
        refreshTokenStore = refreshTokenStore,
        clock = clock,
    )

    beforeTest { clearMocks(repository, hasher, tokenIssuer, refreshTokenStore) }

    test("returns access + refresh tokens for valid credentials and stores the refresh token in a fresh family") {
        val account = AccountFixture.someAccount(email = "alice@example.com")
        every { repository.findByEmail(Email("alice@example.com")) } returns account
        every { hasher.verify("s3cr3t-password", account.passwordHash) } returns true
        every { tokenIssuer.issue(account) } returns SessionTokens("access.jwt", "refresh-token-abc", "Bearer", 900)

        val storedExpiry = slot<Instant>()
        val storedFamily = slot<java.util.UUID>()
        every { refreshTokenStore.store("refresh-token-abc", account.id, capture(storedFamily), capture(storedExpiry)) } returns Unit

        val session = authenticate.execute("alice@example.com", "s3cr3t-password")

        session.account.id shouldBe account.id
        session.tokens.accessToken shouldBe "access.jwt"
        session.tokens.refreshToken shouldBe "refresh-token-abc"
        session.tokens.tokenType shouldBe "Bearer"
        session.tokens.accessTokenExpiresInSeconds shouldBe 900
        // A login must mint a family id (each login = a distinct token family).
        storedFamily.captured.toString().isNotBlank() shouldBe true
        verify { refreshTokenStore.store("refresh-token-abc", account.id, storedFamily.captured, any()) }
        // Refresh token persisted with a ~30-day lifetime, not in the past.
        storedExpiry.captured.isAfter(clock.instant().plus(29, ChronoUnit.DAYS)) shouldBe true
        storedExpiry.captured.isBefore(clock.instant().plus(31, ChronoUnit.DAYS)) shouldBe true
    }

    test("each login mints a distinct fresh family (no reuse of a previous family id)") {
        val account = AccountFixture.someAccount(email = "alice@example.com")
        every { repository.findByEmail(Email("alice@example.com")) } returns account
        every { hasher.verify("s3cr3t-password", account.passwordHash) } returns true
        every { tokenIssuer.issue(account) } returns SessionTokens("access.jwt", "refresh-token-abc", "Bearer", 900)

        val familySlot = mutableListOf<java.util.UUID>()
        every { refreshTokenStore.store("refresh-token-abc", account.id, capture(familySlot), any()) } returns Unit

        authenticate.execute("alice@example.com", "s3cr3t-password")
        authenticate.execute("alice@example.com", "s3cr3t-password")

        // Two log-ins must never share a family id.
        familySlot[0] shouldBe familySlot[0]
        (familySlot[0] != familySlot[1]) shouldBe true
    }

    test("rejects an unknown email with a generic InvalidCredentialsException and issues nothing") {
        every { repository.findByEmail(Email("nobody@example.com")) } returns null

        val ex = shouldThrow<InvalidCredentialsException> { authenticate.execute("nobody@example.com", "anything") }

        ex.message shouldBe "Invalid email or password."
        verify(exactly = 0) { hasher.verify(any(), any()) }
        verify(exactly = 0) { tokenIssuer.issue(any()) }
        verify(exactly = 0) { refreshTokenStore.store(any(), any(), any(), any()) }
    }

    test("rejects a wrong password with the same generic error and issues nothing") {
        val account = AccountFixture.someAccount(email = "alice@example.com")
        every { repository.findByEmail(Email("alice@example.com")) } returns account
        every { hasher.verify("wrong-password", account.passwordHash) } returns false

        val ex = shouldThrow<InvalidCredentialsException> { authenticate.execute("alice@example.com", "wrong-password") }

        // Same message/shape as the unknown-email path — no user enumeration.
        ex.message shouldBe "Invalid email or password."
        verify(exactly = 0) { tokenIssuer.issue(any()) }
        verify(exactly = 0) { refreshTokenStore.store(any(), any(), any(), any()) }
    }

    test("normalises the email to canonical lowercase before the lookup") {
        val account = AccountFixture.someAccount(email = "a.user@example.com")
        val lookedUp = slot<Email>()
        every { repository.findByEmail(capture(lookedUp)) } returns account
        every { hasher.verify("s3cr3t-password", account.passwordHash) } returns true
        every { tokenIssuer.issue(account) } returns SessionTokens("access.jwt", "refresh", "Bearer", 900)
        every { refreshTokenStore.store(any(), any(), any(), any()) } returns Unit

        authenticate.execute("  A.User@Example.COM ", "s3cr3t-password")

        lookedUp.captured.value shouldBe "a.user@example.com"
    }
})