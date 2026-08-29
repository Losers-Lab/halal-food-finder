# ARCHITECTURE.md — Halal Food Finder

This document describes the architecture of Halal Food Finder. It is split into
two distinct parts:

1. **Agreed** — the domain model and engineering process we have actually agreed
   upon (derived from the Shortcut Product Requirements Doc and Development
   Process doc).
2. **Unresolved** — technical decisions that are **not yet agreed**. These are
   deliberately left open and marked clearly; **do not treat them as decided.**

> Status: **Greenfield / DRAFT.** This document accompanies the first commit of
> an empty repository. Nothing here implies implementation exists.

---

## Part 1 — Agreed architecture

### 1.1 Core product model

From the PRD's objective and high-level user stories:

- Users search for halal restaurants by **location** (with current-location
  opt-in) and filter results by **cutting method** (hand-cut / machine-cut),
  **price**, **cuisine** (AND/OR chaining, default OR), **rating**, and
  **distance**.
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

These services appear in the PRD/use cases as integrations, but **none have been
agreed as final implementation choices** (see Unresolved):

- Google Maps API — location search/autocomplete and current location.
- Google Business / Yelp — ratings and reviews for the View Restaurant use case.
- Image-recognition model — automated halal-certificate verification (with human
  committee oversight).

---

## Part 2 — Unresolved decisions (NOT yet agreed)

The following are **open decisions**. Do not assume any of them is settled.
They need to be resolved (with input from the relevant specialists and the
founder) before implementation of substantial features. Until resolved, treat
them as **unknowns**, not as a chosen architecture.

| ID | Decision | Notes / open questions |
|----|----------|------------------------|
| U-01 | **Backend language & framework** | Not specified anywhere in Shortcut. Needs a recommendation (e.g. proposal from backend engineering). |
| U-02 | **Frontend framework / tech** | Not specified. Design (Figma) exists, but no UI tech chosen. |
| U-03 | **Database & data layer** | Not specified. Must support the domain model and geo/distance filtering. |
| U-04 | **Authentication & authorization model** | "Create Account / Log In" exists as a story, but the mechanism, session/token model, and per-persona access control are not specified. |
| U-05 | **Image-recognition model & ML pipeline** | The PRD describes the goal (certificate verification) but no model, data source, training, or accuracy pipeline is defined (success target: ≥90% accuracy within 6 months of launch). |
| U-06 | **Hosting / deployment / infrastructure** | Not specified. |
| U-07 | **Google Maps integration details** | API provider, billing, geocoding/autocomplete approach not specified. |
| U-08 | **Google/Yelp data integration** | Reviews/ratings sourcing not specified (API vs. scraping vs. manual). |
| U-09 | **Browser extension platform/build** | Chrome/Firefox/etc., and extension framework not specified. |

### Open product/requirements questions (from PRD "Open Questions" & "Edge Cases")

These are **product** questions, not architecture, but they can constrain the
architecture. They remain unresolved in Shortcut:

- Should **kosher** restaurants be listed?
- Should the platform broaden beyond restaurants (e.g. meat shops)?
- How to handle **partial-halal** restaurants / partially halal menus (the
  verification tag currently implies whole-restaurant status)?
- What happens when a halal **certification expires or is revoked** after
  verification?
- (Resolved in PRD as **deferred**): menu-item entries and analytics
  date-range customizability.

---

## Conventions

- This document should be updated **only** when an architecture decision is
  actually agreed (recorded in Shortcut and/or confirmed by the founder /
  engineering manager). Move items from Part 2 to Part 1 as they are resolved,
  with a note of when and where they were agreed.
- Mark unresolved items explicitly; never silently assume them.
