# Umbra Quickstart → Devnet Readiness Plan

_Last updated: 2026-02-24 (CST)_

## Goal
Take the locally-built quickstart and run it as a stable devnet deployment with repeatable releases and observability.

---

## Deliverable from current phase
You now have:
- reproducible local run commands (`RUNBOOK.md`)
- confirmed setup/build path
- identified runtime blocker class (start/build interruptions via SIGKILL in constrained exec environment)

This is enough to begin devnet prep in parallel while local start stabilization finishes.

---

## 1) Required artifacts before first devnet deploy

### A. Version lock
- Pin DAML SDK version (currently 3.4.10)
- Pin Docker image tags used by compose stack
- Capture exact git commit SHA for `quickstart`

### B. Config externalization
Move local values into environment-driven config for devnet:
- hostnames / domains
- exposed ports
- auth mode (OAuth2 config)
- backend URL wiring for frontend/nginx
- party/network identifiers

### C. Secrets inventory (must not be in repo)
- OAuth client secrets
- any Canton/splice credentials
- TLS certs/keys if using HTTPS ingress

Store in secret manager (1Password/Vault/GCP Secret Manager/etc.).

---

## 2) Devnet infra checklist
- [ ] Container runtime host(s) with enough CPU/memory
- [ ] Docker + compose (or convert compose → k8s manifests)
- [ ] Persistent volumes for any stateful components/log retention
- [ ] DNS entries (e.g., `app-provider.devnet.<domain>`)
- [ ] Reverse proxy/ingress + TLS
- [ ] Centralized logs + metrics + alerts

---

## 3) Deployment sequence (recommended)
1. **Build artifacts**
   - run build pipeline, confirm DAML DARs generated
2. **Deploy backend + dependencies first**
   - canton/splice + backend service + required onboarding flow
3. **Run initialization/migrations**
   - load DARs and execute required setup scripts
4. **Deploy frontend/nginx**
   - point to devnet backend endpoints
5. **Smoke test E2E**
   - app load, auth flow, at least one complete functional path

---

## 4) Smoke tests to define devnet success
- [ ] UI loads via devnet domain
- [ ] backend API reachable and healthy
- [ ] DAML workflow path executes (minimum one happy-path transaction)
- [ ] no crash-looping containers for 30+ minutes
- [ ] logs/metrics visible in monitoring stack

---

## 5) Immediate next steps (what I’ll do next)
1. Produce `ENV_TEMPLATE.devnet` from current local vars (`.env`, `.env.local`, compose references) with secret placeholders.
2. Produce `DEPLOY_CHECKLIST.md` with exact preflight + post-deploy commands.
3. Separate runtime-related failures from project failures in a final local evidence report.

---

## 6) Risk notes
- Current SIGKILL interruptions were environmental execution constraints, not a clear deterministic app bug.
- First devnet deployment should still include conservative resource limits + robust restart policy + full logs.
