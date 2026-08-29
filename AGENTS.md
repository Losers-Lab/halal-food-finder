# AGENTS.md — Halal Food Finder Engineering Rules & Context

This file provides project-wide engineering rules and context. It is intended to
be read by both human engineers and AI agents working in this repository (it is
portable across Hermes, Claude Code, Codex, etc.).

> Bismillah. This is a Muslim software project. Treat the accuracy, integrity,
> and verification of halal information as a communal trust (amanah). Report
> status, progress, and defects honestly — never exaggerate or hide known
> issues.

## Sources of truth

- **Shortcut** (`halal-food-finder` workspace) is the source of truth for
  **product requirements, roadmap, epics, stories, iterations, and project
  management.** The most relevant Shortcut documents:
  - *Product Requirements Doc* (PRD) — the master requirements document
  - *Use Case Brief Descriptions* — persona + use-case detail per story
  - *Use Case Diagram* — actor/use-case model
  - *User Story Prioritization* — **priority = Backlog order filtered by the
    "Product" skill set**
  - *Use Case Prioritization* — **priority = Backlog order filtered by the
    "Development Process" skill set**
  - *Development Process* — the team's workflow (see below)
- **This Git repository** is the source of truth for **implementation**.

**Do not invent product requirements.** When a requirement is not known, consult
Shortcut first. When it is genuinely unresolved, mark it as unresolved rather
than inventing an answer, and flag it to the product/engineering manager.

## Development process (from Shortcut doc "Development Process")

The team's agreed workflow, per feature, in order:

1. **Develop User Story**
2. **Develop Use Case**
3. **Update Use Case Diagram**
4. **TDD for the backend server** (test-driven development; tests first)
5. **Front-end development**

Follow this order. Backend work is test-driven; do not write backend
implementation before its failing tests unless explicitly directed otherwise.

## Product at a glance

Halal Food Finder helps Muslims find halal food, with a granular
**hand-cut vs machine-cut** filter and a **formal verification process** for
restaurants. Core pillars (from the PRD):

- Searchable restaurant listings by location; filters for cutting method,
  price, cuisine (with AND/OR chaining), rating, and distance.
- Listing-first model: anyone can add a restaurant (starts **unverified**);
  owners claim it by submitting proof of ownership + a photo of their halal
  certification.
- Verification assisted by an **image-recognition model**, reviewed by a human
  **Verification Committee** in early stages; target ~1 week turnaround.
- **Verified certification display** on restaurant pages.
- **Browser extensions** for Google and Yelp surfacing our listing/verification
  status.

### Personas

U = User, RO = Restaurant Owner, VRO = Verified Restaurant Owner,
VC = Verification Committee, IC = Issues Committee, ET = Extension Trigger.
See README.md for the full table.

### Edge cases (documented in PRD — requirements may depend on these)

- Multi-cuisine filtering needs AND/OR chaining (default logic: **OR**).
- Partial-halal restaurants/handling of partially halal menus is unresolved.
- Certification expiry/revocation handling is unresolved.
- Menu items and analytics date-range customizability are deferred features.

## Architecture

See [ARCHITECTURE.md](./ARCHITECTURE.md). The domain model (User /
RestaurantOwner / VerifiedRestaurantOwner, Restaurant ↔ HalalCertification,
RestaurantListing → Verified/Unverified, committee review) is agreed. The
**technical stack is NOT yet decided** — see the "Unresolved decisions" section
of ARCHITECTURE.md. Do not assume a stack; propose/confirm it before
introducing frameworks, languages, or databases.

## Engineering ground rules

- **Work in small, reviewable increments.** Prefer clear tasks with explicit
  acceptance criteria tied to Shortcut stories.
- **TDD for the backend** (per the Development Process doc). Keep tests runnable
  and green before declaring work done.
- **No invented requirements or architecture.** Derive from Shortcut / agreed
  docs; mark unknowns as unknown.
- **Verification before claiming done:** a change is only "done" when its
  acceptance criteria are verified (tests pass, behavior confirmed), not when
  code merely compiles.
- **Commit hygiene:** keep commits focused and logically separated (this
  repository started with a docs-only commit). Write clear commit messages.
- **Honesty in status reporting (amanah):** report real results; surface
  blockers and unknowns rather than papering over them.

## Contact / coordination

- Product & Engineering Manager: Adnan (coordinates requirements, engineering,
  QA, security review, and escalation to the founder).
- Specialist team: Fatima (design/UX), Maryam (frontend), Hamza (backend/DB),
  Aisha (research/data), Yusuf (QA), Omar (security/code review).

When a decision materially affects architecture or the product, consult the
appropriate specialist and involve the founder rather than deciding silently.
