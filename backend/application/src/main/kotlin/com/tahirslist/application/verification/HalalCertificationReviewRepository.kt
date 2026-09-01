package com.tahirslist.application.verification

import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.VerificationState
import java.util.UUID

/**
 * Persistence port (hexagonal "out" port) for certification reviews. Implemented
 * by the JDBC adapter (persistence module); the application layer depends only on
 * this contract.
 *
 * sc-46 needs only [save] (the claim path persists the review that results from
 * driving it through the sc-117 state machine). Reading reviews back for the
 * Verification Committee — find by listing, query the pending-by-state work
 * queue — is sc-73 and grows this port:
 *  - [findById] reaches a single review for the approve/deny command;
 *  - [findByState] feeds the VC workqueue (reviews awaiting a human decision).
 *
 * [save] is an upsert: it writes a review whether the row is new (claim path)
 * or the aggregate has advanced through a state transition (VC decision path).
 */
interface HalalCertificationReviewRepository {
    fun save(review: HalalCertificationReview): HalalCertificationReview

    /** The review with the given id, or null if none. */
    fun findById(id: UUID): HalalCertificationReview?

    /** All reviews currently in [state] (the VC workqueue reads AI_SUGGESTED). */
    fun findByState(state: VerificationState): List<HalalCertificationReview>
}