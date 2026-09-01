package com.tahirslist.application.verification

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.VerificationState
import java.time.Instant
import java.util.UUID

/**
 * Owner claim (sc-46): an authenticated listing **owner** submits proof of
 * ownership plus a photo of their halal certification, and the listing is driven
 * through the sc-117 verification state machine.
 *
 * Flow and its failure semantics ("what happens when it fails"):
 *
 *  1. Validate the ownership proof (trimmed, non-blank) — fails fast, before I/O.
 *  2. Require explicit AI-analysis consent (sc-120) — the certification image may
 *     be sent to the hosted AI, so the owner must affirm consent BEFORE upload;
 *     a missing/false consent is a 400 and the image is never archived.
 *  3. The listing must exist ([ListingNotFoundException] → 404) and be claimed by
 *     exactly the recorded `owner_id` ([NotListingOwnerException] → 403). RBAC is
 *     enforced at the edge (deny-by-default resource server, JWT `sub`); this is
 *     the second, owner-scoped guard. Never act on a client-supplied owner.
 *  4. Store the certification image via [CertificationImageStorage] (MinIO/S3).
 *  5. Drive [RequestVerification] (SUBMITTED → AI_REVIEW → AI_SUGGESTED). The AI
 *     only ever *suggests* — the review is never auto-APPROVED here.
 *  6. Persist the resulting review ([HalalCertificationReviewRepository]).
 *     Consent is recorded on the review (repository) with the request.
 *
 * On a [VerificationProviderException]: the claim is NOT dropped. A review held in
 * [VerificationState.AI_REVIEW] is persisted so it can be retried later, and the
 * operation rethrows as [VerificationUnavailableException] (→ 503). The review
 * never auto-advances on an outage, and it can never auto-grant verification.
 */
class ClaimListing(
    private val listings: RestaurantListingRepository,
    private val reviews: HalalCertificationReviewRepository,
    private val certificates: CertificationImageStorage,
    private val requestVerification: RequestVerification,
) {

    fun execute(
        listingId: UUID,
        claimerId: UUID,
        proof: String,
        aiConsentGiven: Boolean,
        contentType: String,
        imageBytes: ByteArray,
        now: Instant = Instant.now(),
    ): HalalCertificationReview {
        require(proof.trim().isNotBlank()) { "Ownership proof must not be blank." }
        // sc-120 privacy guard: the certification image may be sent to the hosted
        // AI for analysis, so the owner must explicitly consent BEFORE it is
        // archived/uploaded. Fail fast here, before any I/O.
        require(aiConsentGiven) { "Explicit consent to AI certification analysis is required before upload." }

        val listing = listings.findById(listingId) ?: throw ListingNotFoundException(listingId)
        if (listing.ownerId != claimerId) {
            throw NotListingOwnerException(listingId, claimerId)
        }

        // Evidence archived first, so a later AI outage still leaves the cert on record.
        certificates.save(listingId, contentType, imageBytes)

        return try {
            val suggested = requestVerification.execute(
                listingId = listingId,
                submittedBy = claimerId,
                image = CertificationImage(contentType, imageBytes),
                now = now,
                aiConsentGivenAt = now,
            )
            reviews.save(suggested)
        } catch (e: VerificationProviderException) {
            // Provider outage: hold a durable AI_REVIEW record for a retry. Never
            // drop the claim, never auto-grant.
            val held = HalalCertificationReview.create(listingId, claimerId, now, now)
                .beginAiReview(now)
            reviews.save(held)
            throw VerificationUnavailableException(
                "Verification provider unavailable; review held in AI_REVIEW for retry.",
                e,
            )
        }
    }
}