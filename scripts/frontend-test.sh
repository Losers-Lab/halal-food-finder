#!/usr/bin/env bash
# Containerized build/test for the Halal Food Finder frontend.
# Host requirements: Docker only. No Node/npm needed on the machine.
#
# Usage:
#   ./scripts/frontend-test.sh             # npm ci + build (clean install)
#   ./scripts/frontend-test.sh dev-install # npm install (faster, reuses lockfile drift)
set -euo pipefail

# Works from repo root OR from a worktree (e.g. .worktrees/t_xxx) — finds the nearest frontend/ dir.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -d "$SCRIPT_DIR/../frontend" ]; then
  FRONTEND_DIR="$SCRIPT_DIR/../frontend"
elif [ -d "$SCRIPT_DIR/../../frontend" ]; then
  FRONTEND_DIR="$SCRIPT_DIR/../../frontend"   # repo-root/scripts sibling
else
  echo "Cannot locate frontend/ directory relative to $SCRIPT_DIR" >&2
  exit 1
fi

MODE="${1:-ci}"
cd "$FRONTEND_DIR"

NPM_CMD="npm run build"
if [ "$MODE" = "ci" ]; then
  NPM_CMD="npm ci && npm run build"
elif [ "$MODE" != "dev-install" ]; then
  echo "Unknown mode: $MODE (use 'ci' or 'dev-install')" >&2
  exit 1
fi

# Mounts:
#   - frontend dir -> /app (source; node_modules is generated inside the container)
#   - named npm cache -> /npm-cache (persistent, kept OUT of the repo tree)
# Note: node_modules from a host-run npm (if any) would poison the container build;
# the script mounts it as an anonymous volume to isolate it.
docker run --rm \
  -v "$PWD":/app \
  -w /app \
  -v /app/node_modules \
  -v halal-npm-cache:/npm-cache \
  -e npm_config_cache=/npm-cache \
  node:22 \
  bash -lc "$NPM_CMD"
