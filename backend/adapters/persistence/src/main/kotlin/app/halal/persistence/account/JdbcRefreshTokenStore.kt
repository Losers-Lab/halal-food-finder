package app.halal.persistence.account

import app.halal.application.account.RefreshTokenStore
import app.halal.application.account.StoredRefreshToken
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * JDBC implementation of the [RefreshTokenStore] port against the
 * `refresh_tokens` table (see V2__create_refresh_tokens.sql + V3: family ids and
 * a soft-consumed marker for reuse detection). Only the SHA-256 hash of each
 * token is persisted — the plaintext token is never stored, so a database leak
 * of this table cannot be replayed.
 *
 * Lifecycle (sc-136): a live token has `consumed_at IS NULL`. Rotation soft-
 * consumes the old token (sets `consumed_at`) and inserts the new one in the
 * SAME `family_id`, so a *present-but-consumed* token remains discoverable — the
 * application treats its reappearance as reuse and revokes the whole family.
 */
@Repository
class JdbcRefreshTokenStore(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
) : RefreshTokenStore {

    override fun store(token: String, accountId: UUID, familyId: UUID, expiresAt: Instant) {
        jdbc.update(
            """
            INSERT INTO refresh_tokens (token_hash, account_id, family_id, expires_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (token_hash) DO UPDATE
                SET account_id = EXCLUDED.account_id,
                    family_id = EXCLUDED.family_id,
                    expires_at = EXCLUDED.expires_at
            """.trimIndent(),
            sha256(token),
            accountId,
            familyId,
            Timestamp.from(expiresAt),
        )
    }

    override fun findByToken(token: String): StoredRefreshToken? {
        val rows = jdbc.query(
            "SELECT account_id, family_id, expires_at FROM refresh_tokens WHERE token_hash = ? AND consumed_at IS NULL",
            { rs, _ ->
                StoredRefreshToken(
                    accountId = rs.getObject("account_id", UUID::class.java),
                    familyId = rs.getObject("family_id", UUID::class.java),
                    expiresAt = rs.getTimestamp("expires_at").toInstant(),
                )
            },
            sha256(token),
        )
        return rows.firstOrNull()
    }

    override fun findConsumedByToken(token: String): StoredRefreshToken? {
        val rows = jdbc.query(
            "SELECT account_id, family_id, expires_at FROM refresh_tokens WHERE token_hash = ? AND consumed_at IS NOT NULL",
            { rs, _ ->
                StoredRefreshToken(
                    accountId = rs.getObject("account_id", UUID::class.java),
                    familyId = rs.getObject("family_id", UUID::class.java),
                    expiresAt = rs.getTimestamp("expires_at").toInstant(),
                )
            },
            sha256(token),
        )
        return rows.firstOrNull()
    }

    override fun revoke(token: String) {
        jdbc.update("DELETE FROM refresh_tokens WHERE token_hash = ?", sha256(token))
    }

    override fun revokeFamily(familyId: UUID) {
        jdbc.update("DELETE FROM refresh_tokens WHERE family_id = ?", familyId)
    }

    override fun consumeAndRotate(
        presentedToken: String,
        newToken: String,
        accountId: UUID,
        familyId: UUID,
        expiresAt: Instant,
    ): Boolean = tx.execute {
        // The conditional soft-consume is the atomic claim on the token. Its
        // affected-row count is the gate: exactly one concurrent caller can flip
        // a live row to consumed (Postgres locks the row until this tx commits,
        // so a concurrent consume blocks, then re-evaluates and sees 0 rows after
        // the winner commits). If we did NOT own the row (0 updated), we must not
        // insert a replacement — that is what stops a second live token. On a
        // successful consume the INSERT happens in the same transaction, so the
        // rotate is atomic (an INSERT failure rolls the consume back too).
        val consumed = jdbc.update(
            "UPDATE refresh_tokens SET consumed_at = now() WHERE token_hash = ? AND consumed_at IS NULL",
            sha256(presentedToken),
        )
        if (consumed != 1) {
            // Losing branch: no row was written, so a normal (empty) commit is
            // harmless and avoids relying on rollback-only commit semantics.
            false
        } else {
            jdbc.update(
                "INSERT INTO refresh_tokens (token_hash, account_id, family_id, expires_at) VALUES (?, ?, ?, ?)",
                sha256(newToken),
                accountId,
                familyId,
                Timestamp.from(expiresAt),
            )
            true
        }
    } ?: false

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}