#!/usr/bin/env bash
# Launch the Tahir's List live dev-stack backend with the `live` profile (sc-157).
#
# The `live` profile (application-live.yml) coordinates the two durable switches
# the live backend needs to serve real seeded hero photos:
#   - app.storage.s3.*            -> real MinIO-backed S3ImagePort (not the stub)
#   - app.images.seed-ingest.*    -> seed-photo ingest runner runs on boot
#
# WITHOUT the `live` profile the app boots with InMemoryImagePort and seed-ingest
# OFF, which is exactly why GET /v1/listings/{id}/image 404s in the live stack
# (sc-157 / epic 111). To change the object-store creds or bucket, override the
# APP_S3_* env vars below or on the launch command line — do not edit the running
# container.
#
# Usage:
#   ./scripts/backend-live.sh     # ensure compose up, (re)start hff-backend
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"

# 1. Backing services (PostGIS + MinIO) from the backend compose file.
docker compose -f "$repo_root/backend/docker-compose.yml" up -d

# 2. (Re)start the backend container, recompiled against the repo mount so
#    bootRun picks up live source + the application-live.yml resources.
docker rm -f hff-backend >/dev/null 2>&1 || true
docker run -d \
  --name hff-backend \
  --restart unless-stopped \
  --network halal-food-finder_default \
  -p 8080:8080 \
  -v "$repo_root/backend":/workspace \
  -v halal-gradle-cache:/root/.gradle \
  -w /workspace \
  -e SPRING_PROFILES_ACTIVE=live \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgis:5432/halal \
  -e SPRING_DATASOURCE_USERNAME=halal \
  -e SPRING_DATASOURCE_PASSWORD=halal \
  -e APP_S3_ENDPOINT="${APP_S3_ENDPOINT:-http://minio:9000}" \
  -e APP_S3_ACCESS_KEY="${APP_S3_ACCESS_KEY:-minioadmin}" \
  -e APP_S3_SECRET_KEY="${APP_S3_SECRET_KEY:-minioadmin}" \
  -e APP_S3_REGION="${APP_S3_REGION:-us-east-1}" \
  -e APP_S3_BUCKET="${APP_S3_BUCKET:-halal}" \
  eclipse-temurin:21-jdk \
  ./gradlew --no-daemon :bootstrap:bootRun

echo "hff-backend started with SPRING_PROFILES_ACTIVE=live (seed-photo ingest enabled)."
echo "curl http://localhost:8080/v1/health to poll readiness."