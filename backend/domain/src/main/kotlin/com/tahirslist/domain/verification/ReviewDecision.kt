package com.tahirslist.domain.verification

import java.time.Instant
import java.util.UUID

/**
 * The human (Verification Committee) decision recorded when a review is
 * finalized. [VerificationOutcome.REVERSED] is recorded when a finalized
 * APPROVED/DENIED review is later reversed.
 */
class ReviewDecision(
    val outcome: VerificationOutcome,
    val decidedBy: UUID,
    val reason: String?,
    val decidedAt: Instant,
)