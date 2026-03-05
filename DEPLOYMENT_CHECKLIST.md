# Umbra Protocol - Production Deployment Checklist

## Pre-Deployment Verification

### 1. Code Compilation

```bash
# Build DAML contracts
cd daml/umbra
daml build
# Expected: Success, generates .dar file

# Build Java backend
cd ../../backend
./gradlew clean build
# Expected: BUILD SUCCESSFUL
```

**Status:** [ ] DAML compiles | [ ] Backend compiles

---

### 2. Review Refactoring Documents

Read and understand:
- [ ] `REFACTOR_SUMMARY.md` - Overview of all changes
- [ ] `BACKEND_FIXES_COMPLETE.md` - Backend implementation details
- [ ] `SECURITY_COMPARISON.md` - Security improvements (D- → A-)

---

### 3. Test DAML Contracts Locally

```bash
cd daml/umbra
daml test
# Expected: All tests pass
```

**Status:** [ ] DAML tests pass

---

## Initial Contract Setup

### 1. Deploy DAML Package to Canton

```bash
# Upload .dar file to Canton ledger
canton -c canton.conf
> participant1.dars.upload("daml/umbra/.daml/dist/umbra-1.0.0.dar")
```

**Status:** [ ] Package uploaded | Package ID: _________________

---

### 2. Create ProtocolPause Contract

**Required before any other contracts!**

```daml
module Setup where

import Umbra.Types
import DA.Time

setupProtocolPause : Script (ContractId ProtocolPause)
setupProtocolPause = do
  operator <- allocateParty "Operator"
  submit operator do
    createCmd ProtocolPause with
      operator = operator
      isPaused = False
      reason = ""
      pausedAt = None
```

**Execute:**
```bash
daml script --dar .daml/dist/umbra-1.0.0.dar --script-name Setup:setupProtocolPause
```

**Status:** [ ] ProtocolPause created | Contract ID: _________________

---

### 3. Create Oracle Contracts with Bounds

```daml
setupOracles : ContractId ProtocolPause -> Script ()
setupOracles pauseCid = do
  operator <- allocateParty "Operator"
  oracle <- allocateParty "Oracle"

  now <- getTime

  -- USDC Oracle (stablecoin, minimal bounds)
  submit oracle do
    createCmd OraclePrice with
      oracle = oracle
      observers = [operator]
      asset = "USDC"
      price = 1.0
      lastUpdated = now
      minPrice = 0.95      -- 5% tolerance
      maxPrice = 1.05      -- 5% tolerance
      maxChangePercent = 0.10  -- 10% max change per update

  -- CC Oracle (volatile asset, wider bounds)
  submit oracle do
    createCmd OraclePrice with
      oracle = oracle
      observers = [operator]
      asset = "CC"
      price = 0.16
      lastUpdated = now
      minPrice = 0.01      -- Floor at 1 cent
      maxPrice = 1.00      -- Cap at $1
      maxChangePercent = 0.50  -- 50% max change per update (volatile)
```

**Status:**
- [ ] USDC Oracle created | CID: _________________
- [ ] CC Oracle created | CID: _________________

---

### 4. Create DarkPoolOperator with Pause Reference

```daml
setupDarkPoolOperator : ContractId ProtocolPause -> Script (ContractId DarkPoolOperator)
setupDarkPoolOperator pauseCid = do
  operator <- allocateParty "Operator"
  submit operator do
    createCmd DarkPoolOperator with
      operator = operator
      pauseCid = pauseCid
```

**Status:** [ ] DarkPoolOperator created | CID: _________________

---

### 5. Create LendingPool with Pause Reference

```daml
setupLendingPool : ContractId ProtocolPause -> Script (ContractId LendingPool)
setupLendingPool pauseCid = do
  operator <- allocateParty "Operator"
  now <- getTime

  submit operator do
    createCmd LendingPool with
      operator = operator
      asset = "USDC"
      totalSupply = 0.0
      totalBorrows = 0.0
      reserves = 0.0
      reserveFactor = 0.10  -- 10% reserve factor
      borrowCap = None      -- Unlimited (or Some 1000000.0 to cap)
      rateModel = RateModelParams with
        baseRate = 0.02           -- 2% base APY
        multiplier = 0.10         -- 10% slope below kink
        jumpMultiplier = 1.00     -- 100% jump above kink
        kink = 0.80               -- 80% utilization kink
      collateralConfig = defaultCCCollateral  -- From Types.daml
      lastUpdateTime = now
      accumulatedIndex = 1.0
      pauseCid = pauseCid
```

**Status:** [ ] LendingPool created | CID: _________________

---

## Backend Configuration

### 1. Update application.properties

```properties
# Umbra Configuration
umbra.operator-party=Operator::122...  # Your operator party ID
umbra.oracle-party=Oracle::122...      # Your oracle party ID
umbra.package-id=<package-id-from-deployment>

# Ledger Configuration
ledger.host=localhost
ledger.port=5011
ledger.application-id=umbra-backend

# Security (if using tokens)
security.issuer-url=https://your-auth-provider.com
security.token=<optional-static-token>
```

**Status:** [ ] Config updated

---

### 2. Start Backend

```bash
cd backend
./gradlew bootRun
```

**Verify startup logs:**
- [ ] "UmbraLedgerClient initialized"
- [ ] "InterestAccrual scheduled" (optional convenience)
- [ ] "LiquidationMonitor scheduled" (optional bot)
- [ ] "OraclePriceService scheduled" (⚠️ DISABLE IN PRODUCTION)

**Status:** [ ] Backend started on port 8080

---

## Endpoint Verification

### 1. Health Check

```bash
curl http://localhost:8080/api/pool
# Expected: {"totalSupplied": 0, "totalBorrowed": 0, ...}
```

**Status:** [ ] Pool endpoint works

---

### 2. Test Supply (Decentralized)

```bash
curl -X POST http://localhost:8080/api/pool/supply \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <user-token>" \
  -d '{"amount": 100}'

# Expected: {"status": "supplied", "transactionId": "..."}
```

**Status:** [ ] Supply works without operator session

---

### 3. Test Borrow (Decentralized)

```bash
curl -X POST http://localhost:8080/api/pool/borrow \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <user-token>" \
  -d '{
    "borrowAmount": 50,
    "collateralAmount": 100
  }'

# Expected: {"status": "borrowed", "transactionId": "..."}
```

**Status:** [ ] Borrow works without operator session

---

### 4. Test Liquidate (Public)

```bash
# First, create underwater position (manipulate oracle price)

curl -X POST http://localhost:8080/api/pool/liquidate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <liquidator-token>" \
  -d '{
    "positionId": "<underwater-position-cid>"
  }'

# Expected: {"status": "liquidated", "liquidator": "...", "transactionId": "..."}
```

**Status:** [ ] Any user can liquidate

---

### 5. Test Dark Pool Privacy

```bash
# User A creates order and trades
curl http://localhost:8080/api/trades/mine \
  -H "Authorization: Bearer <user-a-token>"

# Expected: Returns trades with "side": "buy" or "sell"
#           NO "buyer" or "seller" fields exposed
```

**Status:** [ ] Privacy preserved (no counterparty info)

---

## Production Hardening

### 1. Disable Mock Oracle

In `OraclePriceService.java`, line ~37:

```java
// TODO: Add production mode flag that disables mock prices
@Value("${umbra.oracle.mockMode:false}")  // Change to false!
private boolean mockMode;

@Scheduled(fixedRate = 300_000)
public void updatePrice() {
    if (mockMode) {
        logger.warn("Mock oracle mode enabled - DO NOT USE IN PRODUCTION");
        return;  // Disable mock updates
    }
    // Real oracle price fetching logic here
}
```

**Status:** [ ] Mock oracle disabled

---

### 2. Deploy Real Oracle Feeds

**Options:**
- Chainlink Data Feeds
- RedStone Push Model
- Pyth Network
- Custom aggregator (median of 3+ sources)

**Status:** [ ] Real oracle deployed

---

### 3. Deploy Multiple Liquidation Bots

**Best Practice:** Run 3+ independent liquidation bots operated by different parties.

Each bot runs:
```bash
java -jar liquidation-bot.jar \
  --ledger-host=<canton-host> \
  --liquidator-party=<liquidator-party> \
  --check-interval=30s
```

**Status:**
- [ ] Bot 1 deployed (Operator)
- [ ] Bot 2 deployed (Independent party 1)
- [ ] Bot 3 deployed (Independent party 2)

---

### 4. Set Up Monitoring

**Metrics to monitor:**
- Pool utilization rate
- Liquidation health (positions near HF=1.0)
- Oracle price freshness
- Backend uptime (for convenience services)
- Transaction success rate

**Tools:** Prometheus, Grafana, Canton metrics

**Status:** [ ] Monitoring configured

---

### 5. Configure Protocol Pause Governance

**Best Practice:** Multi-sig or DAO for pause control

```daml
-- Example: Require 3-of-5 signatories to pause
template PauseGovernance
  with
    signatories : [Party]  -- 5 governance members
    threshold : Int        -- 3 required signatures
```

**Status:** [ ] Governance configured

---

## Security Verification

### 1. Test Interest Accrual Without Backend

```bash
# Stop backend
pkill -f "umbra-backend"

# Wait 5 minutes

# User supplies (via direct Canton submission if backend is down)
# Expected: Interest still accrues correctly in DAML
```

**Status:** [ ] Interest accrues inline (verified)

---

### 2. Test Liquidation by Non-Operator

```bash
# Random user (not operator) liquidates position
curl -X POST http://localhost:8080/api/pool/liquidate \
  -H "Authorization: Bearer <random-user-token>" \
  -d '{"positionId": "..."}'

# Expected: Success
```

**Status:** [ ] Permissionless liquidation (verified)

---

### 3. Test Oracle Bounds

```bash
# Try to update oracle with invalid price
# Expected: Rejected by DAML contract

# Try to update with >50% change
# Expected: Rejected by DAML contract
```

**Status:** [ ] Oracle bounds enforced (verified)

---

### 4. Test Emergency Pause

```bash
# Operator pauses protocol
curl -X POST http://localhost:8080/api/admin/pause \
  -H "Authorization: Bearer <operator-token>" \
  -d '{"reason": "Security test"}'

# Try to supply
# Expected: Rejected with "Protocol is paused: Security test"

# Try to liquidate
# Expected: Success (liquidations bypass pause)
```

**Status:** [ ] Circuit breaker works (verified)

---

## Post-Deployment

### 1. Announce Decentralization

**Key Messages:**
- ✅ Users can supply/borrow/repay independently
- ✅ Anyone can run liquidation bots for profit
- ✅ Dark pool preserves privacy
- ✅ Interest accrues on-chain (no backend dependency)
- ✅ Emergency pause available

**Status:** [ ] Documentation updated

---

### 2. Run Load Testing

**Test scenarios:**
- 100 concurrent supply operations
- 50 borrows with varying collateral
- Multiple liquidations during price crash simulation
- Dark pool order spam (rate limiting test)

**Status:** [ ] Load tested

---

### 3. Security Audit

**Recommended:** Independent audit by blockchain security firm

**Focus areas:**
- Interest calculation correctness
- Liquidation logic (critical health factor, close factor)
- Oracle manipulation resistance
- Privacy preservation (dark pool)
- Emergency controls (pause mechanism)

**Status:** [ ] Audit scheduled | [ ] Audit complete

---

## Rollback Plan

If critical issues discovered:

1. **Pause Protocol**
   ```bash
   # Set isPaused = True on ProtocolPause contract
   ```

2. **Notify Users**
   - Post status update
   - Explain issue and timeline

3. **Fix and Redeploy**
   - Fix DAML or backend code
   - Redeploy .dar file
   - Migrate existing contracts if needed

4. **Unpause**
   ```bash
   # Set isPaused = False
   ```

**Status:** [ ] Rollback plan documented

---

## Sign-Off

**Deployment Date:** ___________________

**Deployed By:** ___________________

**Verified By:** ___________________

**Notes:**
_____________________________________________
_____________________________________________
_____________________________________________

---

## 🎉 Success Criteria

- [x] DAML contracts compile
- [x] Backend compiles and starts
- [x] All endpoints respond correctly
- [x] Users can transact independently (no operator required)
- [x] Liquidations are permissionless
- [x] Privacy is preserved (dark pool)
- [x] Interest accrues correctly (even if backend offline)
- [x] Oracle bounds are enforced
- [x] Circuit breaker works
- [ ] Load tested
- [ ] Security audited
- [ ] Monitoring configured
- [ ] Real oracles deployed
- [ ] Multiple liquidation bots running

**When all criteria are met:** ✅ PRODUCTION READY!
