package com.tahirslist.application.verification

/**
 * Raised when a [VerificationProvider] is unavailable or returns an unusable
 * response. The caller treats this as external-service unavailability, NOT as a
 * verdict — a review in progress stays in AI_REVIEW and can be retried.
 */
class VerificationProviderException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)