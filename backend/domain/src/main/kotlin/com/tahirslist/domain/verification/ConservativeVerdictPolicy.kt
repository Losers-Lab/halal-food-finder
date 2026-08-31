package com.tahirslist.domain.verification

/**
 * The conservative-verdict policy every verification provider is held to
 * (ratified hosted-AI trust rules — ARCHITECTURE.md §1.0 "Verification
 * (hosted AI)"). It maps a raw model [ModelJudgment] to a [VerificationSuggestion].
 *
 * Trust posture (amanah): a wrongly-granted VERIFIED badge is a product-wide
 * trust failure, so the bar to *suggest* APPROVE is deliberately high. Only a
 * high-confidence, explicitly-valid model judgment may be suggested APPROVE;
 * anything uncertain, low-confidence, or unclassifiable is deferred to a human
 * as [SuggestionVerdict.NEEDS_REVIEW].
 *  - [INCONCLUSIVE] -> [SuggestionVerdict.NEEDS_REVIEW] regardless of confidence
 *    ("I cannot tell" is never treated as "therefore it is halal").
 *  - confidence below [HIGH_CONFIDENCE] -> [SuggestionVerdict.NEEDS_REVIEW].
 *  - high-confidence CERT_VALID -> [SuggestionVerdict.APPROVE];
 *    high-confidence NOT_VALID -> [SuggestionVerdict.DENY].
 *
 * The suggestion is carried to a human as AI_SUGGESTED; the human makes the
 * final call (never ship AI alone).
 */
object ConservativeVerdictPolicy {

    /** Confidence at/above which a *positive* (CERT_VALID or NOT_VALID) judgment may be suggested. */
    const val HIGH_CONFIDENCE: Double = 0.9

    fun apply(judgment: ModelJudgment): VerificationSuggestion {
        val verdict = when {
            judgment.verdict == ModelVerdict.INCONCLUSIVE -> SuggestionVerdict.NEEDS_REVIEW
            judgment.confidence < HIGH_CONFIDENCE -> SuggestionVerdict.NEEDS_REVIEW
            judgment.verdict == ModelVerdict.CERT_VALID -> SuggestionVerdict.APPROVE
            else -> SuggestionVerdict.DENY
        }
        return VerificationSuggestion(verdict, judgment.confidence, judgment.summary)
    }
}