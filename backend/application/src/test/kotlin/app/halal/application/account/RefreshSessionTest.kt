package app.halal.application.account

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Log In (sc-40): RefreshSession use case — rotating hashed refresh tokens. A
 * fresh access token + new refresh token are issued; the previous refresh token
 * is revoked (single-use). Unknown/expired tokens are rejected generically.
 */
class RefreshSessionTest : FunSpec({

    val tokenIssuer = mockk<TokenIssuer>()
    val refreshTokenStore = mockk<RefreshTokenStore>()
    val accountRepository = mockk<AccountRepository>()
    val clock = Clock.systemUTC()

    val refresh = RefreshSession(
        tokenIssuer = tokenIssuer,
        refreshTokenStore = refreshTokenStore,
        accountRepository = accountRepository,
        clock = clock,
    )

    beforeTest { clearMocks(tokenIssuer, refreshTokenStore, accountRepository) }

    test("rotates: revokes the old refresh token, issues a new pair, stores the new refresh") {
        val account = AccountFixture.someAccount(email = "alice@example.com")
        val stored = StoredRefreshToken(account.id, clock.instant().plus(Duration.ofDays(29)))
        every { refreshTokenStore.findByToken("old-refresh-token") } returns stored
        every { accountRepository.findById(account.id) } returns account
        every { tokenIssuer.issue(account) } returns SessionTokens("new.access", "new-refresh-token", "Bearer", 900)
        every { refreshTokenStore.revoke("old-refresh-token") } returns Unit
        every { refreshTokenStore.store("new-refresh-token", account.id, any()) } returns Unit

        val session = refresh.execute("old-refresh-token")

        session.tokens.accessToken shouldBe "new.access"
        session.tokens.refreshToken shouldBe "new-refresh-token"
        session.account.id shouldBe account.id
        verify { refreshTokenStore.revoke("old-refresh-token") }
        verify { refreshTokenStore.store("new-refresh-token", account.id, any()) }
    }

    test("rejects an unknown/revoked refresh token without issuing a new session") {
        every { refreshTokenStore.findByToken("garbage") } returns null

        shouldThrow<InvalidCredentialsException> { refresh.execute("garbage") }

        verify(exactly = 0) { tokenIssuer.issue(any()) }
        verify(exactly = 0) { refreshTokenStore.store(any(), any(), any()) }
    }

    test("rejects an expired refresh token and revokes it so it cannot be replayed") {
        val account = AccountFixture.someAccount(email = "alice@example.com")
        val expired = StoredRefreshToken(account.id, clock.instant().minusSeconds(1))
        every { refreshTokenStore.findByToken("expired-token") } returns expired
        every { refreshTokenStore.revoke("expired-token") } returns Unit

        shouldThrow<InvalidCredentialsException> { refresh.execute("expired-token") }

        verify { refreshTokenStore.revoke("expired-token") }
        verify(exactly = 0) { tokenIssuer.issue(any()) }
        verify(exactly = 0) { refreshTokenStore.store(any(), any(), any()) }
    }

    test("re-issues from the account fetched by id (so current RBAC role is embedded)") {
        val account = AccountFixture.someAccount(email = "alice@example.com")
        val stored = StoredRefreshToken(account.id, clock.instant().plus(Duration.ofDays(29)))
        every { refreshTokenStore.findByToken("tok") } returns stored
        every { accountRepository.findById(account.id) } returns account
        every { tokenIssuer.issue(account) } returns SessionTokens("a", "r", "Bearer", 900)
        every { refreshTokenStore.revoke("tok") } returns Unit
        every { refreshTokenStore.store("r", account.id, any()) } returns Unit

        refresh.execute("tok")

        verify { accountRepository.findById(account.id) }
        verify { tokenIssuer.issue(account) }
    }

    test("rejects the token when the owning account no longer exists") {
        val orphaned = StoredRefreshToken(java.util.UUID.randomUUID(), clock.instant().plus(Duration.ofDays(29)))
        every { refreshTokenStore.findByToken("orphan") } returns orphaned
        every { accountRepository.findById(orphaned.accountId) } returns null

        shouldThrow<InvalidCredentialsException> { refresh.execute("orphan") }

        verify(exactly = 0) { tokenIssuer.issue(any()) }
    }
})