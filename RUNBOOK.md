# Umbra Quickstart — Local Build/Run Runbook

_Last updated: 2026-02-24 (CST)_

## 1) What this runbook gives you
A reproducible way to:
1. verify prerequisites
2. configure local quickstart
3. build frontend/backend/DAML artifacts
4. start the stack
5. verify health

Also includes known blockers seen on this machine (Mac mini) and fixes.

---

## 2) Environment baseline (this machine)
- Repo: `~/projects/umbra/quickstart`
- Java: Homebrew OpenJDK 17 at `/opt/homebrew/opt/openjdk@17`
- DAML: installed (`~/.daml/bin/daml`, SDK 3.4.10)
- Docker: installed and reachable
- Node: installed

Required exports for each shell:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$HOME/.daml/bin:$PATH"
```

Sanity checks:

```bash
java -version
daml version
docker --version
node --version
```

---

## 3) One-shot local bootstrap

```bash
cd ~/projects/umbra/quickstart
make setup
make build
make start
```

### Notes from actual setup prompts used
- Observability: `n` (disabled)
- OAuth2: default (`on`)
- Party hint: default
- Test mode: default (`off`)

---

## 4) Health checks after `make start`

### Container/process view
```bash
cd ~/projects/umbra/quickstart
make status
```

### Logs
```bash
cd ~/projects/umbra/quickstart
make logs
# or
make tail
```

### App/UI endpoints expected by project
- App UI: `http://app-provider.localhost:3000`
- Swagger UI: `http://localhost:9090`
- Observability (if enabled): `http://localhost:3030`

Quick curl checks:

```bash
curl -I http://app-provider.localhost:3000
curl -I http://localhost:9090
```

---

## 5) What has already succeeded
- `make setup` completed successfully
- `make build` completed core builds successfully (frontend + backend + DAML compilation)
- DAML packages compiled (including quickstart licensing DARs)

---

## 6) Current blocker observed
On this OpenClaw runtime, long Docker build/start commands were interrupted with `SIGKILL` during `make start`/docker build phases.

Observed pattern:
- build stages begin normally
- process receives external kill before full stack comes up

This appears to be execution/runtime interruption, not a deterministic compile error in the project itself.

---

## 7) Fastest workaround if start is interrupted
Run start directly in an interactive terminal (outside constrained exec), then tail logs:

```bash
cd ~/projects/umbra/quickstart
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$HOME/.daml/bin:$PATH"
make start
```

In second terminal:

```bash
cd ~/projects/umbra/quickstart
make tail
```

If partial state got stuck:

```bash
cd ~/projects/umbra/quickstart
make stop
make start
```

---

## 8) Definition of done (local)
Local is considered green when all are true:
1. `make start` exits successfully (or stays up as intended)
2. `make status` shows expected services healthy
3. app endpoint responds on `app-provider.localhost:3000`
4. backend/swagger endpoint responds on `localhost:9090`
5. basic app flow reachable in browser

---

## 9) Files to check if anything drifts
- `Makefile`
- `compose.yaml`
- `.env.local`
- `docker/backend-service/...`
- `daml/licensing/.daml/dist/*.dar`

---

## 10) Debug / Ledger smoke-check

Once the stack is up, verify backend diagnostics:

```bash
cd ~/projects/umbra/quickstart
./scripts/smoke-debug-ledger.sh http://localhost:8080
```

Or through the frontend proxy:

```bash
./scripts/smoke-debug-ledger.sh http://app-provider.localhost:3000/api
```

Also verify the in-app page:

- `http://app-provider.localhost:3000/debug`
