package com.tahirslist.application.verification

import com.tahirslist.domain.verification.VerificationState
import java.util.UUID

/**
 * Thrown when a verification review is in a state that cannot be decided
 * (e.g. it was already decided, or has not reached the pending human-review
 * stage yet). Mapped by the web layer to 409 `review_not_pending`.
 */
class ReviewNotPendingException(
    val reviewId: UUID,
    val state: VerificationState,
) : RuntimeException("Verification review $reviewId is not pending; current state $state")