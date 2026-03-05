# Umbra Protocol - Comprehensive Testing Guide

This guide covers all testing methods: local setup, functional testing via UI and API, and verification of decentralization improvements.

---

## 📋 **Prerequisites**

### System Requirements
```bash
# Check you have required tools
java -version        # Should be Java 21
daml version         # Should be DAML SDK 3.4.10+
docker --version     # Docker Desktop or Docker Engine
node --version       # Node 18+
make --version       # GNU Make
```

### Expected Output
```
java version "21.0.x"
Daml SDK Version: 3.4.10
Docker version 24.x.x
v18.x.x or higher
GNU Make 3.81 or higher
```

**Fix if needed:**
```bash
# macOS
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/.daml/bin:$PATH"

# Linux
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$HOME/.daml/bin:$PATH"
```

---

## 🚀 **Quick Start - Full Stack Testing**

### 1. Initial Setup (First Time Only)

```bash
cd /path/to/cn-quickstart/quickstart-minimal

# Configure local environment
make setup
```

**Prompts you'll see:**
- **Observability**: `y` (recommended for testing)
- **OAuth2**: `y` (required for auth)
- **Party hint**: Press Enter for default
- **Test mode**: `y` (enables test features)

**This creates:** `.env.local` with your configuration

---

### 2. Fresh Build and Start

```bash
# Clean start (recommended for testing after code changes)
make fresh-start
```

**This will:**
1. Stop and clean all containers
2. Build DAML contracts
3. Build backend (Java)
4. Build frontend (TypeScript/React)
5. Start Canton network (LocalNet)
6. Start backend service
7. Start frontend dev server
8. Initialize contracts on ledger

**Wait for:** "Application is ready" message (takes 2-3 minutes)

---

### 3. Verify Stack is Running

```bash
make status
```

**Expected output:**
```
NAME                    STATUS
quickstart-frontend     Up (healthy)
quickstart-backend      Up (healthy)
canton-participant-1    Up
canton-participant-2    Up
postgres                Up
```

**Check logs if issues:**
```bash
make tail              # Live logs
make logs              # Full logs
```

---

## 🖥️ **Testing via Web UI**

### 1. Open the Application

```bash
make open-app-ui
```

**Or manually:** http://app-provider.localhost:3000

---

### 2. Test Login Flow

**Step 1:** Click "Login" button
- Should redirect to auth provider
- Auto-login in test mode

**Step 2:** Verify logged in
- Top right should show your party ID
- Example: `quickstart-alice-1::122...`

**Step 3:** Test logout/re-login
- Click profile → Logout
- Login again
- Should work seamlessly

---

### 3. Test Lending Features

#### Supply USDC

1. Navigate to **"Lending"** tab
2. Click **"Supply"** section
3. Enter amount: `100`
4. Click **"Supply"** button

**Expected:**
- ✅ Success toast: "Supplied 100 USDC"
- ✅ Your supply position appears in "My Positions"
- ✅ Pool stats update (Total Supplied increases)

**Verify Decentralization:**
- Open browser console (F12)
- Check network request to `/api/pool/supply`
- Should see `200 OK` response
- **No operator session required!**

#### Borrow with Collateral

1. In **"Borrow"** section
2. Enter borrow amount: `50`
3. Enter collateral amount: `100` (CC tokens)
4. Click **"Borrow"** button

**Expected:**
- ✅ Success: "Borrowed 50 USDC"
- ✅ Borrow position appears in "My Positions"
- ✅ Pool stats update (Total Borrowed increases)
- ✅ Shows health factor (should be > 1.0)

**Health Factor Calculation:**
- Collateral Value: 100 CC × $0.16 = $16
- Borrow Value: 50 USDC × $1.00 = $50
- LTV: 55%, Liquidation Threshold: 65%
- Health Factor: ($16 × 0.65) / $50 = 0.208

**This should FAIL** (insufficient collateral)

**Correct amounts:**
- Borrow: `8` USDC
- Collateral: `100` CC
- Health Factor: ($16 × 0.65) / $8 = 1.3 ✅

#### Repay Debt

1. Find your borrow position in "My Positions"
2. Click **"Repay"** button
3. Enter amount: `8` (full repayment)
4. Click **"Repay"**

**Expected:**
- ✅ Success: "Repaid 8 USDC"
- ✅ Position removed (or updated if partial)
- ✅ Pool stats update

---

### 4. Test Dark Pool Trading

#### Create Buy Order

1. Navigate to **"Trade"** tab
2. In **"Create Order"** section
3. Select: **Buy**
4. Price: `0.16`
5. Quantity: `100`
6. Click **"Place Order"**

**Expected:**
- ✅ Success: "Order created"
- ✅ Order appears in "My Orders"
- ✅ Status: "Open"

#### Create Sell Order (as different user)

**Option 1 - Same browser:** Logout → Login as different party
**Option 2 - Incognito window:** Open http://app-provider.localhost:3000

1. Create Sell order
2. Price: `0.16`
3. Quantity: `100`

**Operator will match orders** (automatic or manual depending on matching engine)

#### Verify Privacy

1. After trade executes, check **"Recent Trades"**
2. You should see:
   - ✅ Your side: "Buy" or "Sell"
   - ✅ Price and quantity
   - ❌ **Counterparty is NOT shown!**

**This proves privacy preservation!**

---

## 🔧 **Testing via API (Advanced)**

### 1. Get Auth Token

**For testing, extract token from browser:**

1. Open app in browser
2. Login
3. Open DevTools (F12) → Application → Cookies
4. Find `auth_token` cookie
5. Copy value

**Or use test token if configured in `.env.local`**

---

### 2. API Endpoints - Lending

#### GET Pool Stats
```bash
curl http://localhost:8080/api/pool
```

**Expected:**
```json
{
  "totalSupplied": 100.0,
  "totalBorrowed": 8.0,
  "utilization": 0.08,
  "supplyApy": 0.008,
  "borrowApy": 0.028,
  "reserves": 0.0
}
```

---

#### POST Supply (Decentralized!)

```bash
TOKEN="your-auth-token-here"

curl -X POST http://localhost:8080/api/pool/supply \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100
  }'
```

**Expected:**
```json
{
  "status": "supplied",
  "transactionId": "12345..."
}
```

**Verify Decentralization:**
- ✅ No operator party in request
- ✅ Only user's token required
- ✅ Works even if operator backend has issues

---

#### POST Borrow (Decentralized!)

```bash
curl -X POST http://localhost:8080/api/pool/borrow \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "borrowAmount": 8,
    "collateralAmount": 100
  }'
```

**Expected:**
```json
{
  "status": "borrowed",
  "transactionId": "67890..."
}
```

**Test auto-oracle fetch:** Oracle CIDs are auto-populated if not provided.

---

#### POST Repay (Decentralized!)

```bash
# First, get your position ID
curl http://localhost:8080/api/positions/me \
  -H "Authorization: Bearer $TOKEN"

# Use the position contractId from response
curl -X POST http://localhost:8080/api/pool/repay \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "positionId": "00abc123...",
    "repayAmount": 8
  }'
```

**Expected:**
```json
{
  "status": "repaid",
  "transactionId": "11111..."
}
```

---

#### POST Liquidate (Permissionless!)

**Setup:** Create underwater position first (manipulate oracle or wait)

```bash
# ANY authenticated user can liquidate
curl -X POST http://localhost:8080/api/pool/liquidate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "positionId": "00underwater123..."
  }'
```

**Expected:**
```json
{
  "status": "liquidated",
  "liquidator": "quickstart-alice-1::122...",
  "transactionId": "22222..."
}
```

**This is the key decentralization feature!** Anyone can liquidate for profit.

---

### 3. API Endpoints - Dark Pool

#### GET My Orders

```bash
curl http://localhost:8080/api/orders/mine \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:**
```json
[
  {
    "id": "00order123...",
    "side": "buy",
    "price": 0.16,
    "quantity": 100,
    "status": "Open"
  }
]
```

---

#### POST Create Order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "side": "Buy",
    "price": 0.16,
    "quantity": 100
  }'
```

**Expected:**
```json
{
  "status": "created",
  "transactionId": "33333..."
}
```

---

#### GET My Trades (Privacy Test!)

```bash
curl http://localhost:8080/api/trades/mine \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:**
```json
[
  {
    "id": "00trade123...",
    "side": "buy",
    "price": 0.16,
    "quantity": 100,
    "executedAt": "2026-03-04T12:00:00Z"
  }
]
```

**Key:** No `buyer` or `seller` fields! **Privacy preserved!**

---

#### DELETE Cancel Order

```bash
curl -X DELETE http://localhost:8080/api/orders/00order123... \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:**
```json
{
  "status": "cancelled"
}
```

---

## 🧪 **Decentralization Verification Tests**

### Test 1: Interest Accrual Without Backend

**Goal:** Verify interest accrues on-chain even if backend crashes

```bash
# 1. Supply 100 USDC
curl -X POST http://localhost:8080/api/pool/supply \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"amount": 100}'

# 2. Another user borrows 50 USDC
# (creates interest-bearing debt)

# 3. Stop the backend
make stop-application

# 4. Wait 5 minutes

# 5. Restart backend
make restart-backend

# 6. Check pool stats
curl http://localhost:8080/api/pool

# 7. Verify:
# - accumulatedIndex increased
# - totalBorrows increased (interest accrued)
```

**Result:** ✅ Interest accrues correctly despite backend downtime

---

### Test 2: Supply Without Operator

**Goal:** Verify users can supply even if operator is "offline"

```bash
# 1. Verify operator is NOT in the authentication path
curl -X POST http://localhost:8080/api/pool/supply \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"amount": 50}' \
  -v 2>&1 | grep -i "authorization"

# Expected: Only ONE Authorization header (user's token)
# NOT TWO parties in request
```

**Result:** ✅ Single-party authorization

---

### Test 3: Liquidation by Anyone

**Goal:** Verify non-operator can liquidate

**Setup:**
```bash
# 1. User A creates undercollateralized position
# (manipulate oracle price or use test scenario)

# 2. User B (different party) liquidates
USER_B_TOKEN="different-user-token"

curl -X POST http://localhost:8080/api/pool/liquidate \
  -H "Authorization: Bearer $USER_B_TOKEN" \
  -d '{"positionId": "00position123..."}'

# Expected: SUCCESS
```

**Result:** ✅ Permissionless liquidation works

---

### Test 4: Dark Pool Privacy

**Goal:** Verify counterparties don't see each other

```bash
# 1. User A creates buy order, gets matched
# 2. User A checks trades
curl http://localhost:8080/api/trades/mine \
  -H "Authorization: Bearer $USER_A_TOKEN" | jq

# 3. Verify response has NO "seller" field
# 4. User B checks their trades
curl http://localhost:8080/api/trades/mine \
  -H "Authorization: Bearer $USER_B_TOKEN" | jq

# 5. Verify response has NO "buyer" field
```

**Result:** ✅ Privacy preserved

---

## 🐛 **Testing Error Scenarios**

### Test Insufficient Collateral

```bash
curl -X POST http://localhost:8080/api/pool/borrow \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "borrowAmount": 100,
    "collateralAmount": 10
  }'
```

**Expected:**
```json
{
  "error": "Insufficient collateral"
}
```

---

### Test Invalid Inputs

```bash
# Negative amount
curl -X POST http://localhost:8080/api/pool/supply \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"amount": -50}'

# Expected: "Supply amount must be positive"

# Zero price
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"side": "Buy", "price": 0, "quantity": 100}'

# Expected: "Price must be positive"
```

---

### Test Oracle Bounds (After Setup)

**Prerequisites:** Oracle contracts deployed with bounds

```bash
# Try to update oracle with out-of-bounds price
# (This requires operator/oracle credentials)

# Expected: DAML contract rejects with:
# "Price below minimum bound" or
# "Price above maximum bound" or
# "Price change too large"
```

---

## 📊 **Automated Testing**

### Run DAML Tests

```bash
make test-daml
```

**This runs unit tests defined in DAML code:**
- Interest calculation tests
- Liquidation logic tests
- Health factor tests

**Expected:** All tests pass

---

### Run Smoke Tests

```bash
make smoke-dark-pool-privacy
```

**Tests:**
- Unauthenticated endpoints return 401
- Privacy boundaries enforced

---

### Run Integration Tests (If Available)

```bash
make integration-test
```

**Tests full user flows:**
- Supply → Borrow → Repay
- Create order → Match → Execute

---

## 🔍 **Debugging Tips**

### Check Canton Ledger State

```bash
make canton-console
```

**In Canton console:**
```scala
// List all contracts
participant1.ledger_api.acs.of_all()

// Query specific template
participant1.ledger_api.acs.of_party(
  "quickstart-alice-1::122...",
  "Umbra.Lending:BorrowPosition"
)

// Check if ProtocolPause exists
participant1.ledger_api.acs.of_all().filter(_.templateId.contains("ProtocolPause"))
```

---

### Check Backend Logs

```bash
make logs | grep -i "error"
make logs | grep -i "supply"
make logs | grep -i "liquidat"
```

---

### Check Frontend Dev Console

1. Open browser DevTools (F12)
2. Console tab
3. Look for API errors
4. Network tab → Check request/response

---

### Verify Database State (PQS)

```bash
# Connect to Postgres
docker exec -it quickstart-postgres psql -U canton -d participant1

# Query contracts
SELECT template_id_qualified_name, count(*)
FROM active_contracts
GROUP BY template_id_qualified_name;

# Check specific position
SELECT contract_id, payload
FROM active_contracts
WHERE template_id_qualified_name = 'Umbra.Lending:BorrowPosition';
```

---

## 📝 **Test Checklist**

### Basic Functionality
- [ ] App starts successfully (`make start`)
- [ ] Login works
- [ ] Supply USDC works
- [ ] Borrow with collateral works
- [ ] Repay works
- [ ] Create order works
- [ ] Cancel order works
- [ ] View positions works
- [ ] View trades works

### Decentralization Features
- [ ] Supply works without operator session
- [ ] Borrow works without operator session
- [ ] Repay works without operator session
- [ ] Any user can liquidate underwater positions
- [ ] Interest accrues even if backend offline

### Privacy Features
- [ ] Trades don't expose counterparty to buyer
- [ ] Trades don't expose counterparty to seller
- [ ] Unauthenticated users can't access private data

### Error Handling
- [ ] Insufficient collateral rejected
- [ ] Invalid inputs rejected (negative amounts, zero price)
- [ ] Oracle staleness checked
- [ ] Authentication required for protected endpoints

### Performance
- [ ] Pool stats load within 1 second
- [ ] Order creation completes within 3 seconds
- [ ] Borrow with collateral completes within 5 seconds

---

## 🎯 **Next Steps After Testing**

1. **If all tests pass:** Ready for staging deployment
2. **If tests fail:** Check logs, review code, fix issues
3. **Security testing:** Run penetration tests, fuzzing
4. **Load testing:** Simulate 100+ concurrent users
5. **Audit:** Get third-party security audit

---

## 📞 **Need Help?**

**Logs location:**
- Backend: `make logs | grep backend`
- Frontend: `make logs | grep frontend`
- Canton: `make logs | grep canton`

**Common issues:**
- Port conflicts: Change ports in `.env.local`
- Java version: Must be Java 21
- Docker memory: Increase to 8GB+ in Docker Desktop
- DAML SDK: Run `make install-daml-sdk`

**Documentation:**
- `RUNBOOK.md` - Build/run instructions
- `REFACTOR_SUMMARY.md` - Architecture changes
- `BACKEND_FIXES_COMPLETE.md` - Implementation details
- `SECURITY_COMPARISON.md` - Security improvements
- `DEPLOYMENT_CHECKLIST.md` - Production deployment

---

**Happy Testing! 🎉**
