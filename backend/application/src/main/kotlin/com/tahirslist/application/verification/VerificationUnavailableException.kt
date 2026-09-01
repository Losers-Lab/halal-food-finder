package com.tahirslist.application.verification

/**
 * Thrown when the autonomous verification provider is unavailable during a claim
 * (sc-46 "what happens when it fails"). The claim is not dropped: the review is
 * durably persisted in AI_REVIEW so it can be retried, and the caller maps this
 * to a 503. It is treated as unavailability, never as a verdict against the cert.
 */
class VerificationUnavailableException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)