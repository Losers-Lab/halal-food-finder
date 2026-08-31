package com.tahirslist.domain.verification

/**
 * The hosted vision model's raw classification of a certification image — the
 * transport-level verdict the conservative policy consumes.
 *
 * [ModelVerdict.CERT_VALID] = clearly an authentic, current halal certification
 * naming the restaurant; [ModelVerdict.NOT_VALID] = clearly not (forged,
 * expired, wrong document, another restaurant); [ModelVerdict.INCONCLUSIVE] =
 * ambiguous/unreadable. [confidence] is clamped to 0..1.
 */
enum class ModelVerdict {
    CERT_VALID,
    NOT_VALID,
    INCONCLUSIVE,
    ;
}

/**
 * A parsed verdict from a hosted vision model. See [ModelVerdict]. Feed through
 * [ConservativeVerdictPolicy] to produce a [VerificationSuggestion]; it is NOT
 * itself the suggestion a review records.
 */
class ModelJudgment(
    val verdict: ModelVerdict,
    confidence: Double,
    val summary: String? = null,
) {
    /** Confidence clamped into the 0..1 range. */
    val confidence: Double = confidence.coerceIn(0.0, 1.0)

    init {
        require(confidence.isFinite()) { "Model confidence must be finite." }
    }
}