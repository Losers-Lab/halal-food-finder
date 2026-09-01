package com.tahirslist.application.verification

import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.VerificationState
import java.time.Instant
import java.util.UUID

/**
 * sc-73: the human Verification Committee decision loop.
 *
 * A committee member works the pending queue ([listPending]), then either
 * approves ([approve] → the listing is promoted to VERIFIED) or denies
 * ([deny] → the denial is recorded including a reason; the listing stays
 * unverified).
 *
 * Both decisions are driven through the sc-117 aggregate state machine
 * (AI_SUGGESTED → HUMAN_REVIEW → APPROVED/DENIED), so the same guardrails apply:
 * a review that is not pending (already decided, or never reached the human
 * stage) cannot be decided again ([ReviewNotPendingException]).
 *
 * By design the AI can only ever *suggest* (the claim never auto-promotes); a
 * VERIFIED listing is reached ONLY through this human approve path.
 */
class VerificationCommittee(
    private val reviews: HalalCertificationReviewRepository,
    private val listings: RestaurantListingRepository,
) {

    /** The VC workqueue: every review awaiting a human decision. */
    fun listPending(): List<HalalCertificationReview> =
        reviews.findByState(VerificationState.AI_SUGGESTED)

    /**
     * Approve a pending review. The review advances to APPROVED and the listing
     * is promoted to VERIFIED (source table + search mirror kept in sync by the
     * adapter).
     *
     * @throws ReviewNotFoundException if no review has [reviewId]
     *         (and, after a successful decision, if the listing it references has
     *         been removed — the approve must never persist an APPROVED review to
     *         a now-missing listing)
     * @throws ReviewNotPendingException if the review is not in a decidable state
     */
    fun approve(
        reviewId: UUID,
        decidedBy: UUID,
        reason: String? = null,
        now: Instant = Instant.now(),
    ): HalalCertificationReview {
        val decided = decide(reviewId, decidedBy, reason, now) {
            it.approve(decidedBy, reason, now)
        }
        // Approve is the ONLY path to a VERIFIED listing. The listing must still
        // exist; if it vanished since the review was created, fail loudly rather
        // than leave an orphaned APPROVED review pointing at nothing.
        if (listings.updateVerificationStatus(decided.listingId, VerificationStatus.VERIFIED) == null) {
            throw ReviewNotFoundException(reviewId)
        }
        return decided
    }

    /**
     * Deny a pending review. The review advances to DENIED with [reason] recorded;
     * the listing stays UNVERIFIED (no promotion).
     *
     * @throws ReviewNotFoundException if no review has [reviewId]
     * @throws ReviewNotPendingException if the review is not in a decidable state
     */
    fun deny(
        reviewId: UUID,
        decidedBy: UUID,
        reason: String,
        now: Instant = Instant.now(),
    ): HalalCertificationReview {
        require(reason.trim().isNotBlank()) { "A denial must record a reason." }
        return decide(reviewId, decidedBy, reason, now) {
            it.deny(decidedBy, reason, now)
        }
    }

    /** Load the review, walk HUMAN_REVIEW, apply [finalize], and persist it. */
    private fun decide(
        reviewId: UUID,
        decidedBy: UUID,
        reason: String?,
        now: Instant,
        finalize: (HalalCertificationReview) -> HalalCertificationReview,
    ): HalalCertificationReview {
        val review = reviews.findById(reviewId) ?: throw ReviewNotFoundException(reviewId)
        if (review.state != VerificationState.AI_SUGGESTED) {
            throw ReviewNotPendingException(reviewId, review.state)
        }
        val decided = finalize(review.beginHumanReview(now))
        return reviews.save(decided)
    }
}