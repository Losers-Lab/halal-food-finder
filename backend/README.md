# Halal Food Finder — Backend

Kotlin / Spring Boot backend for Halal Food Finder.

**Implemented so far:** M1 skeleton + TDD harness, **Create Account**
(sc-39) — email-uniqueness check, password-strength validation,
Argon2id hashing, and the `users` Flyway migration (6 MVP roles, default
`USER`) — and **Log In** (sc-40): credential verification, short RS256 JWT
access token with RBAC role claims, and rotating hashed refresh tokens
(`refresh_tokens` Flyway migration, ~30-day lifetime). See `openapi/v1.json`
for the current contract.

## Stack (ratified — see repository ARCHITECTURE.md)

- **JDK 21 LTS** (Temurin), **Kotlin 2.x (K2)**, **Gradle Kotlin DSL 8.10** (wrapper)
- **Spring Boot 3.3+** — blocking thread-per-request with **Virtual Threads** (NOT WebFlux)
- **springdoc-openapi** — code-first **OpenAPI 3.1**, emitted at `/v1/api-docs`
- **Flyway** + **PostgreSQL 17 / PostGIS 3.4** + **MinIO** (S3-compatible) via Docker Compose
- **Kotest + MockK + Testcontainers** — mandated backend TDD

## Module layout (hexagonal)

```
:domain ──► :application ──► adapters (:persistence, :storage-s3,
    :verification-ai, :verification-committee, :web-api) ──► :bootstrap
```

| Module | Role |
|--------|------|
| `:domain` | Pure Kotlin domain core, no framework deps. |
| `:application` | Use cases / ports (in & out), framework-free. |
| `:persistence` | PostgreSQL/PostGIS via Flyway (jOOQ + Spring Data JDBC land with features). |
| `:storage-s3` | S3-compatible storage adapter (MinIO now, R2 later). |
| `:verification-ai` / `:verification-committee` | Verification adapters (hosted AI + human review). |
| `:web-api` | Code-first REST controllers + OpenAPI config. |
| `:bootstrap` | Assembled Spring Boot application + entrypoint. |

## Local development

```bash
# 1. Start PostGIS + MinIO (config in docker-compose.yml)
docker compose up -d          # postgis:5432, minio api:9000 console:9001

# 2. Run style (from repo root)
./gradlew :bootstrap:bootRun   # http://localhost:8080  /v1/health, /v1/api-docs

# 3. Test (full build: compile every module + run all tests)
./gradlew build
```

The `persistence` smoke test boots a real PostGIS container via Testcontainers
(no local PostGIS required to run it).

## OpenAPI spec

springdoc emits OpenAPI 3.1 at `/v1/api-docs`. The spec is **committed** at
`openapi/v1.json` for the frontend type client and browser extensions. To
regenerate after a contract change:

```bash
./gradlew :bootstrap:bootRun &          # wait for startup
curl -s localhost:8080/v1/api-docs -o openapi/v1.json
kill %1
git add openapi/v1.json && git commit -m "chore(api): refresh committed OpenAPI /v1 spec"
```

## Notes for later backend work

- TDD discipline (test-driven-development) applies to all backend work.
- **Docker Desktop / Engine 29**: Testcontainers needs a modern Docker API
  version. The Gradle `Test` tasks pin `api.version=1.44` — see root
  `build.gradle.kts` (testcontainers-java #11212). If CI uses an older engine
  this pin may need revisiting.
- **Flyway**: the Spring Boot 3.3.13 BOM pins Flyway 10.10.0, which warns on
  PostgreSQL 17. Bump the Flyway modules (e.g. 10.15.1+ / 10.20.x) when the
  first real migration is written.