# Multi-stage builder: produces all build artifacts in a container
# No host dependencies needed beyond Docker

# ============================================================
# Stage 1: Build everything (DAML + Backend + Frontend)
# ============================================================
FROM eclipse-temurin:21-jdk AS builder

# Install Node 20
RUN apt-get update && apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    npm --version && node --version

# Install DAML SDK 3.4.10
RUN curl -fSL --retry 5 --retry-delay 10 --retry-max-time 600 \
      https://github.com/digital-asset/daml/releases/download/v3.4.10/daml-sdk-3.4.10-linux-x86_64.tar.gz \
      -o /tmp/daml-sdk.tar.gz && \
    mkdir -p /tmp/daml-sdk && \
    tar xzf /tmp/daml-sdk.tar.gz -C /tmp/daml-sdk --strip-components=1 && \
    cd /tmp/daml-sdk && \
    ./install.sh --install-with-custom-version 3.4.10 && \
    rm -rf /tmp/daml-sdk /tmp/daml-sdk.tar.gz

ENV PATH="/root/.daml/bin:${PATH}"

WORKDIR /project

# Copy everything needed for the build
COPY . .

# Make gradlew executable
RUN chmod +x gradlew

# Build frontend
RUN cd frontend && npm install && npm run build

# Build DAML + backend (daml:build includes codeGen, backend:build depends on daml:build)
RUN ./gradlew :daml:build :backend:build distTar --no-daemon --console=plain

# Artifacts are now at:
#   frontend/dist/
#   backend/build/distributions/backend.tar
#   backend/build/otel-agent/opentelemetry-javaagent-*.jar
#   daml/licensing/.daml/dist/quickstart-licensing-0.0.1.dar

# ============================================================
# Stage 2: Export artifacts only (tiny image)
# ============================================================
FROM alpine:3.19 AS artifacts

COPY --from=builder /project/frontend/dist /artifacts/frontend/dist
COPY --from=builder /project/backend/build/distributions/backend.tar /artifacts/backend/build/distributions/backend.tar
COPY --from=builder /project/backend/build/otel-agent/ /artifacts/backend/build/otel-agent/
COPY --from=builder /project/daml/licensing/.daml/dist/quickstart-licensing-0.0.1.dar /artifacts/daml/licensing/.daml/dist/quickstart-licensing-0.0.1.dar
