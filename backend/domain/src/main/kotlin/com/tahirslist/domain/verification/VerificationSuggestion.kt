package com.tahirslist.domain.verification

/**
 * The AI provider's **suggested** disposition of a certification image, plus how
 * confident the model is. See [SuggestionVerdict] — this is never a final status.
 *
 * [confidence] is exposed clamped into the valid 0..1 range (a provider may
 * over-report, e.g. 0.97); the raw value is rejected if non-finite so the
 * conservative policy and storage never see garbage. [reasoning] is a short,
 * human-readable rationale (redacted of any PII upstream); nullable because some
 * verdicts (e.g. an unparseable response deflected to [SuggestionVerdict.NEEDS_REVIEW])
 * carry none.
 */
class VerificationSuggestion(
    val verdict: SuggestionVerdict,
    confidence: Double,
    val reasoning: String? = null,
) {
    /** Confidence clamped into the 0..1 range. */
    val confidence: Double = confidence.coerceIn(0.0, 1.0)

    init {
        require(confidence.isFinite()) { "Suggestion confidence must be finite." }
    }
}