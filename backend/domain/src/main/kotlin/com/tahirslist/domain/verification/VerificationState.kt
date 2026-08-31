package com.tahirslist.domain.verification

/**
 * The lifecycle states of a certification review.
 *
 * Forward path (sc-117 drives SUBMITTED -> AI_REVIEW -> AI_SUGGESTED; the human
 * actions and reversal are state-machine transitions implemented here but their
 * commands/endpoints ship in sc-46/sc-73):
 *
 *   SUBMITTED -> AI_REVIEW -> AI_SUGGESTED -> HUMAN_REVIEW -> { APPROVED | DENIED }
 *
 * Plus [+ REVERSED], the terminal roll-back of a finalized disposition (a grant
 * reversed on cert expiry/revocation, or a wrongful denial overturned).
 *
 * REVERSED is reachable from APPROVED and DENIED and is terminal. There is no
 * transition out of it — reversing a review sends the *listing* back to an
 * unverified posture (a separate, caller concern), it does not reopen this
 * review record.
 */
enum class VerificationState {
    SUBMITTED,
    AI_REVIEW,
    AI_SUGGESTED,
    HUMAN_REVIEW,
    APPROVED,
    DENIED,
    REVERSED,
    ;
}