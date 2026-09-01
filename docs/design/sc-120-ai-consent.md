# Explicit AI-Verification Consent Before Cert Upload (sc-120)

> Backend TDD story sc-120 (Sprint 4, epic 112 Trust & Verification). Privacy:
> the owner's certification image may be sent to a **hosted AI** for automated
> verification (sc-117), so the owner must explicitly consent BEFORE the image is
> uploaded/archived. This story adds the consent gate and records consent with the
> verification request. Builds on the sc-46 owner-claim vertical.

## What sc-120 adds

| Module        | Addition |
|---------------|----------|
| `:domain`     | `HalalCertificationReview.aiConsentGivenAt: Instant?` — consent timestamp recorded on the aggregate |
| `:application` | `RequestVerification.execute(..., aiConsentGivenAt)`; `ClaimListing` gains a required `aiConsentGiven: Boolean` gate enforced BEFORE any image storage |
| `:persistence` | V14 `ALTER TABLE halal_certification_reviews ADD COLUMN ai_consent_at TIMESTAMPTZ`; the JDBC repo persists it |
| `:bootstrap`  | `POST /v1/listings/{id}/claim` now requires an `aiConsent` multipart part (also: `proof`, `certImage`) |

## The consent flow

`POST /v1/listings/{listingId}/claim` (multipart: `proof` text + `aiConsent` +
`certImage` file):

1. **Auth (edge):** deny-by-default resource server (sc-131) requires a valid
   access JWT → unauth 401.
2. **Consent gate (sc-120):** `ClaimListing` requires `aiConsentGiven == true`
   via `require(...)` → `IllegalArgumentException` → **400 `invalid_input`**.
   This fires BEFORE any repository lookup or image storage — the image is never
   archived without consent. `RequestVerification` threads the consent timestamp
   into the review so it is recorded with the verification request.
3. **Owner guard:** `restaurant_listings.owner_id` must equal the claimer →
   403 `not_listing_owner`, unknown listing 404.
4. **Store the certification image** (only after consent) via
   `CertificationImageStorage` (MinIO/S3).
5. **Drive the sc-117 state machine** (`SUBMITTED → AI_REVIEW → AI_SUGGESTED`).
6. **Persist the review** — including `ai_consent_at`.

## Failure semantics ("what happens when it fails")

- **No consent (missing, or value != "true"):** the claim is rejected with the
  generic **400 `invalid_input`** envelope; the certification image is never
  stored, no review row is written. Test asserts the review count is unchanged.
- **Provider outage** (sc-46 semantics unchanged): the held `AI_REVIEW` review
  also records the consent timestamp, so consent is never lost if the AI is
  briefly unreachable.

## Design decisions

- **Consent is recorded per-review, not per-user.** A row is the unit of trust; a
  user may claim multiple listings over time, and each claim is an independent
  privacy decision. Storing the timestamp on the review keeps the audit trail
  with the evidence it governed.
- **Nullable column, timestamp not boolean.** `ai_consent_at TIMESTAMPTZ` is
  null only for rows that predate this story (no consent was ever given). New
  claims always set it. A timestamp is more auditable than a bare boolean.
- **Enforcement lives in the application layer (`ClaimListing`), not the DB.** The
  DB column documents consent; the behavioural gate (reject un-consented uploads)
  is a use-case concern. A DB `NOT NULL` would have broken pre-consent rows and
  added nothing — the gate already prevents un-consented writes.
- **`aiConsent` is a strict `"true"` (case-insensitive) match.** Anything else,
  including a missing part, is treated as not consented → 400. The frontend must
  send `aiConsent=true` only after the owner affirmatively ticks the consent box.

## Notes / follow-ups (not built here)

- **Frontend consent UI** (acceptance #1/#4) is NOT built here: there is no
  frontend claim page yet (sc-46 shipped backend-only). The UI step belongs in a
  frontend claim story (Maryam) and must send `aiConsent=true`; the backend
  enforcement and recording land here. Flagged as a dependency in the handoff.
- `ClaimResponse` does not yet expose the consent timestamp; add it when the
  frontend/VC surface (sc-73) needs to read consent back.

## Verification

- `./gradlew test` green (TDD). Covers (RED→GREEN per layer): domain consent is
  recorded and survives every transition; `RequestVerification` records the
  consent timestamp on the created review; `ClaimListing` rejects a claim without
  consent before any storage/persistence (asserting zero saves/verification
  calls); V14 migration + JDBC round-trip of `ai_consent_at`; full-context HTTP
  endpoint — 201 happy path records consent, and a `aiConsent=false` claim returns
  400 `invalid_input` with the review count unchanged. PostgisSmokeTest migration
  count 13 → 14.