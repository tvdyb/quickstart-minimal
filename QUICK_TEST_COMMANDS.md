# Quick Test Commands - Umbra Protocol

Fast reference for testing the Umbra app locally.

---

## 🚀 **Start/Stop**

```bash
# Fresh start (clean + build + start)
make fresh-start

# Just start (if already built)
make start

# Stop everything
make stop

# Restart after code changes
make restart

# Check status
make status

# View logs
make tail
```

---

## 🌐 **Open UIs**

```bash
# Main app
make open-app-ui
# → http://app-provider.localhost:3000

# Swagger API docs
make open-swagger-ui
# → http://localhost:9090

# Grafana (if observability enabled)
make open-observe
# → http://localhost:3030
```

---

## 🧪 **Quick API Tests (curl)**

### Setup Auth
```bash
# Get token from browser after login:
# DevTools → Application → Cookies → auth_token
export TOKEN="your-token-here"
export API="http://localhost:8080/api"
```

### Read Operations
```bash
# Pool stats
curl $API/pool

# My positions
curl -H "Authorization: Bearer $TOKEN" $API/positions/me

# My orders
curl -H "Authorization: Bearer $TOKEN" $API/orders/mine

# My trades
curl -H "Authorization: Bearer $TOKEN" $API/trades/mine

# Oracle price
curl $API/oracle
```

### Supply (Decentralized!)
```bash
curl -X POST $API/pool/supply \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100}'
```

### Borrow (Decentralized!)
```bash
curl -X POST $API/pool/borrow \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "borrowAmount": 8,
    "collateralAmount": 100
  }'
```

### Repay (Decentralized!)
```bash
curl -X POST $API/pool/repay \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "positionId": "YOUR_POSITION_ID",
    "repayAmount": 8
  }'
```

### Liquidate (Anyone can call!)
```bash
curl -X POST $API/pool/liquidate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "positionId": "UNDERWATER_POSITION_ID"
  }'
```

### Create Order
```bash
curl -X POST $API/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "side": "Buy",
    "price": 0.16,
    "quantity": 100
  }'
```

### Cancel Order
```bash
curl -X DELETE $API/orders/YOUR_ORDER_ID \
  -H "Authorization: Bearer $TOKEN"
```

---

## 🔧 **Debugging**

### View Logs
```bash
# All logs
make logs

# Just backend
make logs | grep backend

# Just frontend
make logs | grep frontend

# Errors only
make logs | grep -i error

# Live tail
make tail
```

### Canton Console
```bash
make canton-console

# In console:
participant1.ledger_api.acs.of_all()
```

### Database Query
```bash
docker exec -it quickstart-postgres psql -U canton -d participant1

SELECT template_id_qualified_name, count(*)
FROM active_contracts
GROUP BY template_id_qualified_name;
```

---

## ✅ **Verification Tests**

### Test Decentralization
```bash
# Supply should work without operator being "online"
curl -X POST $API/pool/supply \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"amount": 50}' -v 2>&1 | grep "200 OK"

# Should see SUCCESS with single-party auth
```

### Test Privacy
```bash
# Check trades - should NOT see counterparty
curl -H "Authorization: Bearer $TOKEN" $API/trades/mine | jq
# Expected: No "buyer" or "seller" fields, only "side"
```

### Test Permissionless Liquidation
```bash
# Anyone can liquidate (not just operator)
curl -X POST $API/pool/liquidate \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"positionId": "POSITION_ID"}'
# Should work for ANY authenticated user
```

---

## 🐛 **Common Issues**

### Port Already in Use
```bash
# Kill processes on port 8080
lsof -ti:8080 | xargs kill -9

# Or change port in .env.local
```

### Docker Issues
```bash
# Clean everything
make clean-all-docker

# Fresh start
make fresh-start
```

### Build Issues
```bash
# Clean and rebuild
make clean
make build
```

### Auth Issues
```bash
# Check token is valid
echo $TOKEN

# Re-login in browser
# Get fresh token from DevTools
```

---

## 📊 **Testing Checklist**

Quick verification after starting:

```bash
# 1. Stack is running
make status

# 2. API is responding
curl http://localhost:8080/api/pool

# 3. UI is accessible
curl -I http://app-provider.localhost:3000

# 4. Can authenticate
# Open UI, login, check for party ID display

# 5. Supply works
curl -X POST $API/pool/supply \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"amount": 10}'

# 6. Pool stats update
curl $API/pool | jq .totalSupplied
```

---

## 🎯 **Performance Check**

```bash
# Time API response
time curl http://localhost:8080/api/pool

# Should be < 1 second

# Check memory usage
docker stats --no-stream
```

---

## 📝 **Test Scenarios**

### Scenario 1: Full Lending Flow
```bash
# Supply
curl -X POST $API/pool/supply -H "Authorization: Bearer $TOKEN" \
  -d '{"amount": 100}'

# Borrow
curl -X POST $API/pool/borrow -H "Authorization: Bearer $TOKEN" \
  -d '{"borrowAmount": 8, "collateralAmount": 100}'

# Check position
curl -H "Authorization: Bearer $TOKEN" $API/positions/me

# Repay
curl -X POST $API/pool/repay -H "Authorization: Bearer $TOKEN" \
  -d '{"positionId": "ID_FROM_ABOVE", "repayAmount": 8}'
```

### Scenario 2: Dark Pool Trade
```bash
# Create buy order
curl -X POST $API/orders -H "Authorization: Bearer $TOKEN" \
  -d '{"side": "Buy", "price": 0.16, "quantity": 100}'

# Check order
curl -H "Authorization: Bearer $TOKEN" $API/orders/mine

# After matching, check trades
curl -H "Authorization: Bearer $TOKEN" $API/trades/mine

# Verify privacy (no counterparty exposed)
curl -H "Authorization: Bearer $TOKEN" $API/trades/mine | \
  jq '.[0] | has("buyer") or has("seller")'
# Expected: false
```

---

## 🔄 **Continuous Testing Loop**

```bash
# Watch for changes and restart
while true; do
  make restart-backend
  sleep 5
  curl http://localhost:8080/api/pool
  sleep 60
done
```

---

## 📞 **Quick Help**

```bash
# Show all make targets
make help

# View runbook
cat RUNBOOK.md

# View full testing guide
cat TESTING_GUIDE.md

# View security info
cat SECURITY_COMPARISON.md
```

---

**Pro Tip:** Set up shell aliases:

```bash
alias umb='cd /path/to/quickstart-minimal'
alias umb-start='umb && make start'
alias umb-stop='umb && make stop'
alias umb-test='umb && make test-daml'
alias umb-logs='umb && make tail'
```

Add to `~/.bashrc` or `~/.zshrc`

---

**Happy Testing! 🚀**
