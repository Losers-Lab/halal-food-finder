# Verification Committee — Approve/Deny Review Loop (sc-73)

> Backend TDD story sc-73 (Sprint 4, epic 112 Trust & Verification). Builds the
> **human decision loop** on the sc-46/sc-117 verification vertical
> (`docs/design/sc-46-owner-claim.md`, `docs/design/sc-117-verification-seam.md`).
> This story ships the Verification Committee's approve/deny commands and the
> pending workqueue read. The certification view/edit UI is a frontend story.

## What sc-73 adds (hexagonal, matches settings.gradle.kts)

| Module     | Addition |
|------------|----------|
| `:application` | `VerificationCommittee` use case (listPending/approve/deny); port growth on `HalalCertificationReviewRepository` (`findById`, `findByState`); `RestaurantListingRepository.updateVerificationStatus`; exceptions `ReviewNotFoundException`, `ReviewNotPendingException` |
| `:domain`      | *(none — the aggregate already owns `beginHumanReview`/`approve`/`deny` from sc-117)* |
| `:persistence` | V15 opens `VERIFIED` on `restaurant_listings.verification_status` (was UNVERIFIED-only); `JdbcHalalCertificationReviewRepository` upsert `save` + `findById` + `findByState`; `JdbcRestaurantListingRepository.updateVerificationStatus` (source + `listing_search` mirror in one tx) |
| `:bootstrap`   | `VerificationCommitteeController` (`GET/POST /v1/verification-committee/reviews…`), VC-role RBAC, wired in `VerificationConfig` |

## The review loop

A Verified Committee member (role `VERIFICATION_COMMITTEE` on the JWT):

1. **List the workqueue** — `GET /v1/verification-committee/reviews` → every
   review in `AI_SUGGESTED` (awaiting a human). No commit list of listing/cert is
   returned here beyond the review aggregate.
2. **Approve** — `POST /v1/verification-committee/reviews/{reviewId}/approve`
   (optional `{reason}`). Walks the aggregate `AI_SUGGESTED → HUMAN_REVIEW →
   APPROVED`, persists, and promotes the listing to **VERIFIED** in both
   `restaurant_listings` and the `listing_search` read mirror (one transaction).
3. **Deny** — `POST /v1/verification-committee/reviews/{reviewId}/deny`
   (required `{reason}`). Walks to `DENIED`, records the reason; the listing
   stays **UNVERIFIED**.

Both commands re-load the review by id and refuse to decide anything not in a
decisable state (`AI_SUGGESTED`). Already-decided or never-pending reviews → 409
`review_not_pending`; unknown review → 404 `review_not_found`.

## Failure semantics ("what happens when it fails")

- **Wrong state** (`ReviewNotPendingException`): 409. A review decided twice can
  never be silently flipped — the aggregate's `transition()` guard fails loudly.
- **Missing review** (`ReviewNotFoundException`): 404.
- **Listing vanished between review creation and approval**: `updateVerificationStatus`
  returns null and approve aborts with 404 — an APPROVED review is never left
  pointing at a nonexistent listing.
- **Blank deny reason**: 400 invalid input at the use case boundary.
- **Non-committee caller**: the deny-by-default resource server (sc-131) 401s
  unauthenticated callers; this controller 403s any *authenticated* non-committee
  role (`forbidden`). The workqueue is never shown to a non-committee account.

## RBAC

The resource server already validates that the JWT `role` claim is one of the six
MVP roles (sc-131 `RoleClaimValidator`). sc-73 adds the *per-route* committee
guard inside the controller: the JWT `role` must equal `VERIFICATION_COMMITTEE`,
otherwise 403. The `decidedBy` authority is always the JWT `sub` — never a
client-supplied value.

## Notes / follow-ups (not built here)

- Showing the certification **image** to the VC while deciding is a read-surface
  follow-up; sc-46/sc-73 ship the write path (`CertificationImageStorage.save`)
  and sc-73's read-surface follow-up added `loadLatest` + a public cert-image
  proxy (`GET /v1/listings/{id}/certificate`) so the CertificatePanel's
  "View certificate" works with live data (**security-reviewed by Omar**).
- The ownership **proof text** (sc-46) is not yet displayed/persisted on the
  review; the VC view follow-up may surface it.
- Reversal (`APPROVED/DENIED → REVERSED`, e.g. cert expiry/revocation) is a
  separate terminal transition already on the aggregate; its endpoint is not in
  this story. On membership, detail-page.md §1.2 says an EXPIRED cert downgrades
  the badge — the read surface emits `expiresOn`; the frontend computes the
  downgrade from it (no backend state flip needed for display).
- Frontend committee screen is a separate (frontend) story consuming these APIs.

## Certificate display metadata (sc-73 read-surface follow-up)

`GET /v1/listings/{id}` DetailResponse now emits the CertificatePanel facts for a
VERIFIED listing:

- `certificate.certifier` — the issuing body (e.g. "HFSAA"), transcribed by the
  VC **at approval** (they are looking at the certificate image). Stored as
  `halal_certification_reviews.certifier` (V16).
- `certificate.reviewedOn` — the VC's `decision_at` (the approval instant).
- `certificate.expiresOn` — the certificate's expiry, transcribed at approval.
  Stored as `halal_certification_reviews.expires_on` (V16).
- `certificate.certificateUrl` — a same-origin proxy
  (`GET /v1/listings/{id}/certificate`) serving the archived certification image.

Source of truth decision: the certification metadata is **committee-transcribed
at approve time**, never owner-uploaded, so "Reviewed by our verification
committee — not self-reported" stays honest. Fields the committee did not record
are `null` and the frontend omits them (detail-page.md §1.2 "omit unknown
fields"). Only listings in `VERIFIED` status emit a certificate; unverified
listings emit `certificate: null`.

**Security posture change (Omar review):** surfacing the archived certification
image on a *public* read endpoint is a deliberate exposure of evidence documents.
Flagged for Omar's review in the PR that ships this read surface.

## Verification

- `./gradlew test` green (TDD). Covers: application use case (pending list,
  approve→VERIFIED promotion, deny+reason, state guards, missing review, missing
  listing, blank reason), persistence (V15 allows VERIFIED, upsert save round-trip,
  findById/findByState, updateVerificationStatus source+mirror atomic), and the
  full-context HTTP endpoint (list, approve promotes to VERIFIED, deny stays
  UNVERIFIED, re-decision 409, non-VC 403, unauth 401) against PostGIS.