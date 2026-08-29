# Security Review — Authentication (Sprint 1, `main` @ `de28360`)

**Reviewer:** Omar (Security Engineer) · **Date:** 2026-08-29 · **Scope:** sc-39/sc-40 backend + frontend auth
**Method:** static review (containerized-only host rule) · Claims spot-verified by PM against `main`.

## Part 1 — Findings

| # | Severity | Area | Finding & Evidence | Recommendation |
|---|----------|------|--------------------|----------------|
| 1 | **HIGH** | Authorization | JWTs issued but never verified. No Spring Security dependency, no resource-server filter, no `SecurityFilterChain`, no JWT validation anywhere. Any protected route added later would be unauthenticated by default. (`backend/bootstrap/build.gradle.kts`; `TokenIssuerConfig.kt:22-23` flags it as follow-up.) | Add `spring-boot-starter-oauth2-resource-server`; validate RS256 + `iss` + `exp` + role claim; deny-by-default. **Gate: block protected-endpoint work until this lands.** |
| 2 | **HIGH** | Session revocation | No server-side logout. `RefreshTokenStore` supports revoke-by-token (`JdbcRefreshTokenStore.kt:51-53`) but no endpoint calls it; frontend `signOut` only clears localStorage. 30-day refresh lifetime. | `POST /v1/auth/logout` revoking presented refresh token; `revokeAllForAccount` for compromise. |
| 3 | **HIGH** | Token storage | Refresh token (30-day credential) + JWT in `localStorage` (`AuthProvider.tsx:36,68`). XSS = 30-day hijack; acute with future owner/committee roles. | Move refresh token to `HttpOnly; Secure; SameSite=Lax` cookie via the existing same-origin proxy; access JWT in memory only. **Founder ratified: schedule early in Sprint 2.** |
| 4 | **HIGH** | Brute force | No rate limiting on `/v1/auth/login`, `/signup`, `/refresh`. No refresh-token reuse detection (replayed rotated token returns generic 401 without killing the token family). | Rate-limit gate (IP+email keyed; stricter on login failures). Reuse-detection: replayed rotated token ⇒ revoke all tokens for the account. |
| 5 | **MEDIUM** | Secrets | JWT signing key silently auto-generated if unset (`TokenIssuerConfig.kt:39-46`) — invalidates tokens each restart, fails silently in prod. No committed secrets found; `.env` ignored. | Fail-fast in prod profile without explicit `app.jwt.*`; document key provisioning; add secrets-scan to CI. |
| 6 | **MEDIUM** | Info leakage | `IllegalArgumentException.message` echoed verbatim in all three auth controllers (`SignupController.kt:63-64` etc.). | Whitelist known validation messages; unknown exceptions → generic `invalid_input` + correlated server log. |
| 7 | **MEDIUM** | Enumeration | Signup 409 enables account enumeration; frontend advertises it. **Founder decision (2026-08-29): accept UX-friendly 409 for MVP; revisit with email verification.** | Documented tradeoff; do not change silently. |
| 8 | **MEDIUM** | Headers/transport | No CSP/HSTS/X-Frame-Options on frontend; backend sets none; actuator `health.show-details: always`. | CSP + HSTS + X-Content-Type-Options + frame-ancestors; `show-details: never` outside dev. |
| 9 | LOW | Password hashing | Argon2id, 16 MiB / 2 it / 1 par (near OWASP floor); min length 8, no max, no breach-list. | Parameterize; bump toward 19–32 MiB; max length ~128. Breach-list = founder privacy call. |
| 10 | LOW | Injection | Fully parameterized JDBC; opaque 256-bit SecureRandom refresh tokens, stored SHA-256-hashed. Correct. | Preserve: no string-built SQL ever. |
| 11 | LOW | Frontend robustness | `client.ts:62` unguarded `JSON.parse` on error bodies. | Wrap → generic `network_error` class. |
| 12 | INFO | CORS | Same-origin proxy makes CORS unnecessary today; `NEXT_PUBLIC_API_BASE` could reintroduce cross-origin. Browser extensions (PRD pillar) WILL hit this. | Explicit allow-list CORS when first cross-origin consumer lands; never `*` with credentials. |
| 13 | INFO | Strengths | Anti-enumeration login, UUID PKs, hashed rotating single-use refresh tokens, DB-owned hashing, TDD'd edges. | Preserve via gates. |

## Part 2 — Security gates (ratified posture for Sprint 2 onward)

Security-sensitive change = auth, authz/RBAC, secrets/env, external integrations, user data, trust/verification flows.

**Gate checklist (every security-sensitive PR):**
1. Threat note in PR: what the change allows that wasn't possible before + its controls.
2. Every new endpoint declares public / authenticated / role-gated; authenticated = enforced by verified-JWT filter; roles from `users.role`, server-verified.
3. No secrets in code/tests/fixtures; env config only.
4. Parameterized data access only.
5. Errors fail closed and generically (no raw exception messages) — except the ratified signup-409 tradeoff.
6. Token hygiene: ≥128-bit SecureRandom, hashed server-side, rotated on use, revocable; no long-lived credentials in localStorage (cookie migration pending).
7. Same-origin proxy or explicit CORS allow-list; header review on new public surface.
8. Negative-path tests mandatory (invalid credentials, expired/rotated token, unauthorized role).

**Mandatory Omar review (blocking):** login/signup/refresh/token issuance/verification; RBAC changes; trust/verification committee flow; owner-claim & certification upload; new external integration; JWT key management; migrations touching `users`/`refresh_tokens`/PII; CORS/header policy; new credential storage.
**Self-certification (checklist stated in PR):** features consuming an existing session; UI with no new data flow; behavior-identical refactors.

**CI additions:** secrets scan (gitleaks container); ArchUnit-style "no `ex.message` in ErrorResponse"; OpenAPI spec kept in sync.

## Part 3 — Founder decisions

1. Signup enumeration → **ratified 2026-08-29: UX-friendly 409 for MVP.**
2. Refresh lifetime → **ratified 2026-08-29: 30 days flat.**
3. localStorage→cookie migration → **ratified 2026-08-29: schedule early in Sprint 2.**
4. Password breach-list checks → open (privacy/values call).
5. Rate-limit placement (gateway vs in-app) → open; recommend in-app (Bucket4j) at MVP scale.
