package com.tahirslist.persistence.verification

import com.tahirslist.application.verification.HalalCertificationReviewRepository
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.VerificationState
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * JDBC implementation of the [HalalCertificationReviewRepository] port against the
 * `halal_certification_reviews` table (V13__create_halal_certification_reviews.sql).
 * Spring JDBC only — deliberately boring and explicit.
 *
 * sc-46 persists the review that results from driving the claim through the sc-117
 * state machine ([save]); reading reviews back for the Verification Committee is
 * sc-73 and adds its finders here. The aggregate owns its id and timestamps, and
 * the whole aggregate (state, AI suggestion, human decision if any) is written in
 * a single row so the VC reads a consistent snapshot.
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
                ai_consent_at,
                created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            java.sql.Timestamp.from(review.createdAt),
            java.sql.Timestamp.from(review.updatedAt),
        )
        return review
    }
}