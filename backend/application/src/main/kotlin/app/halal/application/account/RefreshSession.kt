package app.halal.application.account

import java.time.Clock

/**
 * Refresh Session (sc-40) use case — rotating hashed refresh tokens with
 * token-family reuse detection (sc-136). A presented refresh token is validated
 * (existence + not expired), then atomically consumed and rotated within its
 * family through the store's single transaction-gated operation (sc-133 fix):
 * the old token is marked consumed and the new one inserted in one store call
 * whose affected-row gate guarantees exact-one-winner under concurrency.
 *
 * sc-136 reuse detection: if the presented token is not live but a *consumed*
 * record still exists for it, that token was already rotated — presenting it
 * again means it was stolen/replayed, so the ENTIRE family is revoked before the
 * request is rejected. Unknown (never-issued) and expired tokens are rejected
 * with the same generic [InvalidCredentialsException] and never revoke a family.
 */
class RefreshSession(
    private val tokenIssuer: TokenIssuer,
    private val refreshTokenStore: RefreshTokenStore,
    private val accountRepository: AccountRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun execute(refreshToken: String): AuthSession {
        val stored = refreshTokenStore.findByToken(refreshToken)
        if (stored == null) {
            // Not live. Distinguish "never issued" from "present-but-consumed"
            // (a reused/stolen token): the latter is a theft signal.
            val consumed = refreshTokenStore.findConsumedByToken(refreshToken)
            if (consumed != null) {
                // Reuse → revoke the WHOLE family, not just this one token.
                refreshTokenStore.revokeFamily(consumed.familyId)
            }
            throw InvalidCredentialsException()
        }

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
        // the new one in a single store operation, keeping the family intact.
        // The store's gated consume returns false if a concurrent refresh already
        // consumed this token, in which case nothing is inserted and this caller
        // loses the race.
        val tokens = tokenIssuer.issue(account)
        val rotated = refreshTokenStore.consumeAndRotate(
            presentedToken = refreshToken,
            newToken = tokens.refreshToken,
            accountId = account.id,
            familyId = stored.familyId,
            expiresAt = clock.instant().plus(SessionLifetimes.REFRESH_LIFETIME),
        )
        if (!rotated) {
            throw InvalidCredentialsException()
        }
        return AuthSession(account, tokens)
    }
}