# Umbra Quickstart — Local Build/Run Runbook

_Last updated: 2026-03-04 (CST)_

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
- Repo: `/Users/wilsonw/cn-quickstart/quickstart-minimal`
- Java: Homebrew OpenJDK 21 at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
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
cd /Users/wilsonw/cn-quickstart/quickstart-minimal
make setup
make build
make start
```

### Notes from actual setup prompts used
- Observability: `y` (enabled)
- OAuth2: `y`
- Party hint: default `quickstart-<user>-1` format
- Test mode: optional (`y` for local testing)

---

## 4) Health checks after `make start`

### Container/process view
```bash
cd /Users/wilsonw/cn-quickstart/quickstart-minimal
make status
```

### Logs
```bash
cd /Users/wilsonw/cn-quickstart/quickstart-minimal
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

## 5) Fastest recovery if stack is messy/conflicting

Use the dedicated reset target before start:

```bash
cd /Users/wilsonw/cn-quickstart/quickstart-minimal
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/.daml/bin:$PATH"
make fresh-start
```

---

## 6) Definition of done (local)
Local is considered green when all are true:
1. `make start` exits successfully (or stays up as intended)
2. `make status` shows expected services healthy
3. app endpoint responds on `app-provider.localhost:3000`
4. backend/swagger endpoint responds on `localhost:9090`
5. basic app flow reachable in browser

---

## 7) Files to check if anything drifts
- `Makefile`
- `compose.yaml`
- `.env.local`
- `docker/backend-service/...`
- `daml/licensing/.daml/dist/*.dar`

---

## 8) Debug / Ledger smoke-check

Once the stack is up, verify backend diagnostics:

```bash
cd /Users/wilsonw/cn-quickstart/quickstart-minimal
./scripts/smoke-debug-ledger.sh http://localhost:8080
```

Or through the frontend proxy:

```bash
./scripts/smoke-debug-ledger.sh http://app-provider.localhost:3000/api
```

Also verify the in-app page:

- `http://app-provider.localhost:3000/debug`

---

## 9) Dark pool privacy smoke-check

Verify unauthenticated users cannot read protected dark-pool endpoints:

```bash
cd /Users/wilsonw/cn-quickstart/quickstart-minimal
make smoke-dark-pool-privacy
```
