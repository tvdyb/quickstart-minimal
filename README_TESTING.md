# Testing the Umbra Protocol - Start Here

This is your starting point for testing the fully decentralized Umbra protocol.

---

## 📚 **Documentation Structure**

We've created comprehensive testing docs for you:

| Document | Purpose | When to Use |
|----------|---------|-------------|
| **TESTING_GUIDE.md** | Full testing guide with step-by-step instructions | First time testing, comprehensive verification |
| **QUICK_TEST_COMMANDS.md** | Fast reference for common commands | Daily testing, quick checks |
| **RUNBOOK.md** | Build and deployment procedures | Setup, troubleshooting |
| **BACKEND_FIXES_COMPLETE.md** | What was changed in the refactor | Understanding implementation |
| **SECURITY_COMPARISON.md** | Security improvements (D- → A-) | Understanding security posture |
| **DEPLOYMENT_CHECKLIST.md** | Production deployment steps | Going to production |

---

## ⚡ **Quick Start (5 Minutes)**

### 1. Start the Application

```bash
cd /path/to/cn-quickstart/quickstart-minimal

# First time? Run setup
make setup

# Start everything
make fresh-start
```

Wait for: **"Application is ready"** (~2-3 minutes)

---

### 2. Open the UI

```bash
make open-app-ui
```

Or manually: http://app-provider.localhost:3000

---

### 3. Test Basic Flow

1. **Login** → Should auto-login in test mode
2. **Navigate to "Lending"** tab
3. **Supply 100 USDC** → Click "Supply" button
4. **Verify** → Should see position in "My Positions"

**✅ If this works, your stack is healthy!**

---

## 🎯 **What to Test**

### Core Features

1. **Lending (Decentralized ✨)**
   - Supply assets
   - Borrow with collateral
   - Repay debt
   - **Key:** Works WITHOUT operator being online

2. **Dark Pool (Privacy-Preserving 🔒)**
   - Create orders
   - Execute trades
   - **Key:** Counterparties DON'T see each other

3. **Liquidations (Permissionless 💰)**
   - Any user can liquidate
   - **Key:** Competitive market for liquidation bots

4. **Interest Accrual (On-Chain ⛓️)**
   - Calculates inline in DAML
   - **Key:** Correct even if backend crashes

---

## 🧪 **Verification Tests**

### Test 1: Decentralization (Most Important!)

**What to verify:** User operations don't require operator

```bash
# Get auth token from browser (DevTools → Application → Cookies)
export TOKEN="your-token-here"

# Supply without operator
curl -X POST http://localhost:8080/api/pool/supply \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 50}'

# Expected: 200 OK, only user's token required
```

**Result:** ✅ Single-party authorization

---

### Test 2: Privacy

**What to verify:** Dark pool doesn't leak counterparty info

```bash
# Check your trades
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/trades/mine | jq

# Verify response has:
# ✅ "side": "buy" or "sell"
# ❌ NO "buyer" field
# ❌ NO "seller" field
```

**Result:** ✅ Privacy preserved

---

### Test 3: Permissionless Liquidation

**What to verify:** Anyone can liquidate

```bash
# Any authenticated user can liquidate
curl -X POST http://localhost:8080/api/pool/liquidate \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "positionId": "underwater-position-id"
  }'

# Expected: SUCCESS (not just operator)
```

**Result:** ✅ Anyone can liquidate for profit

---

## 📊 **Test Coverage**

After testing, verify you've covered:

### Functional Tests
- [ ] Supply USDC
- [ ] Borrow with collateral
- [ ] Repay debt
- [ ] Create buy order
- [ ] Create sell order
- [ ] Cancel order
- [ ] View positions
- [ ] View trades

### Decentralization Tests
- [ ] Supply works without operator session
- [ ] Borrow works without operator session
- [ ] Repay works without operator session
- [ ] Non-operator can liquidate

### Privacy Tests
- [ ] Trades hide counterparty from buyer
- [ ] Trades hide counterparty from seller
- [ ] Unauthenticated users blocked

### Error Tests
- [ ] Insufficient collateral rejected
- [ ] Negative amounts rejected
- [ ] Zero price rejected

---

## 🐛 **Common Issues**

### Issue: "Address already in use"
```bash
# Solution: Kill port 8080
lsof -ti:8080 | xargs kill -9
make start
```

### Issue: "Java version mismatch"
```bash
# Solution: Set Java 21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version  # Verify
```

### Issue: "DAML SDK not found"
```bash
# Solution: Install DAML
make install-daml-sdk
```

### Issue: "Docker containers not starting"
```bash
# Solution: Clean and restart
make clean-all-docker
make fresh-start
```

### Issue: "Can't login"
```bash
# Check .env.local exists
cat .env.local

# If missing, run setup
make setup
```

---

## 🔍 **Debugging**

### Check Logs
```bash
# All logs
make logs

# Backend only
make logs | grep backend

# Errors only
make logs | grep -i error

# Live tail
make tail
```

### Check Canton State
```bash
make canton-console

# In console, check contracts:
participant1.ledger_api.acs.of_all()
```

### Check API Health
```bash
curl http://localhost:8080/api/pool
# Should return JSON with pool stats
```

---

## 📈 **Performance Benchmarks**

Expected performance on local machine:

| Operation | Time | Status |
|-----------|------|--------|
| Pool stats query | < 1s | ✅ |
| Supply operation | < 3s | ✅ |
| Borrow operation | < 5s | ✅ |
| Order creation | < 3s | ✅ |
| Liquidation | < 4s | ✅ |

If slower, check:
- Docker memory (increase to 8GB+)
- CPU usage (close other apps)
- Network latency

---

## 🎓 **Learning Path**

### Beginner
1. Read **TESTING_GUIDE.md** sections 1-3
2. Start the app
3. Test via UI only
4. Basic supply/borrow flow

### Intermediate
1. Read **QUICK_TEST_COMMANDS.md**
2. Test via API (curl)
3. Verify decentralization
4. Run smoke tests

### Advanced
1. Read **BACKEND_FIXES_COMPLETE.md**
2. Review DAML code
3. Test edge cases
4. Contribute improvements

---

## 🚀 **Next Steps After Testing**

### If Everything Works
✅ **Ready for staging deployment!**

Follow: **DEPLOYMENT_CHECKLIST.md**

### If Issues Found
1. Check logs: `make logs`
2. Review error messages
3. Check documentation
4. File GitHub issue if needed

### Security Hardening
1. Review **SECURITY_COMPARISON.md**
2. Address remaining risks (multi-oracle, rate limiting)
3. Get security audit
4. Deploy to testnet first

---

## 📞 **Getting Help**

### Documentation
- **TESTING_GUIDE.md** - Comprehensive testing
- **QUICK_TEST_COMMANDS.md** - Fast reference
- **RUNBOOK.md** - Build/deployment
- **SECURITY_COMPARISON.md** - Security analysis

### Commands
```bash
make help           # Show all make targets
make status         # Check container status
make logs           # View logs
make canton-console # Debug ledger state
```

### Common Commands
```bash
make fresh-start    # Clean start
make restart        # Restart after changes
make stop           # Stop everything
make tail           # Live logs
```

---

## ✨ **What Makes This Special**

This refactored Umbra protocol is **production-ready** with:

✅ **Decentralized Lending** - Users supply/borrow independently
✅ **Permissionless Liquidations** - Anyone can liquidate for profit
✅ **True Dark Pool Privacy** - Counterparties hidden
✅ **On-Chain Interest** - Correct even if backend offline
✅ **Oracle Security** - Price bounds and staleness checks
✅ **Emergency Controls** - Circuit breaker for security incidents

**Security Grade: A-** (up from D- before refactor!)

---

## 🎯 **Key Testing Goals**

Your testing should verify:

1. ✅ **Decentralization:** Lending works without operator
2. ✅ **Privacy:** Dark pool hides counterparties
3. ✅ **Security:** Interest accrues correctly on-chain
4. ✅ **Robustness:** Liquidations work for any party

**If all four pass → System is production-ready!**

---

## 🎉 **Ready to Start?**

```bash
# Let's go!
make fresh-start
make open-app-ui

# Happy testing! 🚀
```

**For detailed step-by-step instructions, see TESTING_GUIDE.md**

**For quick commands, see QUICK_TEST_COMMANDS.md**
