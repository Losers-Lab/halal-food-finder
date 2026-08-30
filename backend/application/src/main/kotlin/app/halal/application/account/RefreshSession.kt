package app.halal.application.account

import java.time.Clock

/**
 * Refresh Session (sc-40) use case — rotating hashed refresh tokens. Validates a
 * presented refresh token (existence + not expired), then atomically consumes
 * and rotates it through the store's single transaction-gated operation (sc-133
 * fix): the old token is deleted and the new one inserted in one store call
 * whose affected-row gate guarantees exact-one-winner under concurrency. Unknown,
 * already-rotated, or expired tokens are rejected with the same generic
 * [InvalidCredentialsException].
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

        // Issue the new pair, then atomically consume the old token and persist
        // the new one in a single store operation. The store's gated delete
        // returns false if a concurrent refresh already consumed this token, in
        // which case nothing is inserted and this caller loses the race.
        val tokens = tokenIssuer.issue(account)
        val rotated = refreshTokenStore.consumeAndRotate(
            presentedToken = refreshToken,
            newToken = tokens.refreshToken,
            accountId = account.id,
            expiresAt = clock.instant().plus(SessionLifetimes.REFRESH_LIFETIME),
        )
        if (!rotated) {
            throw InvalidCredentialsException()
        }
        return AuthSession(account, tokens)
    }
}