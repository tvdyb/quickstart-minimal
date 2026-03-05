# Backend Migration Guide - Umbra Decentralization

## Critical Backend Changes Required

This document provides specific code changes needed to complete the decentralization refactor.

---

## 1. UmbraController.java - Supply Endpoint

### BEFORE (Current - Broken):
```java
@PostMapping("/pool/supply")
public CompletableFuture<ResponseEntity<Map<String, Object>>> supply(@RequestBody Map<String, Object> body) {
    ResponseEntity<Map<String, Object>> guard = requireOperatorSession("Supply");
    if (guard != null) {
        return CompletableFuture.completedFuture(guard);
    }

    String supplier = getPartyFromBodyOrAuth(body.get("supplier"));
    // ... validation ...

    return ledger.exerciseChoiceMulti(
            poolCid,
            "Umbra.Lending", "LendingPool",
            "Supply",
            choiceArg,
            List.of(config.getOperatorParty(), supplierParty)  // ❌ Multi-party
    )
}
```

### AFTER (Decentralized):
```java
@PostMapping("/pool/supply")
public CompletableFuture<ResponseEntity<Map<String, Object>>> supply(@RequestBody Map<String, Object> body) {
    // ✅ NO operator session check - user signs independently!

    String supplier = authenticatedPartyProvider.getPartyOrFail();
    double amount = parseDouble(body.get("amount"));

    if (amount <= 0.0) {
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of("error", "Supply amount must be positive"))
        );
    }

    return repo.getLendingPool()
            .map(pool -> {
                String poolCid = (String) pool.get("contractId");
                ValueOuterClass.Value choiceArg = recordVal(
                        field("supplier", partyVal(supplier)),
                        field("amount", numericVal(amount))
                );
                return ledger.exerciseChoice(  // ✅ Single party!
                        poolCid,
                        "Umbra.Lending", "LendingPool",
                        "Supply",
                        choiceArg,
                        supplier  // ✅ Only supplier signs
                ).thenApply(tx -> ResponseEntity.ok(Map.<String, Object>of(
                        "status", "supplied",
                        "transactionId", tx.getUpdateId()
                ))).exceptionally(e -> {
                    logger.error("Supply failed", e);
                    return mapLedgerWriteFailure("Supply", e);
                });
            })
            .orElse(CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.of("error", "LendingPool not found"))
            ));
}
```

**Key Changes:**
1. Removed `requireOperatorSession()` call
2. Get supplier from authenticated session ONLY (no body fallback)
3. Changed `exerciseChoiceMulti` → `exerciseChoice`
4. Changed `List.of(operator, supplier)` → just `supplier`

---

## 2. UmbraController.java - Borrow Endpoint

### AFTER (Decentralized):
```java
@PostMapping("/pool/borrow")
public CompletableFuture<ResponseEntity<Map<String, Object>>> borrow(@RequestBody Map<String, Object> body) {
    // ✅ NO operator session check

    String borrower = authenticatedPartyProvider.getPartyOrFail();
    double borrowAmount = parseDouble(body.get("borrowAmount"));
    if (borrowAmount == 0.0) {
        borrowAmount = parseDouble(body.get("amount"));
    }
    double collateralAmount = parseDouble(body.get("collateralAmount"));
    if (collateralAmount == 0.0) {
        collateralAmount = parseDouble(body.get("collateral"));
    }

    String oracleCid = body.get("oracleCid") == null ? null : String.valueOf(body.get("oracleCid"));
    String collateralOracleCid = body.get("collateralOracleCid") == null ? null : String.valueOf(body.get("collateralOracleCid"));

    // Auto-fetch oracle cids if not provided
    if (oracleCid == null || oracleCid.isBlank()) {
        oracleCid = repo.getOraclePrice("USDC").map(v -> (String) v.get("contractId")).orElse(null);
    }
    if (collateralOracleCid == null || collateralOracleCid.isBlank()) {
        collateralOracleCid = repo.getOraclePrice("CC").map(v -> (String) v.get("contractId")).orElse(null);
    }

    // Validation
    if (borrowAmount <= 0.0) {
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of("error", "Borrow amount must be positive"))
        );
    }
    if (collateralAmount <= 0.0) {
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of("error", "Collateral amount must be positive"))
        );
    }
    if (oracleCid == null || collateralOracleCid == null) {
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of("error", "Oracle contracts not initialized"))
        );
    }

    final String borrowerParty = borrower;
    final double requestedBorrowAmount = borrowAmount;
    final double requestedCollateralAmount = collateralAmount;
    final String borrowOracleCid = oracleCid;
    final String collateralPriceOracleCid = collateralOracleCid;

    return repo.getLendingPool()
            .map(pool -> {
                String poolCid = (String) pool.get("contractId");
                ValueOuterClass.Value choiceArg = recordVal(
                        field("borrower", partyVal(borrowerParty)),
                        field("borrowAmount", numericVal(requestedBorrowAmount)),
                        field("collateralAmount", numericVal(requestedCollateralAmount)),
                        field("oracleCid", contractIdVal(borrowOracleCid)),
                        field("collateralOracleCid", contractIdVal(collateralPriceOracleCid))
                );
                return ledger.exerciseChoice(  // ✅ Single party
                        poolCid,
                        "Umbra.Lending", "LendingPool",
                        "Borrow",
                        choiceArg,
                        borrowerParty  // ✅ Only borrower signs
                ).thenApply(tx -> ResponseEntity.ok(Map.<String, Object>of(
                        "status", "borrowed",
                        "transactionId", tx.getUpdateId()
                ))).exceptionally(e -> {
                    logger.error("Borrow failed", e);
                    return mapLedgerWriteFailure("Borrow", e);
                });
            })
            .orElse(CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.of("error", "LendingPool not found"))
            ));
}
```

---

## 3. UmbraController.java - Repay Endpoint

### AFTER (Decentralized):
```java
@PostMapping("/pool/repay")
public CompletableFuture<ResponseEntity<Map<String, Object>>> repay(@RequestBody Map<String, Object> body) {
    // ✅ NO operator session check

    String borrower = authenticatedPartyProvider.getPartyOrFail();
    String contractId = body.get("contractId") == null
            ? String.valueOf(body.getOrDefault("positionId", ""))
            : String.valueOf(body.get("contractId"));
    double repayAmount = parseDouble(body.get("repayAmount"));
    if (repayAmount == 0.0) {
        repayAmount = parseDouble(body.get("amount"));
    }

    if (contractId == null || contractId.isBlank()) {
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of("error", "Position contract id is required"))
        );
    }
    if (repayAmount <= 0.0) {
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of("error", "Repay amount must be positive"))
        );
    }

    final String borrowerParty = borrower;
    final double requestedRepayAmount = repayAmount;
    final String positionContractId = contractId;

    return repo.getLendingPool()
            .map(pool -> {
                String poolContractId = (String) pool.get("contractId");
                ValueOuterClass.Value poolAwareArg = recordVal(
                        field("poolCid", contractIdVal(poolContractId)),
                        field("repayAmount", numericVal(requestedRepayAmount))
                );
                return ledger.exerciseChoice(  // ✅ Single party
                        positionContractId,
                        "Umbra.Lending", "BorrowPosition",
                        "Repay",
                        poolAwareArg,
                        borrowerParty  // ✅ Only borrower signs
                ).thenApply(tx -> ResponseEntity.ok(Map.<String, Object>of(
                        "status", "repaid",
                        "transactionId", tx.getUpdateId()
                ))).exceptionally(e -> {
                    logger.error("Repay failed", e);
                    return mapLedgerWriteFailure("Repay", e);
                });
            })
            .orElse(CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.of("error", "LendingPool not found"))
            ));
}
```

---

## 4. UmbraController.java - Liquidate Endpoint (NEW)

Add this new endpoint to allow anyone to liquidate:

```java
/**
 * POST /api/pool/liquidate → Liquidate an undercollateralized position
 * DECENTRALIZED: Any authenticated user can call this to earn liquidation bonus
 * Body: { positionId, borrowOracleCid?, collateralOracleCid? }
 */
@PostMapping("/pool/liquidate")
public CompletableFuture<ResponseEntity<Map<String, Object>>> liquidate(@RequestBody Map<String, Object> body) {
    // ✅ ANY authenticated user can liquidate
    String liquidator = authenticatedPartyProvider.getPartyOrFail();

    String positionId = String.valueOf(body.getOrDefault("positionId", ""));
    String borrowOracleCid = body.get("borrowOracleCid") == null ? null : String.valueOf(body.get("borrowOracleCid"));
    String collateralOracleCid = body.get("collateralOracleCid") == null ? null : String.valueOf(body.get("collateralOracleCid"));

    // Auto-fetch oracle cids if not provided
    if (borrowOracleCid == null || borrowOracleCid.isBlank()) {
        borrowOracleCid = repo.getOraclePrice("USDC").map(v -> (String) v.get("contractId")).orElse(null);
    }
    if (collateralOracleCid == null || collateralOracleCid.isBlank()) {
        collateralOracleCid = repo.getOraclePrice("CC").map(v -> (String) v.get("contractId")).orElse(null);
    }

    if (positionId.isBlank()) {
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of("error", "Position ID is required"))
        );
    }
    if (borrowOracleCid == null || collateralOracleCid == null) {
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of("error", "Oracle contracts not initialized"))
        );
    }

    final String liquidatorParty = liquidator;
    final String finalBorrowOracleCid = borrowOracleCid;
    final String finalCollateralOracleCid = collateralOracleCid;

    return repo.getLendingPool()
            .map(pool -> {
                String poolCid = (String) pool.get("contractId");
                ValueOuterClass.Value choiceArg = recordVal(
                        field("liquidator", partyVal(liquidatorParty)),
                        field("poolCid", contractIdVal(poolCid)),
                        field("borrowOracleCid", contractIdVal(finalBorrowOracleCid)),
                        field("collateralOracleCid", contractIdVal(finalCollateralOracleCid))
                );
                return ledger.exerciseChoice(  // ✅ Anyone can liquidate
                        positionId,
                        "Umbra.Lending", "BorrowPosition",
                        "Liquidate",
                        choiceArg,
                        liquidatorParty  // ✅ Liquidator signs
                ).thenApply(tx -> ResponseEntity.ok(Map.<String, Object>of(
                        "status", "liquidated",
                        "transactionId", tx.getUpdateId(),
                        "liquidator", liquidatorParty
                ))).exceptionally(e -> {
                    logger.error("Liquidation failed", e);
                    return mapLedgerWriteFailure("Liquidate", e);
                });
            })
            .orElse(CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.of("error", "LendingPool not found"))
            ));
}
```

---

## 5. LiquidationMonitor.java - Update to use public endpoint

Update the liquidation trigger logic:

```java
@Scheduled(fixedRate = 30_000)
public void checkLiquidations() {
    String operator = config.getOperatorParty();
    if (operator.isEmpty()) return;

    try {
        List<Map<String, Object>> positions = repo.getAllBorrowPositions();
        if (positions.isEmpty()) return;

        Optional<Map<String, Object>> borrowOracle = repo.getOraclePrice("USDC");
        Optional<Map<String, Object>> collateralOracle = repo.getOraclePrice("CC");
        if (borrowOracle.isEmpty() || collateralOracle.isEmpty()) {
            logger.debug("Oracle prices not available for liquidation check");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> borrowOraclePayload = (Map<String, Object>) borrowOracle.get().get("payload");
        double borrowPrice = Double.parseDouble(String.valueOf(borrowOraclePayload.get("price")));

        @SuppressWarnings("unchecked")
        Map<String, Object> collOraclePayload = (Map<String, Object>) collateralOracle.get().get("payload");
        double collPrice = Double.parseDouble(String.valueOf(collOraclePayload.get("price")));

        String borrowOracleCid = (String) borrowOracle.get().get("contractId");
        String collOracleCid = (String) collateralOracle.get().get("contractId");

        for (Map<String, Object> pos : positions) {
            Optional<Map<String, Object>> poolOpt = repo.getLendingPool();
            if (poolOpt.isEmpty()) {
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> poolPayload = (Map<String, Object>) poolOpt.get().get("payload");
            double accIndex = Double.parseDouble(String.valueOf(poolPayload.get("accumulatedIndex")));
            String poolContractId = (String) poolOpt.get().get("contractId");

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) pos.get("payload");
            String contractId = (String) pos.get("contractId");

            double borrowAmount = Double.parseDouble(String.valueOf(payload.get("borrowAmount")));
            double collateralAmount = Double.parseDouble(String.valueOf(payload.get("collateralAmount")));
            double entryIndex = Double.parseDouble(String.valueOf(payload.get("entryIndex")));
            double liquidationThreshold = Double.parseDouble(String.valueOf(payload.get("liquidationThreshold")));

            // Calculate health factor locally (optimization)
            double growthFactor = accIndex / entryIndex;
            double currentDebt = borrowAmount * growthFactor;
            double debtValue = currentDebt * borrowPrice;
            double collateralValue = collateralAmount * collPrice;
            double healthFactor = debtValue == 0 ? 999.0 : (collateralValue * liquidationThreshold) / debtValue;

            if (healthFactor < 1.0) {
                logger.warn("Liquidating position {} with health factor {}", contractId, healthFactor);

                ValueOuterClass.Value choiceArg = recordVal(
                        field("liquidator", partyVal(operator)),
                        field("poolCid", contractIdVal(poolContractId)),
                        field("borrowOracleCid", contractIdVal(borrowOracleCid)),
                        field("collateralOracleCid", contractIdVal(collOracleCid))
                );

                // ✅ Now uses single-party liquidation
                ledger.exerciseChoice(
                        contractId,
                        "Umbra.Lending", "BorrowPosition",
                        "Liquidate",
                        choiceArg,
                        operator  // ✅ Single party (operator as liquidator bot)
                ).thenAccept(tx -> logger.info("Liquidated position {} (tx: {})", contractId, tx.getUpdateId()))
                 .exceptionally(e -> {
                     logger.error("Failed to liquidate position {}", contractId, e);
                     return null;
                 });
            }
        }
    } catch (Exception e) {
        logger.debug("Liquidation check error (may be normal if no contracts exist)", e);
    }
}
```

---

## 6. UmbraRepository.java - Add Trade Confirm Queries

Add methods to query new trade confirmation templates:

```java
/**
 * Get buyer confirmations for a specific buyer.
 */
public List<Map<String, Object>> getBuyerConfirms(String buyer) {
    String sql = "SELECT contract_id, payload FROM active(?) WHERE payload->>'buyer' = ?";
    try {
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("contractId", rs.getString("contract_id"));
            row.put("payload", parseJson(rs.getString("payload")));
            return row;
        }, BUYER_CONFIRM_TEMPLATE, buyer);
    } catch (Exception e) {
        logger.debug("BuyerConfirm template not yet available in PQS", e);
        return List.of();
    }
}

/**
 * Get seller confirmations for a specific seller.
 */
public List<Map<String, Object>> getSellerConfirms(String seller) {
    String sql = "SELECT contract_id, payload FROM active(?) WHERE payload->>'seller' = ?";
    try {
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("contractId", rs.getString("contract_id"));
            row.put("payload", parseJson(rs.getString("payload")));
            return row;
        }, SELLER_CONFIRM_TEMPLATE, seller);
    } catch (Exception e) {
        logger.debug("SellerConfirm template not yet available in PQS", e);
        return List.of();
    }
}

/**
 * Get all trade confirmations for a trader (both as buyer and seller).
 */
public List<Map<String, Object>> getTradesForTrader(String trader) {
    List<Map<String, Object>> trades = new ArrayList<>();

    // Get buyer confirms
    List<Map<String, Object>> buyerConfirms = getBuyerConfirms(trader);
    for (Map<String, Object> confirm : buyerConfirms) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) confirm.get("payload");
        trades.add(Map.of(
            "contractId", confirm.get("contractId"),
            "payload", payload,
            "side", "buy"
        ));
    }

    // Get seller confirms
    List<Map<String, Object>> sellerConfirms = getSellerConfirms(trader);
    for (Map<String, Object> confirm : sellerConfirms) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) confirm.get("payload");
        trades.add(Map.of(
            "contractId", confirm.get("contractId"),
            "payload", payload,
            "side", "sell"
        ));
    }

    return trades;
}
```

---

## 7. Remove Old TradeConfirm Logic

Search codebase for references to "TradeConfirm" (old unified template) and replace with:
- Query both BuyerConfirm and SellerConfirm
- Each user only sees their own side
- No counterparty information exposed

---

## Testing After Migration

1. **Compile and Build:**
   ```bash
   cd backend
   ./gradlew build
   ```

2. **Check for Compilation Errors** related to:
   - `exerciseChoiceMulti` usage
   - `requireOperatorSession` calls
   - TRADE_CONFIRM_TEMPLATE references

3. **Run Integration Tests** (see REFACTOR_SUMMARY.md Testing Checklist)

4. **Monitor Logs** for:
   - "OPERATOR_SESSION_REQUIRED" errors
   - Successful single-party submissions
   - Liquidation events from non-operator parties

---

## Summary of Changes

| Component | Change | Status |
|-----------|--------|--------|
| Supply endpoint | Remove operator requirement | ⚠️ TODO |
| Borrow endpoint | Remove operator requirement | ⚠️ TODO |
| Repay endpoint | Remove operator requirement | ⚠️ TODO |
| Liquidate endpoint | Add public endpoint | ⚠️ TODO |
| LiquidationMonitor | Update to single-party | ⚠️ TODO |
| UmbraRepository | Add BuyerConfirm/SellerConfirm queries | ⚠️ TODO |
| Trade queries | Replace TradeConfirm with new templates | ⚠️ TODO |

---

**Once these changes are complete, the Umbra protocol will be fully decentralized! 🎉**
