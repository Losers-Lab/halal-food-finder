package app.halal.application.account

import java.time.Instant
import java.util.UUID

/**
 * Persistence port for refresh tokens (hexagonal "out" port), implemented by
 * the persistence adapter. Refresh tokens are opaque random strings handled by
 * this contract in raw form; the adapter is responsible for storing only a
 * one-way SHA-256 hash and for looking tokens up by that hash — the plaintext
 * token must never be persisted. Records are single-use and rotated on refresh.
 */
interface RefreshTokenStore {

    /** Persist a hashed refresh token, bound to the owning account, expiring at [expiresAt]. */
    fun store(token: String, accountId: UUID, expiresAt: Instant)

    /** Resolve a previously-issued (non-revoked) refresh token, if any. */
    fun findByToken(token: String): StoredRefreshToken?

    /** Irrevocably invalidate a refresh token (rotation / expiry). */
    fun revoke(token: String)

    /**
     * Atomically consume-and-rotate a refresh token in a single transactional
     * operation: delete [presentedToken] and — only if exactly one row was
     * actually deleted (i.e. the token was still live and this caller owns it) —
     * persist [newToken] as its replacement. Returns `false` (and inserts
     * nothing) when the presented token was already consumed or unknown, so a
     * concurrent refresh presenting the same token cannot mint a second live
     * record. The affected-row count of the conditional delete is what makes the
     * rotation race-safe: exact-one-winner under concurrency.
     */
    fun consumeAndRotate(
        presentedToken: String,
        newToken: String,
        accountId: UUID,
        expiresAt: Instant,
    ): Boolean
}

/** A live refresh-token record as read back from storage. */
data class StoredRefreshToken(val accountId: UUID, val expiresAt: Instant)