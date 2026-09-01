# Verification Provider Seam — hosted AI + human review (sc-117)

> Backend TDD story sc-117 (Sprint 4, epic 112 Trust & Verification). Ratified
> architecture: **ARCHITECTURE.md §1.0 "Verification (hosted AI)"** and
> **"Verification provider (U-11)"** (Gemini 2.5 Flash paid tier, human-in-the-loop
> mandatory). This doc records the seam contract, the conservative-verdict policy,
> and the state machine so later stories (sc-46 owner claim, sc-73 human review,
> sc-119 partial-halal) build on a stable foundation.

## Goal

Define the **pluggable verification seam**: a `VerificationProvider` port, a
**default `HostedVisionAdapter`** implementation (calls a hosted multimodal
vision model), a **conservative-verdict policy**, and the **verification state
machine**. No in-house ML. No persistence (that is sc-46/73). This story only
proves the seam and the SUBMITTED → AI_REVIEW → AI_SUGGESTED forward path with
the AI adapter **mocked in tests (no live API call)**.

## Module layout (hexagonal, matches settings.gradle.kts)

```
:domain  domain/verification        -> state machine + suggestion + conservative policy + review aggregate
:application  application/verification  -> VerificationProvider port + RequestVerification use case
:verification-ai  verification/ai    -> HostedVisionAdapter (default), VisionModelClient transport seam, RestVisionModelClient
```

The same port/adapter shape as `GeocoderPort`↔`PhotonGeocoder` and
`ImagePort`↔`S3ImagePort` (`docs/reviews/sc-138-external-services.md` §3): the
application layer depends on the port only; the hosted provider is swappable by
implementation/bean selection, never by schema of the use case.

## Port contract — `VerificationProvider`

```kotlin
interface VerificationProvider {
    fun suggest(image: CertificationImage): VerificationSuggestion
}
```

- **Input:** `CertificationImage(contentType, bytes)` — the cert-only image
  (upload hygiene / downscale / EXIF-strip are handled upstream in sc-46; the
  seam receives image bytes).
- **Output:** a **conservative `VerificationSuggestion`** — the AI's *suggested*
  disposition, **never** a final VERIFIED status.
- **Never final;** the suggestion only moves the review to AI_SUGGESTED, which a
  human (Verification Committee) must confirm before the listing is VERIFIED
  (founder mandate: "AI-suggests → VC-approves, never ship AI alone").
- **Failure:** provider outage/invalid response throws
  [VerificationProviderException]; the caller treats that as unavailability, not
  as a verdict. The review stays in AI_REVIEW and can be retried.

### Conservative-verdict policy (the "when in doubt, human" rule)

Trust rule captured as `ConservativeVerdictPolicy` in the domain, so every
provider adapter is held to the same standard and it is unit-tested once.

```
if verdict == INCONCLUSIVE                                    -> NEEDS_REVIEW
else if confidence < HIGH_CONFIDENCE (0.9)                    -> NEEDS_REVIEW
else if verdict == CERT_VALID                                 -> APPROVE  (suggestion only)
else                                                          -> DENY     (suggestion only)
```

- **False-positive over false-negative:** a genuinely halal cert the AI is
  unsure about goes to a human; an ambiguous/fake that the AI flags as "not
  valid" is only ever *suggested* DENY, never final.
- `HIGH_CONFIDENCE = 0.9` is deliberately high: an AI **suggestion** to approve
  requires near-certainty because a wrongly-granted VERIFIED badge is a trust
  failure for the whole product (amanah). Everything else defers to a human.
- Provider output that does not parse to a known verdict is treated as
  **INCONCLUSIVE → NEEDS_REVIEW** (conservative default — never invent a verdict).

## State machine

`VerificationState`: `SUBMITTED → AI_REVIEW → AI_SUGGESTED → HUMAN_REVIEW →
{APPROVED | DENIED}` (+ `REVERSED`).

Allowed transitions (enforced by `HalalCertificationReview`, immutable copy
semantics; any other transition throws `IllegalStateException`):

| From           | Event                     | To           |
|----------------|---------------------------|--------------|
| SUBMITTED      | begin AI review           | AI_REVIEW    |
| AI_REVIEW      | record AI suggestion      | AI_SUGGESTED |
| AI_SUGGESTED   | human takes it up         | HUMAN_REVIEW |
| HUMAN_REVIEW   | human approves            | APPROVED     |
| HUMAN_REVIEW   | human denies              | DENIED       |
| APPROVED       | certification reversed    | REVERSED     |
| DENIED         | decision reversed         | REVERSED     |

- `REVERSED` is the **terminal roll-back** for a finalized disposition
  (grant reversal on cert expiry/revocation, or a wrongful denial being
  overturned). The forward human actions (HUMAN_REVIEW→APPROVED/DENIED) and
  REVERSED are **state-machine transitions implemented here** (foundation), but
  their **commands/endpoints are sc-73/46** — sc-117 does not ship those.
- A provider failure during AI review leaves the review in **AI_REVIEW** (never
  auto-progresses), so it can be retried.

## Forward path driven by this story

`RequestVerification.execute(listingId, submittedBy, image)`:

1. creates the review in **SUBMITTED**,
2. `beginAiReview()` → **AI_REVIEW**,
3. `provider.suggest(image)` (the seam; default `HostedVisionAdapter`),
4. `recordAiSuggestion(suggestion)` → **AI_SUGGESTED**.

## Spot-verified properties

- Port + default adapter exist; adapter is a `VerificationProvider`.
- Conservative policy: INCONCLUSIVE / low-confidence ⇒ NEEDS_REVIEW.
- Adapter tests mock the vision transport (`VisionModelClient`) and run against
  a pure-JDK `HttpServer` — **no live provider call.**
- `./gradlew test` green (TDD). Commits `[SC-117]`.

## Out of scope (explicitly not built here)

- Persistence / DB migration for review records (sc-46/73).
- Claim / upload endpoints, RBAC guards, cert storage (sc-46).
- Approve/deny/reverse commands & VC listing UI (sc-73).
- Spring bean wiring / config resolution in bootstrap (sc-46 when the seam is
  wired into the app).
- Actual Gemini SDK / provider package — the transport is an HTTP client behind
  `VisionModelClient`.