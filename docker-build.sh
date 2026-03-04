#!/usr/bin/env bash
# Containerized build: builds all artifacts in Docker, extracts them locally,
# then starts the full stack using the existing compose.yaml + Makefile.
#
# Usage: ./docker-build.sh [--build-only]
#   --build-only  Just build artifacts, don't start the stack

set -euo pipefail
cd "$(dirname "$0")"

echo "==> Building all artifacts in Docker..."
DOCKER_BUILDKIT=1 docker build \
  -f Dockerfile.builder \
  --target artifacts \
  -t quickstart-builder:latest \
  .

echo "==> Extracting artifacts from builder container..."
CONTAINER_ID=$(docker create quickstart-builder:latest)

# Extract frontend dist
rm -rf frontend/dist
docker cp "$CONTAINER_ID:/artifacts/frontend/dist" frontend/dist

# Extract backend tar
mkdir -p backend/build/distributions
docker cp "$CONTAINER_ID:/artifacts/backend/build/distributions/backend.tar" backend/build/distributions/backend.tar

# Extract otel agent
mkdir -p backend/build/otel-agent
docker cp "$CONTAINER_ID:/artifacts/backend/build/otel-agent/." backend/build/otel-agent/

# Extract DAR
mkdir -p daml/licensing/.daml/dist
docker cp "$CONTAINER_ID:/artifacts/daml/licensing/.daml/dist/quickstart-licensing-0.0.1.dar" daml/licensing/.daml/dist/quickstart-licensing-0.0.1.dar

docker rm "$CONTAINER_ID" > /dev/null

echo "==> Build artifacts extracted successfully!"
echo "    frontend/dist/"
echo "    backend/build/distributions/backend.tar"
echo "    backend/build/otel-agent/"
echo "    daml/licensing/.daml/dist/quickstart-licensing-0.0.1.dar"

if [[ "${1:-}" == "--build-only" ]]; then
  echo "==> Done (build only)."
  exit 0
fi

echo ""
echo "==> Starting the full stack with 'make start'..."
echo "    (This uses the existing compose.yaml and Makefile)"
make start
