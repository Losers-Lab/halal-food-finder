package app.halal.application.account

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * Log Out (sc-132): LogoutSession use case — server-side refresh-token
 * revocation. Revokes the presented refresh token so it can no longer be used
 * to obtain a fresh access token. Deliberately idempotent: revoking an unknown
 * or already-revoked token is a no-op success, so the endpoint never reveals
 * whether a given refresh token was ever valid (no token-enumeration oracle).
 */
class LogoutSessionTest : FunSpec({

    val refreshTokenStore = mockk<RefreshTokenStore>()
    val logout = LogoutSession(refreshTokenStore)

    beforeTest { clearMocks(refreshTokenStore) }

    test("revokes the presented refresh token") {
        every { refreshTokenStore.revoke("refresh-token-to-kill") } returns Unit

        logout.execute("refresh-token-to-kill")

        verify(exactly = 1) { refreshTokenStore.revoke("refresh-token-to-kill") }
    }

    test("is idempotent: revoking an already-revoked/twice-presented token is a no-op success") {
        every { refreshTokenStore.revoke("already-dead") } returns Unit

        logout.execute("already-dead")
        logout.execute("already-dead")

        verify(exactly = 2) { refreshTokenStore.revoke("already-dead") }
    }

    test("is idempotent: revoking an unknown token does not throw (no enumeration oracle)") {
        every { refreshTokenStore.revoke("never-issued") } returns Unit

        logout.execute("never-issued")

        verify(exactly = 1) { refreshTokenStore.revoke("never-issued") }
    }
})