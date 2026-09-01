package com.tahirslist.domain.verification

import java.time.Instant
import java.util.UUID

/**
 * The certification-review aggregate. Owns the verification state machine:
 *
 *   SUBMITTED -> AI_REVIEW -> AI_SUGGESTED -> HUMAN_REVIEW -> { APPROVED | DENIED }
 *                                                             |-> REVERSED (from
 *                                                                 APPROVED/DENIED)
 *
 * Every transition is an immutable copy; a transition the machine does not allow
 * throws [IllegalStateException]. This makes out-of-order drivers fail loudly
 * instead of silently corrupting the review.
 *
 * Ownership: [submittedBy] is the account that submitted the certification.
 * [suggestion] is the AI's conservative suggestion (populated at AI_SUGGESTED);
 * [decision] is the human's final (or reversal) record. [aiConsentGivenAt]
 * records when the submitter explicitly consented to the certification image
 * being analysed by the hosted AI (sc-120) — it may be null for reviews that
 * predate consent (no consent was ever given).
 *
 * NOTE: sc-117 ships the forward path (SUBMITTED -> ... -> AI_SUGGESTED), the
 * human actions (HUMAN_REVIEW -> APPROVED/DENIED) and reversal (APPROVED/DENIED
 * -> REVERSED) are state-machine transitions implemented here, but their
 * commands/endpoints land in sc-46/sc-73.
 */
data class HalalCertificationReview(
    val id: UUID,
    val listingId: UUID,
    val submittedBy: UUID,
    val state: VerificationState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val suggestion: VerificationSuggestion? = null,
    val decision: ReviewDecision? = null,
    val aiConsentGivenAt: Instant? = null,
) {
    companion object {

        /** Start a review in [VerificationState.SUBMITTED] for [listingId]. */
        fun create(
            listingId: UUID,
            submittedBy: UUID,
            now: Instant = Instant.now(),
            aiConsentGivenAt: Instant? = null,
        ): HalalCertificationReview = HalalCertificationReview(
            id = UUID.randomUUID(),
            listingId = listingId,
            submittedBy = submittedBy,
            state = VerificationState.SUBMITTED,
            createdAt = now,
            updatedAt = now,
            aiConsentGivenAt = aiConsentGivenAt,
        )
    }

    /** SUBMITTED -> AI_REVIEW (the hosted model now reviews the certification). */
    fun beginAiReview(now: Instant = Instant.now()): HalalCertificationReview =
        transition(setOf(VerificationState.SUBMITTED), "begin AI review") {
            copy(state = VerificationState.AI_REVIEW, updatedAt = now)
        }

    /** AI_REVIEW -> AI_SUGGESTED (records the AI's conservative [suggestion]). */
    fun recordAiSuggestion(
        suggestion: VerificationSuggestion,
        now: Instant = Instant.now(),
    ): HalalCertificationReview =
        transition(setOf(VerificationState.AI_REVIEW), "record an AI suggestion") {
            copy(
                state = VerificationState.AI_SUGGESTED,
                suggestion = suggestion,
                updatedAt = now,
            )
        }

    /** AI_SUGGESTED -> HUMAN_REVIEW (a committee member takes the review up). */
    fun beginHumanReview(now: Instant = Instant.now()): HalalCertificationReview =
        transition(setOf(VerificationState.AI_SUGGESTED), "begin human review") {
            copy(state = VerificationState.HUMAN_REVIEW, updatedAt = now)
        }

    /** HUMAN_REVIEW -> APPROVED (the listing may become VERIFIED — sc-46/73). */
    fun approve(
        decidedBy: UUID,
        reason: String? = null,
        now: Instant = Instant.now(),
    ): HalalCertificationReview =
        transition(setOf(VerificationState.HUMAN_REVIEW), "approve") {
            copy(
                state = VerificationState.APPROVED,
                decision = ReviewDecision(VerificationOutcome.APPROVED, decidedBy, reason, now),
                updatedAt = now,
            )
        }

    /** HUMAN_REVIEW -> DENIED (verification refused). */
    fun deny(
        decidedBy: UUID,
        reason: String? = null,
        now: Instant = Instant.now(),
    ): HalalCertificationReview =
        transition(setOf(VerificationState.HUMAN_REVIEW), "deny") {
            copy(
                state = VerificationState.DENIED,
                decision = ReviewDecision(VerificationOutcome.DENIED, decidedBy, reason, now),
                updatedAt = now,
            )
        }

    /**
     * APPROVED|DENIED -> REVERSED. Rolls back a finalized disposition — a grant
     * reversed on cert expiry/revocation, or a wrongful denial overturned.
     * REVERSED is terminal: reversing sends the *listing* back to an unverified
     * posture (a separate, caller concern); it never reopens this review.
     */
    fun reverse(
        decidedBy: UUID,
        reason: String? = null,
        now: Instant = Instant.now(),
    ): HalalCertificationReview =
        transition(setOf(VerificationState.APPROVED, VerificationState.DENIED), "reverse") {
            copy(
                state = VerificationState.REVERSED,
                decision = ReviewDecision(VerificationOutcome.REVERSED, decidedBy, reason, now),
                updatedAt = now,
            )
        }

    /** Guard: only the documented source state(s) may take [action]. */
    private fun transition(
        allowed: Set<VerificationState>,
        action: String,
        apply: HalalCertificationReview.() -> HalalCertificationReview,
    ): HalalCertificationReview {
        if (state !in allowed) {
            throw IllegalStateException("Cannot $action a review in state $state.")
        }
        return apply()
    }
}