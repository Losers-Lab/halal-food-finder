package com.tahirslist.application.verification

import com.tahirslist.domain.verification.HalalCertificationReview

/**
 * Persistence port (hexagonal "out" port) for certification reviews. Implemented
 * by the JDBC adapter (persistence module); the application layer depends only on
 * this contract.
 *
 * sc-46 needs only [save] (the claim path persists the review that results from
 * driving it through the sc-117 state machine). Reading reviews back for the
 * Verification Committee (find-by-listing, query by state) is sc-73 and will grow
 * this port then — deliberately not added speculatively.
 */
interface HalalCertificationReviewRepository {
    fun save(review: HalalCertificationReview): HalalCertificationReview
}