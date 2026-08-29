# QA Assessment: Auth/Accounts (Sprint 1) + Proposed Testing Conventions

**Author:** Yusuf (QA) · **Date:** 2026-08-29 · **Scope:** Create Account (sc-39), Log In (sc-40), frontend Sign Up / Log In UI (sc-39/sc-40 UI work, design tokens sc-116)
**Basis:** Read all test sources on `main`; Shortcut story records for sc-39/sc-40. No application code modified.

---

## Part A — What is tested today, and gaps ranked by risk

### A.1 What IS tested (credit where due — the base is genuinely solid)

**Backend (Kotest + MockK + Testcontainers/PostGIS, layered per architecture):**

| Layer | Tests | Highlights |
|---|---|---|
| Domain | `EmailTest`, `AccountTest`, `RoleTest` | email normalization, malformed-email rejection, default USER role, exactly-6 MVP roles |
| Application (use case, mocked ports) | `CreateAccountTest` (5), `AuthenticateAccountTest` (4), `RefreshSessionTest` (5), `PasswordPolicyTest` (3) | duplicate email → no save; weak password → no hash/save; password never persisted plaintext; generic `InvalidCredentialsException` for both unknown-email and wrong-password (**anti-enumeration asserted at both layers**); email normalization; refresh **rotation with single-use revocation**, expired-token rejection + revocation-on-replay-attempt, orphaned-account rejection |
| Bootstrap adapters | `Argon2idPasswordHasherTest` (4) | salted, non-plaintext, wrong-password fails |
| Bootstrap E2E (real DB + Flyway + HTTP) | `SignupEndpointTest` (3), `LoginEndpointTest` (7) | 201 + hashed row in DB; **409 duplicate + exactly-one row**; 422 weak password + zero rows; 401 generic for wrong password AND unknown email; missing fields → 400; JWT payload embeds `sub` + `role`; refresh token stored only as SHA-256 hash; **refresh rotation E2E incl. replay of old token → 401**; garbage refresh → 401; empty refresh → 400 |
| Persistence | `JdbcAccountRepositoryTest` (4), `JdbcRefreshTokenStoreTest` | Flyway V1/V2 migrations, round-trip by canonical email, hash-only token storage |

**Frontend (Vitest + React Testing Library):**
- `schemas.test.ts` — Zod validation + exact error copy for signup (email, min-8 password) and login (empty password).
- `client.test.ts` — API client maps 409→`email_already_exists`, 422→`weak_password` (with backend detail), 401→`invalid_credentials`, network failure →`network_error`, ApiError type.
- `signup/page.test.tsx` — **only the duplicate-email path of the Sign Up page**: error copy with "logging in" link, clears on edit, aria-invalid/aria-describedby wiring.

### A.2 Gaps, ranked by risk

**R1 — HIGH · No access-token expiry / token-authentication tests at all.** `JwtTokenIssuer` has zero dedicated tests. Nothing tests that an *expired* access token is rejected on any protected endpoint (there is no protected endpoint yet, but the moment Favorites lands, this is untested). Also nothing tests JWT signature validation / tampered-token rejection. **Close this before sc-41+ (Favorites) consumes tokens.**

**R2 — HIGH · Frontend session handling (AuthProvider) is completely untested.** `signIn`/`signOut`, localStorage persistence, `expiresAt` derivation, corrupted-JSON recovery, and *token refresh on expiry* have no tests — and there is **no refresh-trigger in the client code at all**: `signIn` stores `expiresAt`, but nothing ever calls `/v1/auth/refresh` from the frontend when the access token lapses. The backend's excellent refresh flow is dead code from the browser's perspective. This is a product-impacting gap (user is silently logged out after ~15 min) *and* an untested one.

**R3 — HIGH · Rate limiting / brute-force protection: nonexistent and untested.** No lockout, no throttling, no delay on login failure. Acceptable to defer for MVP, but it must be a **tracked, deliberate decision**, not an accident — and once added it needs load-ish tests.

**R4 — MEDIUM-HIGH · Password policy is weak and inconsistently asserted.** Backend `PasswordPolicy` only enforces length ≥ 8 + non-blank; "password123" passes. Story sc-39 says "the system verifies the password is strong enough" — is 8 chars the agreed definition? No test pins the boundary (exactly 8 accepted, 7 rejected at HTTP level), no maximum-length guard (bcrypt-style truncation / DoS via huge passwords with Argon2), no common-password list. Also frontend/backend messages differ subtly ("at least 8 characters" copies are duplicated in three places with no contract test keeping them in sync).

**R5 — MEDIUM · Log In UI page is entirely untested.** `login/page.tsx` has no test file (Sign Up has one for a single error path). Untested: successful login → `signIn` + redirect; credential error copy ("Incorrect email or password."), password-cleared-and-refocused behavior, `?created=1` success banner, loading/disabled states, generic server-error banner.

**R6 — MEDIUM · Sign Up page success path untested.** Only the 409 path exists. No test: valid submit → `router.push("/login?created=1")`, 422 weak-password surfaces the backend detail as a field error, network failure shows the generic banner.

**R7 — MEDIUM · Signup race condition (TOCTOU).** Uniqueness is check-then-insert (`findByEmail` then `save`). Two concurrent signups with the same email can both pass the check; the test only proves the sequential case. Need: unique DB constraint + a concurrency test (or an explicit decision that the unique index makes 409-on-conflict safe). I did not see the V1 migration asserting a unique constraint in tests.

**R8 — MEDIUM · Refresh-token edge cases.** Concurrent refresh with the same token (double-spend window), refresh token expiry *boundary* (29 vs 31 days asserted loosely at use-case level only), no logout/refresh-token-revocation endpoint at all (localStorage `signOut` clears the client but the server-side refresh token stays live — session can't actually be invalidated server-side).

**R9 — LOW · Input hardening at the HTTP edge.** No tests for malformed JSON, oversized payloads, unicode/homoglyph emails (`Ünïcode@…`), very long emails (DB column limit), nulls in JSON fields, or content-type enforcement.

**R10 — LOW · No frontend-backend contract test.** `client.test.ts` mocks fetch; nothing proves the mocked shapes match the real OpenAPI schema (`schema.d.ts` exists but is only compile-time). A stale mock would let the suite stay green while the UI breaks.

### A.3 Sprint 1 acceptance criteria — under-covered items

From the sc-39/sc-40 story descriptions and completion comments:

1. **sc-39 "verifies … the password is strong enough"** — only length≥8 is defined; the AC's definition of "strong enough" is itself unresolved and untested at boundaries. Under-covered.
2. **sc-40 "changes the User state to be logged in"** — the persisted session exists only in browser localStorage; refresh-silently, server-side session validation, and logout are not implemented/tested. The "logged in state" AC is only half-delivered (token issuance ✔, session lifecycle ✘) — flagged honestly in the story comment? No; the comment says Completed without this caveat. **This should be reopened or spun into a follow-up story.**
3. **Both stories: "Frontend error message" branches** in the use-case diagrams — only the 409 branch of Sign Up is UI-tested; login UI and all other error branches are untested.
4. **Both story comments note honestly:** PM has not manually exercised the UI end-to-end in a browser. No automated E2E (Playwright/ Cypress) exists either, so the *integrated* frontend↔backend flow is verified by nobody. This is the biggest evidence gap for "done."

---

## Part B — Proposed testing conventions (effective from the start of every feature)

### B.1 Test naming & structure

- **Backend (Kotest FunSpec):** sentences, behavior-focused, no method names: `test("rejects a duplicate email with 409 and persists only one row")`. Follow the existing style — it's good. Structure rules:
  - One spec class per production unit, mirroring the package (`CreateAccount` → `CreateAccountTest`).
  - Arrange–Act–Assert within each test; shared fixtures via small `object XFixture` helpers; no god-fixtures.
  - Each test's doc comment (when present) must name the story it serves (`sc-40: …`).
- **Frontend (Vitest + RTL):** `describe("<Component> — <behavior area> (#story)")` → `it("renders <observable outcome>")`. Query by role/label (a11y-first), assert user-visible outcomes, never internal state. Exact error copy is asserted because copy is part of the spec (auth-screens.md).
- **Error-copy rule:** UI strings that the spec dictates are asserted verbatim; strings that aren't spec'd are asserted loosely.

### B.2 Testing pyramid for this stack

```
        /  E2E  \      Playwright against docker-compose (backend + frontend)
       / Contract \    OpenAPI schema ⇄ frontend client type test (cheap, always-on)
      / API-integ \   Backend bootstrap tests: full Spring context + Testcontainers
     /   PostGIS    \  (HTTP in, DB out, real Flyway). THE primary backend evidence.
    /  Use-case (unit \ MockK-mocked ports. Fast; owns business-rule edge cases.
   /  + component unit \ Pure Kotlin / Vitest+RTL for domain logic & UI components
```

Proportions in practice: ~60% use-case/unit, ~30% API-integration (bootstrap), ~10% E2E. The existing suite already matches this shape — ratify it. Rules:

- **Every backend story** ships: use-case tests (happy + every error branch) **and** at least one bootstrap-level HTTP test proving the status codes/contracts the frontend depends on.
- **Every frontend story** ships: schema/client tests if the API surface changes, component tests for every rendered error/success branch, not just one.
- **E2E (Playwright in a container, no host toolchain)** is required for user-visible journeys (signup → login → see "logged in" state) before a milestone is declared done — per-feature during the milestone is fine.
- **Persistence changes** get a Testcontainers adapter test against real migrations (as today).
- All of it runs only via `./scripts/backend-test.sh` / `./scripts/frontend-test.sh` — no host toolchains, ever.

### B.3 What counts as acceptance evidence for a story

A story is *evidence-complete* when the PR/story comment contains:

1. **Test list** — every test added, each mapped to an acceptance criterion (AC → test traceability, even as a bullet list in the story comment).
2. **Green-run proof** — output (or PM-verified run) of `./scripts/backend-test.sh` and `./scripts/frontend-test.sh` on the merged branch.
3. **Branches-covered statement** — every branch in the story's use-case diagram has a named test or an explicit "deferred, tracked as sc-XXXX".
4. **Manual spot-check** — for UI stories, a screenshot or short note from a human actually clicking the flow (today's missing piece), until E2E exists to replace it.
5. **Honest caveats** — anything not verified, stated (amanah), not buried.

### B.4 QA at story-start

Before implementation begins (state "Ready for Development"):

1. Dev assigns story to QA for a **criteria review**: I check the ACs are testable, unambiguous, and that error branches are enumerated (the sc-39/40 diagrams are a great pattern — keep making them).
2. I flag security-adjacent cases (enumeration, rate limits, token lifetimes, input abuse) that the ACs don't mention, either as ACs or as explicitly deferred tasks.
3. We agree the **test list first** (TDD: for backend, these become the failing tests). No implementation before this list exists.
4. Output: a `## QA criteria review` comment on the story — sign-off recorded in Shortcut, so the review is auditable.

### B.5 Definition of Done (with QA verification)

A story is **Done** when ALL of:

- [ ] Acceptance criteria implemented and each mapped to at least one automated test.
- [ ] Backend TDD respected: tests existed and failed before implementation (Development Process doc).
- [ ] `./scripts/backend-test.sh` **and** `./scripts/frontend-test.sh` green on the final commit (verified in the containerized run, output cited).
- [ ] All branches of the story's use-case diagram tested or explicitly deferred with a tracking story.
- [ ] Error copy per design spec asserted in tests (UI stories).
- [ ] **QA review performed** (Yusuf): criteria review at story-start + a review of the final test list/gap check before moving to Completed; unresolved QA findings block the state change or become tracked follow-ups with PM sign-off.
- [ ] Story comment contains the acceptance evidence of §B.3 (tests, run proof, caveats).
- [ ] For milestone-closing stories only: E2E journey test green in containers.

---

## Part C — Recommended follow-up test tasks for the auth work

Create these (order = priority):

1. **Frontend AuthProvider + session lifecycle tests** — `signIn`/`signOut` store semantics, `expiresAt` derivation, corrupted-storage recovery, and (new implementation, so this is a story not just tests) **silent refresh** calling `/v1/auth/refresh` on access-token expiry. *(Closes R2 — backend refresh flow is currently unreachable from the browser.)*
2. **Login page component tests** — success path (signIn + redirect), `?created=1` banner, credential-error copy + password-clear/refocus, generic error banner, submit disabled states. *(R5)*
3. **Signup page success + weak-password UI tests** — redirect to `/login?created=1`; 422 detail surfaces as the password field error; network failure → banner. *(R6)*
4. **Access-token expiry & tamper tests + a protected-endpoint contract** — expired/tampered JWT rejected with 401 before any endpoint consumes tokens (add with the first protected route, e.g. Favorites). Also unit tests for `JwtTokenIssuer` (claims, TTL). *(R1)*
5. **Pin down the password policy as an explicit decision** — boundary tests (7/8 chars, max length), decide whether "strong enough" means more than length; contract-test that frontend/backend copies agree; add a single shared definition of the policy in the OpenAPI/docs. *(R4)*
6. **Signup uniqueness race test** — assert a unique DB constraint on `users.email` exists (test the migration, not just the use case) and that a lost race still returns 409. *(R7)*
7. **Logout story + tests** — server-side refresh-token revocation endpoint + frontend signOut wired to it; otherwise "logged out" users keep a live session server-side. *(R8, closes the sc-40 "logged in state" AC honestly)*
8. **Rate-limiting decision ticket** — either "deferred deliberately (MVP, tracked)" or implement throttling on `/v1/auth/login` with tests. *(R3 — must be a visible decision, not an omission)*
9. **OpenAPI contract test for the frontend client** — a cheap test that runs the mocked-response shapes through the real `schema.d.ts` types / validates against the served OpenAPI doc, so client mocks can't drift. *(R10)*
10. **Playwright E2E skeleton (containerized)** — signup → login → visible logged-in state, replacing the "PM hasn't clicked it yet" gap with an automated, repeatable journey; runs via `./scripts/` only. *(A.3.4)*
