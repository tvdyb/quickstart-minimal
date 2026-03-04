# Umbra Quickstart Devnet Deploy Checklist

Use this as a strict runbook for first deploy and every redeploy.

## 0) Preflight (5–10 min)
- [ ] Repo at intended commit: `git rev-parse --short HEAD`
- [ ] Java 17+ available locally for build steps
- [ ] DAML installed + expected version
- [ ] Docker daemon healthy on deploy host
- [ ] `ENV_TEMPLATE.devnet` copied to `.env.devnet` and secrets filled

Commands:
```bash
java -version
daml version
docker --version
docker info >/dev/null
```

## 1) Build artifacts (10–20 min)
```bash
cd ~/projects/umbra/quickstart
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$HOME/.daml/bin:$PATH"
make build
```

Acceptance:
- [ ] frontend build succeeded
- [ ] backend build succeeded
- [ ] DAML DARs produced under `daml/.../.daml/dist/`

## 2) Configure devnet env (10 min)
- [ ] Create deploy env file: `.env.devnet`
- [ ] Verify required vars present (AUTH/DB/tokens/ports)
- [ ] Ensure no secrets in git tracked files

Quick checks:
```bash
grep -n "=\s*$" .env.devnet   # should return no required blank values
git status --short              # ensure secrets file is ignored
```

## 3) Bring up stack (10–30 min)
> If Makefile is hardwired to `.env`/`.env.local`, either map `.env.devnet` into those values for devnet host, or run docker compose with explicit `--env-file`.

Primary path:
```bash
cd ~/projects/umbra/quickstart
make start
```

If previous state exists:
```bash
make stop
make start
```

## 4) Post-deploy health checks (5–15 min)
```bash
cd ~/projects/umbra/quickstart
make status
make logs
```

HTTP checks:
```bash
curl -I http://app-provider.localhost:3000
curl -I http://localhost:9090
```
(Replace with devnet domain/ports once ingress is configured.)

Acceptance:
- [ ] expected containers are up
- [ ] no crash loops in logs
- [ ] app UI responds
- [ ] backend/swagger responds

## 5) Functional smoke test (10 min)
- [ ] Auth/login path succeeds
- [ ] One happy-path app flow succeeds end-to-end
- [ ] No critical errors in backend/canton logs

## 6) Observability verification (optional but recommended)
- [ ] Prometheus scraping targets
- [ ] Grafana dashboards loading
- [ ] Loki/Tempo ingesting logs/traces

## 7) Rollback plan (must have before prod-like testing)
- [ ] Previous image tags recorded
- [ ] One-command rollback documented
- [ ] Backup/restore plan for persistent state

Rollback skeleton:
```bash
# example pattern; adjust to your image/tag strategy
# set IMAGE_TAG=<previous>
make stop
make start
```

## 8) Known failure modes + immediate fixes
1. **`make start` interrupted / killed**
   - Run directly in persistent terminal session; avoid constrained runner timeouts.
2. **Port conflicts**
   - Check bound ports and update env ports.
3. **Auth/OIDC mismatch**
   - Validate issuer/jwks/token endpoints and audiences.
4. **DAML version mismatch**
   - Ensure runtime + package versions align.

## 9) Definition of done
Deployment is "done" when all are true:
- [ ] Build reproducible
- [ ] Stack starts cleanly
- [ ] Health checks pass
- [ ] Functional smoke test passes
- [ ] Observability + rollback path confirmed
