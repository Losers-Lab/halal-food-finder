package app.halal.application.account

import java.time.Clock

/**
 * Refresh Session (sc-40) use case — rotating hashed refresh tokens. Validates a
 * presented refresh token (existence + not expired), revokes it (single-use),
 * then issues a fresh access + refresh pair stored for the owning account.
 * Unknown, already-rotated, or expired tokens are rejected with the same
 * generic [InvalidCredentialsException].
 */
class RefreshSession(
    private val tokenIssuer: TokenIssuer,
    private val refreshTokenStore: RefreshTokenStore,
    private val accountRepository: AccountRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun execute(refreshToken: String): AuthSession {
        val stored = refreshTokenStore.findByToken(refreshToken)
            ?: throw InvalidCredentialsException()

        // Expired (or expiring now): revoke so it cannot replay, then reject.
        if (!stored.expiresAt.isAfter(clock.instant())) {
            refreshTokenStore.revoke(refreshToken)
            throw InvalidCredentialsException()
        }

        // Load the current account so the re-issued access JWT carries the
        // account's current RBAC role.
        val account = accountRepository.findById(stored.accountId)
            ?: throw InvalidCredentialsException()

        // Rotate: the old refresh token is single-use, so revoke it first.
        refreshTokenStore.revoke(refreshToken)

        val tokens = tokenIssuer.issue(account)
        refreshTokenStore.store(
            token = tokens.refreshToken,
            accountId = account.id,
            expiresAt = clock.instant().plus(SessionLifetimes.REFRESH_LIFETIME),
        )
        return AuthSession(account, tokens)
    }
}