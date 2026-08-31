package com.tahirslist.domain.verification

/**
 * The human (Verification Committee) outcome recorded when a review is
 * finalized via [HalalCertificationReview.approve] / [HalalCertificationReview.deny].
 *
 * Distinct from [SuggestionVerdict]: the suggestion is the AI's *recommendation*;
 * the outcome is the human's binding decision that moves the review to
 * APPROVED / DENIED.
 */
enum class VerificationOutcome {
    APPROVED,
    DENIED,
    REVERSED,
    ;
}