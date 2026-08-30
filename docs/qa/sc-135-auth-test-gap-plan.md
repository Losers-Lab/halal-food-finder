# sc-135 — Agreed Auth Test-Gap Plan (ranked)

**Author:** Yusuf (QA) · **Date:** 2026-08-30 · **Story:** Shortcut sc-135
**Standard:** `docs/qa/auth-qa-assessment-and-testing-conventions.md` (ratified as the project testing standard by this story)
**Status:** Approved test list — implementation is intentionally **NOT** done here; tasks below are assigned to Hamza/Maryam and dispatched separately by Adnan.

---

## 0. Purpose

This is the agreed, **ranking-ordered test list** called for by sc-135. It closes the top authentication
coverage gaps ranked in the conventions assessment (Part A), **reconciled with the code as of `main`**
(2026-08-30). The assessment predates sc-131 (JWT resource server), sc-132 (server-side logout),
sc-133 (HttpOnly refresh cookie), sc-134 (security headers) — so several originally-ranked gaps are
already closed at implementation+test level. This plan records only what *remains* on the current tree,
so Hamza/Maryam don't re-implement covered work.

Every item carries an AC→test traceability (per conventions §B.3) and a **"expected to fail today"**
flag where the test should currently be red — those are **known defects**, not just missing coverage.

---

## 1. Status ledger — the four top gaps vs. today's tree

| Ranked gap (assessment) | Current state on `main` | Remaining work |
|---|---|---|
| **R5/R6 — login & signup page component tests** | Signup page: 409 path only (`signup/page.test.tsx`). **Login page: zero tests** (`login/page.tsx` has no test file). | **Gap 1 + Gap 2** — nearly all of it remains |
| **R1 — JWT expiry/tamper tests** | HTTP-edge coverage **already landed** in sc-131 `ResourceServerSecurityTest`: unauthenticated, valid, expired, tampered, wrong-issuer, missing-role, malformed, public-route → generic 401. | **Gap 3** — issuer-unit + expiry-boundary only |
| **R7 — unique-constraint race test** | `uk_users_email UNIQUE (email)` now exists (V1 migration). Sequential 409 covered by `SignupEndpointTest`. **Race path unhandled** → see **Defect 4**. | **Gap 4** — migration-constraint test + concurrency test |
| **R8/R10 variant — `client.ts` JSON.parse guard** | `client.ts:70` does `const data = text ? JSON.parse(text) : null;` — **unguarded**. Non-JSON body → raw `SyntaxError` escapes to the UI. Untested. | **Gap 5** — parse guard + test |

---

## 2. The agreed, ranked test list

### Gap 1 — Login page component tests — **Maryam** — HIGH

AC source: sc-40 "changes the User state to be logged in"; error copy per `auth-screens.md`.
New file: `frontend/src/app/login/page.test.tsx` (mirror the existing `signup/page.test.tsx`
mocking pattern: mock `next/navigation` `useRouter`/`useSearchParams`, `next/link`, and `api.login`).

| # | Test | AC → | Expected today |
|---|---|---|---|
| 1.1 | Success: `api.login` resolves → `signIn(auth, email)` called and `router.push("/")` | sc-40 logged-in state | PASS (locked) |
| 1.2 | `?created=1` query renders the "Account created. Log in to continue." success notice | sc-39 post-signup UX | PASS |
| 1.3 | `invalid_credentials` → single "Incorrect email or password." message; **password cleared + refocused**; **email input preserved** (anti-enumeration: never reveal which field failed) | sc-40 error branch | PASS |
| 1.4 | Generic server error (`ApiError` other than `invalid_credentials`, or raw error) → "Something went wrong. Please try again." banner | sc-40 error branch | PASS |
| 1.5 | Client-side schema: empty password → field error, no network call | auth-screens.md | PASS |
| 1.6 | Submission disabled state: button shows "Logging in…", inputs disabled while in flight | UX loading state | FAIL (currently untested — no bug proven, just coverage) |
| 1.7 | Footer "Sign up" link → `/signup`; "Forgot password?" link present | auth-screens.md | PASS |

> Rationale for HIGH: login is the primary conversion action and its UI is currently the **only**
> completely-untested surface. A regression here ships silently.

### Gap 2 — Signup page success + remaining error paths — **Maryam** — HIGH

AC source: sc-39; extend existing `frontend/src/app/signup/page.test.tsx` (which only covers 409).

| # | Test | AC → | Expected today |
|---|---|---|---|
| 2.1 | Valid submit → `router.push("/login?created=1")` | sc-39 "account created" flow | PASS |
| 2.2 | `weak_password` 422 → backend `detail` rendered as the **password** field error, wired via `aria-describedby` | sc-39 field-error contract | PASS |
| 2.3 | `weak_password` with **empty** backend detail → fallback copy "Password is too weak. Use at least 8 characters." | sc-39 field-error contract | PASS |
| 2.4 | Generic error (network / non-ApiError) → "Something went wrong. Please try again." banner | sc-39 generic fallback | PASS |
| 2.5 | Submission disabled state: "Creating account…", inputs disabled in flight | UX loading state | FAIL (untested — coverage) |
| 2.6 | Client-side schema: malformed email, password shorter than 8 → field errors, no network call | schemas.test.ts parity at page level | PASS |

### Gap 3 — JWT: issuer unit + expiry-boundary tests — **Hamza** — MEDIUM-HIGH

sc-131 already proves the **HTTP edge** (expired/tampered/wrong-issuer/missing-role/malformed → 401,
valid → 200) in `ResourceServerSecurityTest`. Remaining:

| # | Test | AC → | Expected today |
|---|---|---|---|
| 3.1 | **New `JwtTokenIssuerTest`** (bootstrap unit, no Spring): issued JWT carries `sub,email,role,iss,iat` claims and `exp == iat + accessTokenTtl` exactly; `SessionTokens.accessTokenExpiresInSeconds == ttl`; token is verifiably-signed RS256 (not plaintext); refresh token is a 256-bit URL-safe base64 string, unique per issuance | sc-131 token spec | PASS (issuer currently has zero dedicated tests — coverage) |
| 3.2 | **Expiry boundary** in `ResourceServerSecurityTest`: token with `exp` a few seconds **before** `now` rejected; token with `exp` just **after** `now` accepted under the configured clock-skew; a token expired by 0s (exp == now) rejected | sc-131 deny-by-default | PASS-uncertain (no skew boundary pinned today — verify `ResourceServerSecurityConfig` clock-skew) |

### Gap 4 — Unique-constraint race (TOCTOU) — **Hamza** — MEDIUM-HIGH **⚠ DEFECT**

`CreateAccount` is check-then-insert (`findByEmail` → `save`). `uk_users_email` is the correctness
backstop for the lost race, **but nothing maps the constraint violation to 409**. Consequence:

- **Defect 4a (backend):** a duplicate that slips past `findByEmail` raises an uncaught
  `DataIntegrityViolationException` on `save` → **HTTP 500**, not the 409 the sc-39 contract promises.
  No `@ControllerAdvice` or `SignupController` handler catches it (verified — only
  `EmailAlreadyExistsException`/`WeakPasswordException`/`IllegalArgumentException` are handled).
  **Fix required: catch the constraint violation in the signup path and map it to
  `email_already_exists` 409.** *Omar to adjudicate severity (correctness/data-integrity).*

| # | Test | AC → | Expected today |
|---|---|---|---|
| 4.1 | **Migration test** (persistence adapter): assert `users` has a UNIQUE constraint/index `uk_users_email` (via `information_schema`), proving the DB backstop exists under Flyway | sc-39 exactly-one-row | PASS |
| 4.2 | **Concurrency/race test** (bootstrap E2E): two concurrent signups with the same email → one `201` + one `409` (or both `409`), and **exactly one** `users` row persists | sc-39 exactly-one-row | **FAIL → red until Defect 4a fixed** |

### Gap 5 — `client.ts` JSON.parse guard — **Maryam** — MEDIUM **⚠ DEFECT**

- **Defect 5a (frontend):** `client.ts:70` `const data = text ? JSON.parse(text) : null;` is unguarded.
  A non-JSON response body (malformed JSON, a reverse-proxy `502` HTML page, a whitespace/padded
  body) throws a raw `SyntaxError` that escapes the `request()` helper as an untyped error and breaks
  the UI contract (which expects `ApiError`/`network_error`). `session.ts`'s `readIdentityHint` already
  guards *its* parse — the API client does not.
  **Fix required: wrap the response parse in try/catch; on failure treat as a decorated error
  (malformed-body → `ApiError(status, "invalid_input")`, or `network_error` for 2xx).**

| # | Test | AC → | Expected today |
|---|---|---|---|
| 5.1 | `client.test.ts`: response with a **non-JSON** body on an error status → rejects with `ApiError` (never a bare `SyntaxError`) | API client contract | **FAIL → red until Defect 5a fixed** |
| 5.2 | Response with a non-JSON body on a 2xx status → normalized error, not a `SyntaxError` escaping to the page | API client contract | **FAIL → red until Defect 5a fixed** |
| 5.3 | Empty body (existing `204` path for logout) keeps resolving to `undefined` — regression lock | sc-133 logout | PASS |
| 5.4 | Existing error-mapping cases (409/422/401) stay green — contract lock | API client contract | PASS |

### Gap 6 — Evidence tooling: `frontend-test.sh` doesn't run unit tests — tooling — MEDIUM

- **Defect 6a (tooling):** `scripts/frontend-test.sh` runs `npm ci && npm run build`
  (`next build`), which **never executes the vitest suite** (`npm test`). The project's only sanctioned
  frontend green-run therefore proves the frontend *builds* but not that its unit tests pass — the
  convention's §B.5 "green `./scripts/frontend-test.sh`" cannot be honestly satisfied today.
  **Fix required: extend `scripts/frontend-test.sh` to run `npm ci && npm run build && npm test`**
  (or add a `test` mode), so the cited "green chain" covers the unit suite.
  Owned by: whichever profile owns `scripts/` (backend = Hamza; confirm with Adnan).
- Correlation: **Gap 1 & 2 tests are already running green in-container in this task's evidence**
  (see §4) — this gap is about the *sanctioned* entry point, not about the tests failing.

---

## 3. AC → test traceability (sc-39 / sc-40)

| Acceptance criterion | Tests (existing) | Tests (this plan) |
|---|---|---|
| sc-39 "verifies … the password is strong enough" | `PasswordPolicyTest`, `SignupEndpointTest` (422) | 2.2, 2.3 |
| sc-39 duplicate email → 409, exactly one row | `SignupEndpointTest`, `CreateAccountTest` (sequential) | **4.1, 4.2 (race)** |
| sc-39 account-created → redirect to log in | — | 2.1 |
| sc-39 input validation / error copy | `schemas.test.ts`, signup 409 path | 2.6, 2.4 |
| sc-40 "changes the User state to be logged in" | `session.test.ts` (signIn/silent-refresh/restore/401-clear), `client.test.ts` | 1.1, 1.3 |
| sc-40 anti-enumeration (wrong vs unknown) | backend `AuthenticateAccountTest`, `LoginEndpointTest` | 1.3 |
| sc-40 UI error branches | — | 1.4, 1.5, 1.6 |
| (retained, not in top-4) sc-131 token expiry/tamper edge | `ResourceServerSecurityTest` | 3.1, 3.2 |

---

## 4. Acceptance evidence for this plan (cited, containerized)

Baseline green runs on `wt/sc-135-qa-conventions` (docs-only change; test code untouched):

- **Backend:** `./scripts/backend-test.sh` → Gradle `build` + full Kotest/Testcontainers-PostGIS
  suite, run in `eclipse-temurin:21-jdk`. Result: **BUILD SUCCESSFUL in 2m 15s**, **71 tests PASSED**
  across `domain`, `application`, `persistence`, `bootstrap` (incl. `SignupEndpointTest`,
  `LoginEndpointTest`, `LogoutEndpointTest`, `ResourceServerSecurityTest`). This run was
  executed from an isolated repo worktree because the long-running `hff-backend` dev server holds
  the shared Gradle file-hash cache lock in the main checkout; the test code itself is identical.
  Log: `/tmp/backend-green-worktree.log` (exit 0).
- **Frontend unit suite:** `npm ci && npx vitest run` in `node:22`. Result: **4 files / 27 tests
  passed** (`session.test.ts`, `client.test.ts`, `schemas.test.ts`, `signup/page.test.tsx`).
  Run directly (not via `frontend-test.sh`) because `frontend-test.sh` only runs `next build` and
  does **not** execute the vitest suite — see Gap 6 / Defect 6a. Log: `/tmp/frontend-unit-baseline.log`
  (exit 0).

The plan is docs-only; the test suite result on this branch is identical to `main`.

---

## 5. Proposed implementation dispatch (for Adnan)

| Task | Owner | Covers | Red-on-arrival? |
|---|---|---|---|
| Auth login/signup component tests | Maryam | Gaps 1, 2 | Gap 1 items fail? no — only untested; expect all green as specs are met |
| JWT issuer/boundary tests | Hamza | Gap 3 | No (coverage only) |
| Signup race: constraint → 409 + race test | Hamza | Gap 4 + **Defect 4a** (backend handler) | **Yes — Defect 4a** |
| `client.ts` JSON.parse guard + tests | Maryam | Gap 5 + **Defect 5a** | **Yes — Defect 5a** |
| `frontend-test.sh` run unit suite | scripts owner (Hamza/confirm) | Gap 6 / **Defect 6a** | Yes |

Each implementation task **must** be merged with a PR into `main` and its green run cited per the
conventions §B.3 before its story is marked Done. No code from this plan was implemented here — by
design (QA does not implement test gaps; Hamza/Maryam do).