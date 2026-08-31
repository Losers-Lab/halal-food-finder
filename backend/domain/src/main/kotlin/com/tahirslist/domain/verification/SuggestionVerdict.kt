package com.tahirslist.domain.verification

/**
 * The AI's **suggested** disposition of a certification image.
 *
 * These are suggestions to a human, never a final status: a suggestion only
 * moves the review to [VerificationState.AI_SUGGESTED], and a human must
 * confirm before the listing becomes VERIFIED (founder mandate:
 * "AI-suggests -> VC-approves, never ship AI alone").
 *
 *  - [APPROVE] / [DENY] carry a high-confidence suggestion the human should
 *    spot-check; the human independently makes the final APPROVED/DENIED call.
 *  - [NEEDS_REVIEW] means "cannot be trusted at high confidence" — the
 *    conservative default that keeps a possibly-halal cert from being wrongly
 *    stamped VERIFIED.
 */
enum class SuggestionVerdict {
    APPROVE,
    DENY,
    NEEDS_REVIEW,
    ;
}