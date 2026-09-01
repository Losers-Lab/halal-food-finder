package com.tahirslist.bootstrap.verification

/**
 * Thrown when a caller without the Verification Committee role attempts a
 * committee-only action. Returned as 403 `forbidden` by the controller.
 */
class NotVerificationCommitteeException :
    RuntimeException("Verification Committee role required")