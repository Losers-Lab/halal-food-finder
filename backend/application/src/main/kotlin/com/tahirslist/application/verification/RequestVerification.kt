package com.tahirslist.application.verification

import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.HalalCertificationReview
import java.time.Instant
import java.util.UUID

/**
 * Drives the forward verification path through the [VerificationProvider] seam:
 *
 *   SUBMITTED -> AI_REVIEW -> (AI suggests) -> AI_SUGGESTED
 *
 * The provider judgement is recorded as the review's conservative suggestion;
 * the review is left in [VerificationState.AI_SUGGESTED] awaiting a human.
 *
 * Failure semantics ("what happens when it fails"): if [VerificationProvider.suggest]
 * throws, this operation aborts and no AI_SUGGESTED review is produced — the
 * caller (sc-46 persistence) keeps the review in AI_REVIEW for a retry. The
 * review never auto-advances on a provider outage.
 */
class RequestVerification(
    private val provider: VerificationProvider,
) {
    fun execute(
        listingId: UUID,
        submittedBy: UUID,
        image: CertificationImage,
        now: Instant = Instant.now(),
        aiConsentGivenAt: Instant? = null,
    ): HalalCertificationReview {
        val aiReview = HalalCertificationReview.create(
            listingId,
            submittedBy,
            now,
            aiConsentGivenAt = aiConsentGivenAt,
        ).beginAiReview(now)
        // May throw VerificationProviderException (provider outage) — state stays AI_REVIEW.
        val suggestion = provider.suggest(image)
        return aiReview.recordAiSuggestion(suggestion, now)
    }
}