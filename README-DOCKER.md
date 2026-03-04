# Containerized Build (No Host Dependencies)

Build the entire Quickstart project using only Docker — no Java, DAML SDK, Node, or Gradle needed on your machine.

## Prerequisites

- Docker (27.0+) with Compose v2 (2.27+)
- A configured `.env.local` file (run `make setup` once, OR manually create it — see below)

## Quick Start

```bash
# One command: build in Docker, then start the full stack
./docker-build.sh
```

## Build Only (no stack start)

```bash
./docker-build.sh --build-only
```

This builds all artifacts inside a Docker container and extracts them to the local filesystem:
- `frontend/dist/` — compiled frontend
- `backend/build/distributions/backend.tar` — backend distribution
- `backend/build/otel-agent/` — OpenTelemetry agent JAR
- `daml/licensing/.daml/dist/quickstart-licensing-0.0.1.dar` — compiled DAML model

After extraction, `make start` launches the full compose stack as usual.

## How It Works

1. `Dockerfile.builder` is a multi-stage Docker build with Java 21, Node 20, and DAML SDK 3.4.10
2. It runs the Gradle build (`daml:build`, `backend:build`, `distTar`) and `npm run build` inside the container
3. `docker-build.sh` extracts the artifacts and optionally starts the stack via `make start`

## Notes

- The existing `compose.yaml` and Makefile are **not modified**
- You still need `.env.local` with at least `PARTY_HINT` set for `make start` to work without the interactive setup wizard
- Minimal `.env.local` example:
  ```
  PARTY_HINT=quickstart-dev-1
  AUTH_MODE=shared-secret
  ```
- The Docker build runs on `linux/amd64`. On Apple Silicon, it uses emulation (slower but works).

## Architecture Note

The build is fully containerized but the **runtime** still uses the existing compose stack (`compose.yaml` + module compose files). This keeps the complex multi-service orchestration untouched while eliminating host build dependencies.
