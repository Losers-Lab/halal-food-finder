package app.halal.application.account

import java.time.Instant
import java.util.UUID

/**
 * Persistence port for refresh tokens (hexagonal "out" port), implemented by
 * the persistence adapter. Refresh tokens are opaque random strings handled by
 * this contract in raw form; the adapter is responsible for storing only a
 * one-way SHA-256 hash and for looking tokens up by that hash — the plaintext
 * token must never be persisted.
 *
 * Tokens are single-use and rotated on refresh (sc-133 atomic grant). Each token
 * belongs to a **family** (sc-136 reuse detection): every login mints a token in
 * a fresh family, and each rotation keeps the family so the lineage of one
 * session is traceable. A token that is *present-but-consumed* (already rotated)
 * is a reuse/theft signal — the use case then revokes the ENTIRE family.
 */
interface RefreshTokenStore {

    /** Persist a hashed refresh token in [familyId], expiring at [expiresAt]. */
    fun store(token: String, accountId: UUID, familyId: UUID, expiresAt: Instant)

    /** Resolve a live (not-yet-consumed) refresh token, if any. */
    fun findByToken(token: String): StoredRefreshToken?

    /** Resolve a refresh token that exists but is already consumed (reuse signal). */
    fun findConsumedByToken(token: String): StoredRefreshToken?

    /** Irrevocably invalidate a single refresh token (logout / expiry). */
    fun revoke(token: String)

    /** Irrevocably invalidate EVERY token (live or already consumed) in the family. */
    fun revokeFamily(familyId: UUID)

    /**
     * Atomically consume-and-rotate a refresh token in a single transactional
     * operation: mark [presentedToken] as consumed — and only if that update
     * claimed exactly one live row (i.e. the token was still valid and this
     * caller owns it) — persist [newToken] in the same [familyId] as its
     * replacement. Returns `false` (and inserts nothing) when the presented
     * token was already consumed or unknown, so a concurrent refresh of the same
     * token cannot mint a second live record. The affected-row count of the
     * conditional update is what makes the rotation race-safe: exact-one-winner
     * under concurrency (sc-133 baseline preserved).
     */
    fun consumeAndRotate(
        presentedToken: String,
        newToken: String,
        accountId: UUID,
        familyId: UUID,
        expiresAt: Instant,
    ): Boolean
}

/** A refresh-token record as read back from storage (live or consumed). */
data class StoredRefreshToken(
    val accountId: UUID,
    val familyId: UUID,
    val expiresAt: Instant,
)