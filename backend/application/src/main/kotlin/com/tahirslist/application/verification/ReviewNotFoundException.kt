package com.tahirslist.application.verification

import java.util.UUID

/**
 * Thrown when a verification review cannot be found by id. Mapped by the web
 * layer to 404 `review_not_found`.
 */
class ReviewNotFoundException(val reviewId: UUID) :
    RuntimeException("Verification review $reviewId not found")