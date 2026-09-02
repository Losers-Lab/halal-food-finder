# Tahir's List

> بِسْمِ اللهِ الرَّحْمٰنِ الرَّحِيْمِ

A platform to help Muslims reliably find halal food, with a granular **hand-cut**
filter (an extra on/off switch — there is no machine-cut concept) and a formal
verification process for restaurants.

## Status

**Active development — Milestone 1 in progress.** The repo contains backend +
frontend implementations (see [ARCHITECTURE.md](./ARCHITECTURE.md)).

- The product requirements and documentation live in **Shortcut**
  (`app.shortcut.com/halal-food-finder`), which is the source of truth for
  product and project management.
- The technical stack is **ratified**: Kotlin/Spring Boot 3 + PostgreSQL/PostGIS
  backend, Next.js (TypeScript) frontend. See
  [ARCHITECTURE.md](./ARCHITECTURE.md) for the resolved decisions and what is
  still open.

## What this product is

Tahir's List is designed to solve a real problem for Muslims: verifying
that food is genuinely halal is hard, especially outside Muslim-majority
countries, and whether meat is **hand-cut** (Zabiha) matters to many people.
The product's core ideas (from the Shortcut PRD):

1. **Discoverable, searchable listings** of restaurants, searchable by
   location (Google Maps) with filters for food-cutting method, price, cuisine
   (AND/OR chaining), rating, and distance.
2. **Trust through verification** — restaurants can be listed immediately
   (unverified) and then "claimed" by their owners, who submit proof of
   ownership plus a photo of their halal certification. Verification is assisted
   by an image-recognition model and reviewed by a human Verification Committee
   in the early stages.
3. **Verified certification display** — users can see a posted image of the
   certification on verified restaurants' pages.
4. **Browser extensions** for Google and Yelp that surface our platform's
   listing + verification status for restaurants encountered on those sites.

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the agreed domain model and the
list of open decisions, and Shortcut for the authoritative requirements.

## Personas

Defined in the Shortcut use-case documentation:

| Prefix | Persona | Description |
|--------|---------|-------------|
| U  | User | A person wanting to find food to eat. States: not logged in / logged in |
| RO | Restaurant Owner | A user who owns a restaurant, not yet registered as such |
| VRO| Verified Restaurant Owner | An owner verified as the owner of a listing |
| VC | Verification Committee | Reviews verification requests |
| IC | Issues Committee | Reviews reported issues |
| ET | Extension Trigger | Trigger listened by the browser extensions |

## Documentation index

| File | Purpose |
|------|---------|
| [README.md](./README.md) | This file — overview and status |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Agreed architecture, domain model, open decisions |
| [AGENTS.md](./AGENTS.md) | Project-wide engineering rules and context (for agents and humans) |

## Source of truth

- **Product & project management:** Shortcut (`halal-food-finder` workspace),
  including the Product Requirements Doc, Use Case Brief Descriptions, Use Case
  Diagram, and the two Prioritization documents.
- **Implementation:** this Git repository.
