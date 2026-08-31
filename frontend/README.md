# Tahir's List — Frontend

Next.js 16 (App Router, TypeScript strict, Tailwind v4) frontend. Design tokens
live in the Tailwind `@theme` block in `src/app/globals.css` (spec:
`docs/design/tokens.md`).

## Getting started

```bash
npm install
npm run dev        # http://localhost:3000
```

The app needs the backend running (Kotlin/Spring Boot on `http://127.0.0.1:8080`,
see `backend/README.md`). PostGIS + MinIO are required — `docker compose up -d`
in `backend/`.

## API client & typed contract

The API client is type-safe, generated from the backend's committed OpenAPI
spec:

- `src/lib/api/openapi.json` — snapshot of `backend/openapi/v1.json`.
- `src/lib/api/schema.d.ts` — generated TypeScript types (openapi-typescript).
- `src/lib/api/client.ts` — thin typed fetch client (`api.signup` / `api.login`
  / `api.refresh`); throws `ApiError` with the backend's `code` + optional
  `detail`, or `network_error` for transport failures.

Regenerate types after the backend contract changes:

```bash
npm run codegen:api   # must run from frontend/
```

Keep `openapi.json` in sync with `backend/openapi/v1.json` when the API changes.

### Same-origin API proxy

The browser calls the API via Next.js rewrites (`/v1/*` → backend) so all
requests are same-origin and the backend needs no CORS config. Override the
target with the `API_PROXY_TARGET` env var, or point the client directly at an
origin with `NEXT_PUBLIC_API_BASE`.

## Auth (Sign Up sc-39 / Log In sc-40)

- `src/app/signup/page.tsx` — create-account form (Zod + react-hook-form):
  email-uniqueness, weak-password, and inline validation errors; success
  redirects to Log In (`?created=1`).
- `src/app/login/page.tsx` — sign-in form with a single combined
  "Incorrect email or password." alert (anti-enumeration); clears + refocuses
  the password field on bad credentials; success persists the session and goes
  home.
- `src/lib/auth/AuthProvider.tsx` — session store (localStorage-backed,
  `useSyncExternalStore`); `useAuth()` exposes `session` / `signIn` / `signOut`.
- `src/components/auth/*` — shared form primitives (Button, Field,
  PasswordField, Alert, AuthCard, AuthHeader) per the auth-screens design spec.

## Checks

```bash
npm run lint   # eslint
npm test       # vitest (unit)
npm run build  # production build + typecheck
```

## Auth notes / open questions

Per Fatima's `docs/design/auth-screens.md`, the Sign Up spec lists a **Full
name** field, but the sc-39 backend contract (`SignupRequest`) accepts only
email + password. The form intentionally matches the API so signup works
end-to-end. Collecting a name requires a backend contract change first — see
kanban task t_514f91af for the escalation to Adnan.