package com.tahirslist.persistence.verification

import com.tahirslist.application.verification.HalalCertificationReviewRepository
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.ReviewDecision
import com.tahirslist.domain.verification.SuggestionVerdict
import com.tahirslist.domain.verification.VerificationOutcome
import com.tahirslist.domain.verification.VerificationSuggestion
import com.tahirslist.domain.verification.VerificationState
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/**
 * JDBC implementation of the [HalalCertificationReviewRepository] port against the
 * `halal_certification_reviews` table (V13__create_halal_certification_reviews.sql).
 * Spring JDBC only — deliberately boring and explicit.
 *
 * [save] is an **upsert** (`INSERT ... ON CONFLICT (id) DO UPDATE`): sc-46 writes
 * a fresh row from the claim (state, AI suggestion, consent), and sc-73 advances
 * the same row through a state transition (state + human decision), so a single
 * `save` serves both paths. The whole aggregate (state, AI suggestion, human
 * decision if any, consent) lives in one row, so every read yields a consistent
 * snapshot.
 *
 * [findById] / [findByState] back the sc-73 VC decision loop and workqueue; the
 * row is mapped back into the full aggregate so a decision can be applied to it.
 */
@Repository
class JdbcHalalCertificationReviewRepository(
    private val jdbc: JdbcTemplate,
) : HalalCertificationReviewRepository {

    override fun save(review: HalalCertificationReview): HalalCertificationReview {
        jdbc.update(
            """
            INSERT INTO halal_certification_reviews (
                id, listing_id, submitted_by, state,
                suggestion_verdict, suggestion_confidence, suggestion_reasoning,
                decision_outcome, decision_by, decision_reason, decision_at,
                ai_consent_at, certifier, expires_on,
                created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                state = EXCLUDED.state,
                suggestion_verdict = EXCLUDED.suggestion_verdict,
                suggestion_confidence = EXCLUDED.suggestion_confidence,
                suggestion_reasoning = EXCLUDED.suggestion_reasoning,
                decision_outcome = EXCLUDED.decision_outcome,
                decision_by = EXCLUDED.decision_by,
                decision_reason = EXCLUDED.decision_reason,
                decision_at = EXCLUDED.decision_at,
                ai_consent_at = EXCLUDED.ai_consent_at,
                certifier = EXCLUDED.certifier,
                expires_on = EXCLUDED.expires_on,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
            review.id,
            review.listingId,
            review.submittedBy,
            review.state.name,
            review.suggestion?.verdict?.name,
            review.suggestion?.confidence,
            review.suggestion?.reasoning,
            review.decision?.outcome?.name,
            review.decision?.decidedBy,
            review.decision?.reason,
            review.decision?.decidedAt?.let { java.sql.Timestamp.from(it) },
            review.aiConsentGivenAt?.let { java.sql.Timestamp.from(it) },
            review.certifier,
            review.expiresOn?.let { java.sql.Date.valueOf(it) },
            java.sql.Timestamp.from(review.createdAt),
            java.sql.Timestamp.from(review.updatedAt),
        )
        return review
    }

    override fun findById(id: UUID): HalalCertificationReview? =
        jdbc.query(SELECT_SQL + " WHERE r.id = ?", { rs, _ -> mapReview(rs) }, id).firstOrNull()

    override fun findByState(state: VerificationState): List<HalalCertificationReview> =
        jdbc.query(SELECT_SQL + " WHERE r.state = ?", { rs, _ -> mapReview(rs) }, state.name)

    override fun findLatestApprovedByListing(listingId: UUID): HalalCertificationReview? =
        jdbc.query(
            SELECT_SQL + " WHERE r.listing_id = ? AND r.state = 'APPROVED' ORDER BY r.decision_at DESC NULLS LAST, r.updated_at DESC LIMIT 1",
            { rs, _ -> mapReview(rs) },
            listingId,
        ).firstOrNull()

    private fun mapReview(rs: ResultSet): HalalCertificationReview = HalalCertificationReview(
        id = rs.getObject("id", UUID::class.java),
        listingId = rs.getObject("listing_id", UUID::class.java),
        submittedBy = rs.getObject("submitted_by", UUID::class.java),
        state = VerificationState.valueOf(rs.getString("state")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
        suggestion = rs.getString("suggestion_verdict")?.let {
            VerificationSuggestion(
                verdict = SuggestionVerdict.valueOf(it),
                confidence = rs.getDouble("suggestion_confidence"),
                reasoning = rs.getString("suggestion_reasoning"),
            )
        },
        decision = rs.getString("decision_outcome")?.let {
            ReviewDecision(
                outcome = VerificationOutcome.valueOf(it),
                decidedBy = rs.getObject("decision_by", UUID::class.java),
                reason = rs.getString("decision_reason"),
                decidedAt = rs.getTimestamp("decision_at").toInstant(),
            )
        },
        aiConsentGivenAt = rs.getTimestamp("ai_consent_at")?.toInstant(),
        certifier = rs.getString("certifier"),
        expiresOn = rs.getDate("expires_on")?.toLocalDate(),
    )

    private companion object {
        const val SELECT_SQL: String =
            """
            SELECT
                id, listing_id, submitted_by, state,
                suggestion_verdict, suggestion_confidence, suggestion_reasoning,
                decision_outcome, decision_by, decision_reason, decision_at,
                ai_consent_at, certifier, expires_on, created_at, updated_at
            FROM halal_certification_reviews r
            """
    }
}