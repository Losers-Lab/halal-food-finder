# ARCHITECTURE.md — Halal Food Finder

This document describes the architecture of Halal Food Finder. It is split into
two distinct parts:

1. **Agreed** — the domain model and engineering process we have actually agreed
   upon (derived from the Shortcut Product Requirements Doc and Development
   Process doc).
2. **Unresolved** — technical decisions that are **not yet agreed**. These are
   deliberately left open and marked clearly; **do not treat them as decided.**

> Status: **Greenfield / DRAFT.** This document accompanies the first commit of
> an empty repository. Nothing here implies implementation exists. Hosting,
> geo, reviews, verification provider, auth tuning, and SEO were ratified
> 2026-08-29 (see §1.0); partial-halal and alcohol-served added to MVP scope.

---

## Part 1 — Agreed architecture

### 1.0 Resolved technical decisions (ratified by founder)

The following were explicitly agreed with the founder. Anything not listed here
remains **unresolved** (see Part 2).

| Decision | Agreed | Note |
|----------|--------|------|
| **Frontend architecture** | **Next.js** (TypeScript) responsive web (SSR/SEO), later wrapped in **Capacitor** to also ship as a mobile app | Single codebase for website + app; **TypeScript confirmed** by founder; typed client generated from the OpenAPI spec. Resolved 2026-08-28. |
| **Browser extensions (Google + Yelp)** | **In MVP scope** | MV3 thin content-script overlays, shared TS core, address matching; resolved 2026-08-28. |
| **Distance-filter UX** | **Preset radius chips** (ARIA radio-group: 1/5/10/25 mi + "Any distance"), pinned "Near \<place\>" center pill | Fatima (design) recommendation, accepted; resolved 2026-08-28. |
| **Distance semantics** | **Straight-line (haversine)** distance; radius in miles | Founder decision; backend API contract: center + radius query, distance per result. |
| **Geo/search entry UX** | One persistent search bar (Google Maps autocomplete + Current-Location icon/≥44px target on mobile) pinning a search center | Fatima recommendation, accepted. |
| **Filter bar system** | Hero chip row (cutting method — flagship — + top cuisines + distance) + a "+ Filters" sheet (mobile) / popover (desktop) for the tail (price, rating, full cuisine, distance-advanced); cuisine defaults OR with "Advanced" → Any(OR)/All(AND) toggle | Fatima recommendation, accepted; PRD OR default honored. |
| **Verification trust language** | Layer 1: Verified green checkmark badge + posted certification asset (tooltip "Halal certification reviewed & approved by our committee"); Layer 2: neutral "Unverified" tag (never error-red); Layer 3: hand-cut/machine-cut via icon+text tag, colorblind-safe, never color-only | **Verified = green checkmark** (founder); "Unverified" copy + certification expiry display remain open product flags. |
| **Design system / tokens** | Build a **fresh, clean design system from first principles** (mobile-first, trustworthy, accessible), anchored on the agreed verification-trust language; **Figma is not authoritative** | Founder: the existing Figma is an old generic draft and not sacrosanct. Figma-token extraction is **optional**; tokens defined fresh (colors w/ AA contrast, 4px spacing, radius, rem type scale, focus/ring a11y tokens, semantic tokens like `badge-verified`) and wired via Tailwind v4 `@theme`. Brand palette to be confirmed. |
| **Backend language** | **Kotlin** | Founder decision; strong Java background, favourite language Kotlin. |
| **Backend framework** | **Spring Boot 3.3+** (Kotlin, blocking thread-per-request with **Virtual Threads**; NOT WebFlux) | Hamza research; ratified by founder. Ktor 3 kept as swappable alternative via hexagonal modules. |
| **Database & data access** | **PostgreSQL 16/17 + PostGIS**; **jOOQ** (search) + **Spring Data JDBC** (CRUD); **Flyway** migrations | Ratified. DENIED: JPA/Hibernate, Mongo. |
| **API contract** | **Code-first REST via springdoc-openapi**; committed, CI-linted, versioned **OpenAPI 3.1** `/v1` | Frontend types client + extensions consume it. |
| **Authn/Authz** | **Spring Security OAuth2 resource server**; email/password (**Argon2id** — modern best practice, one-time tuning); short **JWT (RS256)** access + **rotating hashed refresh**; **RBAC** for 6 roles + anonymous + extension API-keys | Ratified. |
| **Geo/search** | **PostGIS `geography(Point,4326)` + GiST**; one query: `ST_DWithin` (filter) + `ST_DistanceSphere` (order); denormalized `listing_search` table; offset paging | Straight-line/miles semantics (founder). |
| **Image storage** | **MinIO self-hosted** (S3-compatible, **$0**, runs in Docker alongside the app) for MVP — private bucket, presigned PUT/GET, namespaced keys, versioning + content SHA-256 audit. **Cloudflare R2 is the target for the future** | Founder decision: MinIO now (fully $0, self-operated), **R2 later** (free tier + managed + zero egress). Build against the **S3 SDK on a configurable endpoint** so R2 is a config change, not code — keep all storage access behind one internal interface and avoid AWS-only features. |
| **Verification (hosted AI)** | **Hosted/API multimodal AI (vision)** judges halal-certificate image → conservative verdict + **lightweight human review/correction loop**; **no in-house ML model/training pipeline** | Founded pivot + clarification: "committee" = AI verdict + human spot-check/correct (NOT a people board, NOT training). Pluggable `VerificationProvider` port, default = HostedVisionAdapter. Details: state machine SUBMITTED→AI_REVIEW→AI_SUGGESTED→HUMAN_REVIEW→APPROVED/DENIED (+REVERSED); conservative VerificationSuggestion contract; privacy/security flags (provider terms, PII redaction, retention). See unresolved U-11. |
| **Testing/TDD** | **Kotest + MockK + Testcontainers** (PostGIS) + Spring Test slices; hexagonal modules `:domain → :application → adapters → :bootstrap` | Supports mandated backend TDD. |
| **Build/ops baseline** | **Gradle (Kotlin DSL)**, **JDK 21 LTS** (Temurin), Kotlin 2.x K2, **Jib** OCI images, Postgres+PostGIS | Deploy target now resolved (see U-08 below). |
| **Hosting / deploy (U-08)** | **Hetzner VPS (CX class, ~US$6–7/mo, e.g. CX23 2 vCPU/4GB/40GB) self-hosting** Postgres 16/17 + PostGIS, MinIO, the Spring Boot Jib image, and the async verification worker on one always-on box | Ratified by founder 2026-08-29. Cheapest reliable floor — self-host has full extension control so PostGIS can't be dropped by a vendor tier. **Neon (managed Postgres, PostGIS native, free/scale-to-zero) is the $0 zero-ops alternative.** ⚠ Use Hetzner's cost-optimized CX/CAX line, NOT the CPX line (prices raised 2026). Migration is config-cheap: Jib OCI images + env-var connection strings; DB move = pg_dump/restore + one JDBC URL (<1 day). Avoid provider-specific DB lock-in. EKS is NOT an MVP option (~$60–300/mo). |
| **Maps / geo (U-09)** | **Google Places API for Autocomplete + Geocoding only**, held **server-side** (no Maps SDK / heavy rendering — PostGIS does the radius match) | Since 2025-03-01 Google replaced the $200/mo pool with per-SKU free caps: **Autocomplete free 10k/mo, Geocoding free 10k/mo** → ~$0 at MVP (card on file, pay only past caps). OSM (Photon+Nominatim) is the $0-no-card fallback (weaker UX, no SLA). Server-side key → no client exposure. |
| **Reviews / ratings (U-10)** | **Official APIs + server-side keys + 24h backend cache** (1 restaurant view = 1 API call, repeats hit cache at $0): **Google Place Details (Enterprise fields)** = rating + user_ratings_total + reviews ($20/1k, 1k free/mo → $0); **Yelp Fusion free tier** (~500/day) = rating + review_count | MVP shows rating + count + "Read on [Google/Yelp]" link; **Yelp full review-snippet text deferred to paid Enhanced tier (~$7.99–9.99/1k)** until revenue. **No scraping** (ToS/DMCA risk). Fallback for listings w/o linked Google/Yelp: owner/user-entered rating + external links. ~$0/mo. |
| **Verification provider (U-11)** | **Google Gemini 2.5 Flash (PAID API tier)** as the default `HostedVisionAdapter`/`VerificationProvider`; ~$0.0002–0.0005/verification, 1–4s latency, strong document understanding | Ratified 2026-08-29. **Never the free AI Studio tier** (uses data to improve Google products, no retention controls). Non-Vertex Gemini API retains prompts 55 days for abuse monitoring. **Alternative: Claude Haiku 4.5** — cleanest default data posture (no retention/training by default) if guarantees outweigh cost. **Human-in-the-loop is MANDATORY** (both Google & OpenAI require human supervision for high-stakes automated decisions): AI-suggests → VC-approves, never ship AI alone. **Upload hygiene regardless of provider:** downscale ~1024px, strip EXIF, upload cert-only image (not proof-of-ownership composites). Do-not-use: xAI/Grok. |
| **Verification consent (new)** | **Owner must explicitly consent to AI-based verification before uploading** a certificate for verification | Founder requirement 2026-08-29. A consent step/checkbox on the verification upload flow; recorded with the submission. |
| **SEO / public pages (U-13)** | **Public listing/view pages crawlable (SEO) from day one**; owner/dashboard behind auth | Fits listing-first product + Next.js SSR at no extra cost; protects future SEO asset. |
| **Auth tuning (U-12)** | **VC/IC as roles on existing accounts** (not separate accounts); standard Argon2id params; extension API-keys rotated on a schedule; refresh-token lifetime ~30 days | Defaults ratified by founder 2026-08-29. |

> **Backend stack is RATIFIED** (2026-08-28): Kotlin + Spring Boot 3 + PostgreSQL/PostGIS
> (jOOQ + Spring Data JDBC + Flyway) + REST/OpenAPI + Spring Security JWT/RBAC + PostGIS
> geo + S3 images + hosted-AI verification. Deploy target and a few minor flags remain open
> (see Part 2).

### 1.1 Core product model

From the PRD's objective and high-level user stories:

- Users search for halal restaurants by **location** (with current-location
  opt-in) and filter results by **cutting method** (hand-cut / machine-cut),
  **price**, **cuisine** (AND/OR chaining, default OR), **rating**, and
  **distance**.
- Restaurant listings carry halal-relevant attributes: an **alcohol-served**
  flag (whether the establishment serves alcohol) and, **in MVP scope**,
  support for **partial-halal** restaurants (whole-restaurant vs partial
  status). These are related trust/decision attributes and likely filters.
- Restaurant listings support a **verification tag**: a listing starts
  **unverified**; a restaurant owner can "claim" it and, once verified, gains a
  **Verified** status and control over the listing.
- Verification is assisted by an **image-recognition model** that analyzes a
  halal certificate, with a human **Verification Committee** reviewing in the
  early stages.
- Users can **view** a restaurant (address, phone, hours, photo, Google/Yelp
  reviews, and posted certification for verified listings), **favorite**
  restaurants, submit **crowd-sourced edits**, add images, create accounts, and
  report issues.
- **Browser extensions** for Google and Yelp surface the platform's
  listing/verification status on those sites, keyed by **address** matching.

### 1.2 Domain model (agreed — from the PRD PlantUML)

The PRD defines the following domain model. This is the agreed conceptual
architecture; it is **independent of any technology stack**.

- **User** { email, password }
  - **RestaurantOwner** (is-a User)
    - **VerifiedRestaurantOwner** (is-a RestaurantOwner)
- **Restaurant** — has 1..* **HalalCertification**
- **HalalCertification** — verified by **VerificationCommittee** (1 committee ↔
  0..* certifications)
- **RestaurantListing** — represents 1 Restaurant
  - **VerifiedRestaurantListing** (is-a RestaurantListing) — modified by
    VerifiedRestaurantOwner
  - **UnverifiedRestaurantListing** (is-a RestaurantListing) — created/modified
    by User; sends restaurant edits to VerificationCommittee
- **RestaurantListings** — contains 0..* RestaurantListing
- **Filter** { criteria } — used by **SearchResult** (0..* filters)
- **SearchResult** — filters from RestaurantListings

Committees: **VerificationCommittee** (verifications, edits) and
**IssuesCommittee** (user-reported issues).

### 1.3 Personas & states

U = User (not logged in / logged in), RO = Restaurant Owner,
VRO = Verified Restaurant Owner, VC = Verification Committee,
IC = Issues Committee, ET = Extension Trigger. (Full table in README.md.)

### 1.4 Engineering process (agreed — from Shortcut "Development Process" doc)

Per feature, in order:

1. Develop User Story
2. Develop Use Case
3. Update Use Case Diagram
4. **TDD for the backend server**
5. Front-end development

Implications for the architecture:

- **Backend is test-driven.** Tests are written first and are a first-class
  concern.
- Requirements and use cases are authored in Shortcut **before** implementation
  begins on a substantial feature.

### 1.5 External services referenced in the PRD

These services appear in the PRD/use cases as integrations. Agreed choices are
noted; genuinely open items are flagged in Part 2.

- Google Maps API — location search/autocomplete and current location (agreed as
  the geo entry; provider/billing details open, see U-09).
- Google Business / Yelp — ratings and reviews for the View Restaurant use case
  (integration approach open, see U-10).
- Verification image analysis — replaced by the **hosted-AI vision API** decision
  (see 1.0 "Verification (hosted AI)" and U-11). The PRD's "image-recognition
  model" is no longer an in-house model we train.

---

## Part 2 — Unresolved decisions (NOT yet agreed)

The following are **open decisions**. Do not assume any of them is settled.
They need to be resolved (with input from the relevant specialists and the
founder) before implementation of substantial features. Until resolved, treat
them as **unknowns**, not as a chosen architecture.

> The backend stack (U-01…U-07) and hosting/geo/reviews/verification/auth/SEO
> (U-08…U-13) are now **resolved** — see the Agreed section 1.0. Remaining open
> items below.

| ID | Decision | Notes / open questions |
|----|----------|------------------------|
| U-14 | **Partial-halal semantics** | **Confirmed IN MVP scope by founder (2026-08-29)** — the platform should handle partial-halal restaurants thoughtfully (not deferred). Exact modeling still to design: how the verification tag relates to whole-restaurant vs partial status. See product questions below. |
| U-15 | **Alcohol-served attribute** | **New founder requirement (2026-08-29):** whether alcohol is served in the establishment is a listing attribute and likely a filter — an establishment serving alcohol is halal-relevant. Field + filter semantics to be designed. |

### Open product/requirements questions (from PRD "Open Questions" & "Edge Cases")

These are **product** questions, not architecture, but they can constrain the
architecture. They remain unresolved in Shortcut:

- Should **kosher** restaurants be listed?
- Should the platform broaden beyond restaurants (e.g. meat shops)?
- How to model **partial-halal** restaurants / partially halal menus — now **in
  MVP scope** (founder 2026-08-29). The verification tag currently implies
  whole-restaurant status; partial-halal needs its own representation (field /
  tag / filter semantics). Related: the new **alcohol-served** attribute (U-15).
- What happens when a halal **certification expires or is revoked** after
  verification? (Show expiry date; revocation handling remains an open flag —
  founder 2026-08-29.)
- (Resolved in PRD as **deferred**): menu-item entries and analytics
  date-range customizability.

---

## Conventions

- This document should be updated **only** when an architecture decision is
  actually agreed (recorded in Shortcut and/or confirmed by the founder /
  engineering manager). Move items from Part 2 to Part 1 as they are resolved,
  with a note of when and where they were agreed.
- Mark unresolved items explicitly; never silently assume them.
