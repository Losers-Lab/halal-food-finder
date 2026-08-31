#!/usr/bin/env bash
# Containerized build/test for the Tahir's List backend.
# Host requirements: Docker only. No JDK/Gradle needed on the machine.
#
# Usage:
#   ./scripts/backend-test.sh              # full build + tests
#   ./scripts/backend-test.sh test-only    # :persistence:test only (faster)
set -euo pipefail
# Works from repo root OR from a worktree (e.g. .worktrees/t_xxx) — finds the nearest backend/ dir.
if [ -d "$(dirname "$0")/../backend" ]; then
  cd "$(dirname "$0")/../backend"
elif [ -d "$(dirname "$0")/../../backend" ]; then
  cd "$(dirname "$0")/../../backend"   # script lives in repo-root/scripts/, backend is a sibling dir
else
  echo "Cannot locate backend/ directory relative to $(dirname "$0")" >&2
  exit 1
fi

MODE="${1:-full}"

# Mounts:
#   - repo -> /app (source)
#   - named gradle cache -> /gradle-home (deps persist across runs; kept OUT of the repo tree)
#   - docker.sock -> lets Testcontainers (inside the build container) manage sibling containers.
#     Security note: this is root-equivalent on the host — accepted deliberately so tests
#     can spin up real PostGIS containers. Do not extend this pattern to untrusted code.
# Env (Testcontainers-in-Docker networking):
#   - TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal + --add-host ...:host-gateway:
#     sibling containers bind on the host; the build container must reach them via the
#     gateway alias, otherwise you get "Connection to 172.17.0.1 refused".
#   - TESTCONTAINERS_RYUK_DISABLED=true: Ryuk reaper has the same cross-container
#     networking problem. Cleanup falls to --rm on the sibling containers.

GRADLE_TASKS="./gradlew --no-daemon test"
if [ "$MODE" != "test-only" ]; then
  GRADLE_TASKS="./gradlew --no-daemon build"
fi

docker run --rm \
  -v "$PWD":/app \
  -w /app \
  -v halal-gradle-cache:/gradle-home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  --add-host host.docker.internal:host-gateway \
  eclipse-temurin:21-jdk \
  $GRADLE_TASKS
