# Owner Claim + Certification Upload (sc-46)

> Backend TDD story sc-46 (Sprint 4, epic 112 Trust & Verification). Builds the
> **claim** layer on the sc-117 verification seam (`docs/design/sc-117-verification-seam.md`).
> This story ships the claim write-path: proof of ownership + certification image
> in, a durable review driven through the state machine out. Reading reviews for
> the Verification Committee and the approve/deny commands are sc-73.

## What sc-46 adds (hexagonal, matches settings.gradle.kts)

| Module           | Addition |
|------------------|----------|
| `:application`   | `ClaimListing` use case; ports `HalalCertificationReviewRepository` (save), `CertificationImageStorage` (save); `InMemoryCertificationImageStorage` dev/test fallback; `NotListingOwnerException`, `VerificationUnavailableException`; `DeferToHumanProvider` safe default |
| `:persistence`   | V13 `halal_certification_reviews` migration; `JdbcHalalCertificationReviewRepository` |
| `:storage-s3`    | `S3CertificationImageStorage` (reuses the S3/MinIO client + bucket, `certifications/{listingId}/{uuid}` namespace); bean in `S3ImagePortConfig` |
| `:bootstrap`     | `VerificationConfig` (provider + claim wiring), `VerificationClaimController` (`POST /v1/listings/{listingId}/claim`) |

## The claim flow

`POST /v1/listings/{listingId}/claim` (multipart: `proof` text + `certImage` file):

1. **Auth (edge):** deny-by-default resource server (sc-131) requires a valid
   access JWT → unauth 401. The acting account is the JWT `sub`.
2. **Owner guard:** `restaurant_listings.owner_id` must equal the claimer →
   non-owner 403 `not_listing_owner`, unknown listing 404.
3. **Store the certification image** via `CertificationImageStorage` (MinIO/S3)
   as durable evidence.
4. **Drive the sc-117 state machine:** `RequestVerification` walks
   `SUBMITTED → AI_REVIEW → AI_SUGGESTED`, recording the AI's conservative
   suggestion. The AI can only ever *suggest* — the review is never auto-APPROVED
   here; the Verification Committee decides in sc-73.
5. **Persist the review** (`JdbcHalalCertificationReviewRepository`).

## Failure semantics ("what happens when it fails")

- **Provider outage** (`VerificationProviderException`): the claim is NOT dropped.
  A review held in `AI_REVIEW` is persisted for a later retry and the endpoint
  returns **503 `verification_unavailable`**. The review never auto-advances on an
  outage and can never auto-grant verification.
- **No AI configured** (dev/test, or a deployment running review fully manual):
  the safe `DeferToHumanProvider` is wired, which always suggests `NEEDS_REVIEW`.
  This keeps the app bootable and makes "when in doubt, human" structural, not
  best-effort. The hosted provider (`app.verification.hosted.endpoint`) is the
  mutually-exclusive alternative.

## Notes / follow-ups (not built here)

- The review table stores `state`, the AI `suggestion_*`, and the human
  `decision_*` columns (NULL until sc-73); `find`/query and approve/deny/reverse
  commands are sc-73, growing the repository port then (not added speculatively).
- The ownership **proof text** is validated (trimmed, non-blank) and carried as
  claim input, but not yet persisted on the review; storing it for the VC view is
  a sc-73 follow-up.
- Upload hygiene (downscale / EXIF-strip / cert-not-composite) remains an open
  sc-46 follow-up noted in the sc-117 design; sc-46 archives raw bytes.

## Verification

- `./gradlew test` green (TDD). Covers: claim application use case (happy path,
  APPROVE-never-elevates, non-owner 403, unknown 404, blank proof 400, provider
  outage → AI_REVIEW + 503), V13 migration + JDBC round-trip + FK/CHECK integrity,
  MinIO-backed cert storage round-trip (storage-s3), and the full-context HTTP
  endpoint (201 persisted AI_SUGGESTED, 401, 403, 404, 400) against PostGIS.