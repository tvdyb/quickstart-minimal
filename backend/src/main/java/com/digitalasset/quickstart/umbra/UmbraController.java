package com.digitalasset.quickstart.umbra;

import com.daml.ledger.api.v2.TransactionOuterClass;
import com.daml.ledger.api.v2.ValueOuterClass;
import com.digitalasset.quickstart.security.AuthenticatedPartyProvider;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.digitalasset.quickstart.umbra.ProtoHelper.*;

/**
 * REST API controller for Umbra dark pool and lending protocol.
 */
@RestController
public class UmbraController {

    private static final Logger logger = LoggerFactory.getLogger(UmbraController.class);

    private final UmbraRepository repo;
    private final UmbraLedgerClient ledger;
    private final UmbraConfig config;
    private final AuthenticatedPartyProvider authenticatedPartyProvider;

    @Autowired
    public UmbraController(
            UmbraRepository repo,
            UmbraLedgerClient ledger,
            UmbraConfig config,
            AuthenticatedPartyProvider authenticatedPartyProvider
    ) {
        this.repo = repo;
        this.ledger = ledger;
        this.config = config;
        this.authenticatedPartyProvider = authenticatedPartyProvider;
    }

    // ── Bootstrap ─────────────────────────────────────────

    /**
     * POST /api/umbra/bootstrap → Initialize all Umbra contracts on the ledger.
     * Creates ProtocolPause, DarkPoolOperator, OraclePrice (USDC + CC), and LendingPool.
     * Idempotent: skips contracts that already exist.
     */
    @PostMapping("/umbra/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrap() {
        String operator = config.getOperatorParty();
        if (operator == null || operator.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "umbra.operator-party not configured"));
        }

        String oracleParty = config.getOracleParty();
        if (oracleParty == null || oracleParty.isBlank()) {
            oracleParty = operator;
        }

        Map<String, String> created = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();

        try {
            // 1. ProtocolPause
            String pauseContractId = null;
            var existingPause = pqsSafe(() -> repo.getProtocolPause());
            if (existingPause.isEmpty()) {
                var tx = ledger.createContractAndWait("Umbra.Types", "ProtocolPause", record(
                        field("operator", partyVal(operator)),
                        field("isPaused", boolVal(false)),
                        field("reason", textVal("")),
                        field("pausedAt", optionalNone())
                ), operator).get();
                pauseContractId = extractCreatedContractId(tx);
                created.put("ProtocolPause", "created");
            } else {
                pauseContractId = (String) existingPause.get().get("contractId");
                skipped.add("ProtocolPause");
            }

            // 2. DarkPoolOperator (needs pauseCid)
            if (pqsSafe(() -> repo.getDarkPoolOperator()).isEmpty()) {
                if (pauseContractId != null) {
                    ledger.createContract("Umbra.DarkPool", "DarkPoolOperator", record(
                            field("operator", partyVal(operator)),
                            field("pauseCid", contractIdVal(pauseContractId))
                    ), operator).get();
                    created.put("DarkPoolOperator", "created");
                } else {
                    created.put("DarkPoolOperator", "FAILED - no ProtocolPause");
                }
            } else {
                skipped.add("DarkPoolOperator");
            }

            // 3. OraclePrice for USDC
            if (pqsSafe(() -> repo.getOraclePrice("USDC")).isEmpty()) {
                ledger.createContract("Umbra.Oracle", "OraclePrice", record(
                        field("oracle", partyVal(oracleParty)),
                        field("observers", listVal(partyVal(operator))),
                        field("asset", textVal("USDC")),
                        field("price", numericVal(1.0)),
                        field("lastUpdated", timestampVal(System.currentTimeMillis() * 1000)),
                        field("minPrice", numericVal(0.90)),
                        field("maxPrice", numericVal(1.10)),
                        field("maxChangePercent", numericVal(0.1))
                ), oracleParty).get();
                created.put("OraclePrice:USDC", "created");
            } else {
                skipped.add("OraclePrice:USDC");
            }

            // 4. OraclePrice for CC
            if (pqsSafe(() -> repo.getOraclePrice("CC")).isEmpty()) {
                ledger.createContract("Umbra.Oracle", "OraclePrice", record(
                        field("oracle", partyVal(oracleParty)),
                        field("observers", listVal(partyVal(operator))),
                        field("asset", textVal("CC")),
                        field("price", numericVal(0.16)),
                        field("lastUpdated", timestampVal(System.currentTimeMillis() * 1000)),
                        field("minPrice", numericVal(0.01)),
                        field("maxPrice", numericVal(100.0)),
                        field("maxChangePercent", numericVal(0.5))
                ), oracleParty).get();
                created.put("OraclePrice:CC", "created");
            } else {
                skipped.add("OraclePrice:CC");
            }

            // 5. LendingPool (needs pauseCid)
            if (pqsSafe(() -> repo.getLendingPool()).isEmpty()) {
                if (pauseContractId != null) {
                    String pauseCid = pauseContractId;
                    ledger.createContract("Umbra.Lending", "LendingPool", record(
                            field("operator", partyVal(operator)),
                            field("asset", textVal("USDC")),
                            field("totalSupply", numericVal(0.0)),
                            field("totalBorrows", numericVal(0.0)),
                            field("reserves", numericVal(0.0)),
                            field("reserveFactor", numericVal(0.10)),
                            field("borrowCap", optionalVal(numericVal(100000.0))),
                            field("rateModel", recordVal(
                                    field("baseRate", numericVal(0.02)),
                                    field("multiplier", numericVal(0.1)),
                                    field("jumpMultiplier", numericVal(3.0)),
                                    field("kink", numericVal(0.80))
                            )),
                            field("collateralConfig", recordVal(
                                    field("asset", textVal("CC")),
                                    field("ltvRatio", numericVal(0.55)),
                                    field("liquidationThreshold", numericVal(0.65)),
                                    field("liquidationBonus", numericVal(0.05)),
                                    field("closeFactor", numericVal(0.50)),
                                    field("oracleMaxAgeSeconds", numericVal(3600.0))
                            )),
                            field("lastUpdateTime", timestampVal(System.currentTimeMillis() * 1000)),
                            field("accumulatedIndex", numericVal(1.0)),
                            field("pauseCid", contractIdVal(pauseCid))
                    ), operator).get();
                    created.put("LendingPool", "created");
                } else {
                    created.put("LendingPool", "FAILED - no ProtocolPause");
                }
            } else {
                skipped.add("LendingPool");
            }

            return ResponseEntity.ok(Map.of(
                    "status", "bootstrap complete",
                    "created", created,
                    "skipped", skipped
            ));
        } catch (Exception e) {
            logger.error("Bootstrap failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Bootstrap failed",
                    "created", created,
                    "skipped", skipped
            ));
        }
    }

    /** Extract the first created contract ID from a transaction. */
    private String extractCreatedContractId(TransactionOuterClass.Transaction tx) {
        for (var event : tx.getEventsList()) {
            if (event.hasCreated()) {
                return event.getCreated().getContractId();
            }
        }
        return null;
    }

    /** PQS query that returns Optional.empty() if the template is not yet registered in PQS. */
    private <T> Optional<T> pqsSafe(java.util.function.Supplier<Optional<T>> query) {
        try {
            return query.get();
        } catch (Exception e) {
            logger.debug("PQS query not yet available (expected during bootstrap): {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ── Dark Pool Endpoints ────────────────────────────────

    /**
     * GET /api/orderbook → Dark mode notice (pre-trade depth intentionally hidden)
     */
    @GetMapping("/orderbook")
    public ResponseEntity<Map<String, Object>> getOrderBook() {
        return ResponseEntity.ok(Map.<String, Object>of(
                "mode", "dark",
                "preTradeDepthVisible", false,
                "message", "Orderbook depth is hidden in dark pool mode.",
                "bids", List.of(),
                "asks", List.of()
        ));
    }

    /**
     * POST /api/orders → Create a new SpotOrder
     * Body: { baseAsset?, quoteAsset?, side, price, quantity }
     * NOTE: Dark pool order creation still requires operator involvement for matching,
     * but any authenticated trader can submit orders.
     */
    @PostMapping("/orders")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createOrder(@RequestBody Map<String, Object> body) {
        // ✅ User must be authenticated, but doesn't need to BE the operator
        final String trader = authenticatedPartyProvider.getPartyOrFail();
        final String baseAsset = String.valueOf(body.getOrDefault("baseAsset", "CC"));
        final String quoteAsset = String.valueOf(body.getOrDefault("quoteAsset", "USDC"));
        final String side = normalizeSide(String.valueOf(body.getOrDefault("side", "Buy")));
        final double price = parseDouble(body.get("price"));
        final double quantity = parseDouble(body.get("quantity"));

        if (price <= 0.0) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Price must be positive"))
            );
        }
        if (quantity <= 0.0) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Quantity must be positive"))
            );
        }

        // Exercise CreateOrder on the DarkPoolOperator
        // NOTE: This still requires operator + trader authorization in DAML
        // This is reasonable for a dark pool where operator facilitates matching
        return repo.getDarkPoolOperator()
                .map(op -> {
                    String opContractId = (String) op.get("contractId");
                    ValueOuterClass.Value choiceArg = recordVal(
                            field("trader", partyVal(trader)),
                            field("baseAsset", textVal(baseAsset)),
                            field("quoteAsset", textVal(quoteAsset)),
                            field("side", enumVal(side)),
                            field("price", numericVal(price)),
                            field("quantity", numericVal(quantity))
                    );

                    return ledger.exerciseChoiceMulti(
                            opContractId,
                            "Umbra.DarkPool", "DarkPoolOperator",
                            "CreateOrder",
                            choiceArg,
                            List.of(config.getOperatorParty(), trader)
                    ).thenApply(tx -> ResponseEntity.ok(Map.<String, Object>of(
                            "status", "created",
                            "transactionId", tx.getUpdateId()
                    ))).exceptionally(e -> {
                        logger.error("Failed to create order", e);
                        return mapLedgerWriteFailure("Create order", e);
                    });
                })
                .orElse(CompletableFuture.completedFuture(
                        ResponseEntity.badRequest().body(Map.<String, Object>of("error", "DarkPoolOperator not found"))
                ));
    }

    /**
     * DELETE /api/orders/:id → Cancel a SpotOrder
     */
    @DeleteMapping("/orders/{contractId}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> cancelOrder(
            @PathVariable String contractId
    ) {
        ResponseEntity<Map<String, Object>> guard = requireOperatorSession("Cancel order");
        if (guard != null) {
            return CompletableFuture.completedFuture(guard);
        }

        String trader = authenticatedPartyProvider.getPartyOrFail();
        ValueOuterClass.Value choiceArg = unitVal();

        return ledger.exerciseChoice(
                contractId,
                "Umbra.DarkPool", "SpotOrder",
                "CancelOrder",
                choiceArg,
                trader
        ).thenApply(tx -> ResponseEntity.ok(Map.<String, Object>of(
                "status", "cancelled",
                "transactionId", tx.getUpdateId()
        ))).exceptionally(e -> {
            logger.error("Failed to cancel order", e);
            return mapLedgerWriteFailure("Cancel order", e);
        });
    }

    @GetMapping("/orders/mine")
    public ResponseEntity<List<Map<String, Object>>> getMyOrders() {
        String trader = authenticatedPartyProvider.getPartyOrFail();
        try {
            List<Map<String, Object>> rows = repo.getActiveOrdersForTrader(trader);
            List<Map<String, Object>> out = rows.stream().map(this::mapOrder).toList();
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            logger.error("Failed to get my orders", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/trades/:trader → Trade confirms for a specific trader
     */
    @GetMapping("/trades/{trader}")
    public ResponseEntity<?> getTrades(@PathVariable String trader) {
        if (!canAccessPartyScope(trader)) {
            return forbiddenPartyScopeResponse(
                    "Can only view your own trades unless logged in as app-provider/operator.",
                    trader
            );
        }
        try {
            List<Map<String, Object>> rows = repo.getTradesForTrader(trader);
            List<Map<String, Object>> out = rows.stream().map(row -> mapTrade(row, trader)).toList();
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            logger.error("Failed to get trades", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/trades/me")
    public ResponseEntity<?> getMyTrades() {
        String trader = authenticatedPartyProvider.getPartyOrFail();
        return getTrades(trader);
    }

    // ── Lending Endpoints ──────────────────────────────────

    /**
     * GET /api/pool → LendingPool stats
     */
    @GetMapping("/pool")
    public ResponseEntity<Map<String, Object>> getPool() {
        try {
            return repo.getLendingPool()
                    .map(pool -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = (Map<String, Object>) pool.get("payload");
                        double totalSupply = Double.parseDouble(String.valueOf(payload.get("totalSupply")));
                        double totalBorrows = Double.parseDouble(String.valueOf(payload.get("totalBorrows")));
                        double reserves = parseDouble(payload.get("reserves"));
                        double reserveFactor = parseDouble(payload.get("reserveFactor"));
                        double borrowCap = parseDouble(payload.get("borrowCap"));
                        double lendableSupply = Math.max(0.0, totalSupply - reserves);
                        double utilization = lendableSupply == 0 ? 0 : totalBorrows / lendableSupply;
                        double borrowApy = computeBorrowApy(payload, utilization);
                        double supplyApy = borrowApy * utilization * Math.max(0.0, 1.0 - reserveFactor);

                        Map<String, Object> stats = new LinkedHashMap<>();
                        stats.put("contractId", pool.get("contractId"));
                        stats.put("asset", payload.get("asset"));
                        stats.put("totalSupply", totalSupply);
                        stats.put("totalBorrows", totalBorrows);
                        stats.put("reserves", reserves);
                        stats.put("reserveFactor", reserveFactor);
                        stats.put("borrowCap", borrowCap);
                        stats.put("totalSupplied", totalSupply);
                        stats.put("totalBorrowed", totalBorrows);
                        stats.put("utilization", utilization);
                        stats.put("tvl", totalSupply - totalBorrows - reserves);
                        stats.put("supplyApy", supplyApy);
                        stats.put("borrowApy", borrowApy);
                        stats.put("rateModel", payload.get("rateModel"));
                        stats.put("accumulatedIndex", payload.get("accumulatedIndex"));
                        return ResponseEntity.ok(stats);
                    })
                    .orElse(ResponseEntity.ok(Map.<String, Object>of("error", "No lending pool found")));
        } catch (Exception e) {
            logger.error("Failed to get pool", e);
            return ResponseEntity.internalServerError().body(Map.<String, Object>of("error", "Failed to load pool"));
        }
    }

    /**
     * POST /api/pool/supply → Supply assets to the lending pool
     * Body: { amount }
     * DECENTRALIZED: User supplies directly without operator co-signature
     */
    @PostMapping("/pool/supply")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> supply(@RequestBody Map<String, Object> body) {
        // ✅ DECENTRALIZED: No operator session required!
        String supplier = authenticatedPartyProvider.getPartyOrFail();
        double amount = parseDouble(body.get("amount"));

        if (amount <= 0.0) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Supply amount must be positive"))
            );
        }

        return repo.getLendingPool()
                .map(pool -> {
                    String poolCid = (String) pool.get("contractId");
                    ValueOuterClass.Value choiceArg = recordVal(
                            field("supplier", partyVal(supplier)),
                            field("amount", numericVal(amount))
                    );
                    return ledger.exerciseChoice(
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
                        ResponseEntity.badRequest().body(Map.<String, Object>of("error", "LendingPool not found"))
                ));
    }

    /**
     * POST /api/pool/borrow → Borrow from the lending pool
     * Body: { borrowAmount, collateralAmount, oracleCid?, collateralOracleCid? }
     * DECENTRALIZED: User borrows directly without operator co-signature
     */
    @PostMapping("/pool/borrow")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> borrow(@RequestBody Map<String, Object> body) {
        // ✅ DECENTRALIZED: No operator session required!
        String borrower = authenticatedPartyProvider.getPartyOrFail();

        double borrowAmountRaw = parseDouble(body.get("borrowAmount"));
        if (borrowAmountRaw <= 0.0 && body.get("borrowAmount") == null) {
            borrowAmountRaw = parseDouble(body.get("amount"));
        }
        double collateralAmountRaw = parseDouble(body.get("collateralAmount"));
        if (collateralAmountRaw <= 0.0 && body.get("collateralAmount") == null) {
            collateralAmountRaw = parseDouble(body.get("collateral"));
        }
        final double borrowAmount = borrowAmountRaw;
        final double collateralAmount = collateralAmountRaw;

        String oracleCid = body.get("oracleCid") == null ? null : String.valueOf(body.get("oracleCid"));
        String collateralOracleCid = body.get("collateralOracleCid") == null ? null : String.valueOf(body.get("collateralOracleCid"));
        if (oracleCid == null || oracleCid.isBlank()) {
            oracleCid = repo.getOraclePrice("USDC").map(v -> (String) v.get("contractId")).orElse(null);
        }
        if (collateralOracleCid == null || collateralOracleCid.isBlank()) {
            collateralOracleCid = repo.getOraclePrice("CC").map(v -> (String) v.get("contractId")).orElse(null);
        }

        if (borrowAmount <= 0.0) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Borrow amount must be positive"))
            );
        }
        if (collateralAmount <= 0.0) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Collateral amount must be positive"))
            );
        }
        if (oracleCid == null || collateralOracleCid == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Oracle contracts not initialized"))
            );
        }

        final String borrowOracleCid = oracleCid;
        final String collateralPriceOracleCid = collateralOracleCid;

        return repo.getLendingPool()
                .map(pool -> {
                    String poolCid = (String) pool.get("contractId");
                    ValueOuterClass.Value choiceArg = recordVal(
                            field("borrower", partyVal(borrower)),
                            field("borrowAmount", numericVal(borrowAmount)),
                            field("collateralAmount", numericVal(collateralAmount)),
                            field("oracleCid", contractIdVal(borrowOracleCid)),
                            field("collateralOracleCid", contractIdVal(collateralPriceOracleCid))
                    );
                    return ledger.exerciseChoice(
                            poolCid,
                            "Umbra.Lending", "LendingPool",
                            "Borrow",
                            choiceArg,
                            borrower  // ✅ Only borrower signs
                    ).thenApply(tx -> ResponseEntity.ok(Map.<String, Object>of(
                            "status", "borrowed",
                            "transactionId", tx.getUpdateId()
                    ))).exceptionally(e -> {
                        logger.error("Borrow failed", e);
                        return mapLedgerWriteFailure("Borrow", e);
                    });
                })
                .orElse(CompletableFuture.completedFuture(
                        ResponseEntity.badRequest().body(Map.<String, Object>of("error", "LendingPool not found"))
                ));
    }

    /**
     * POST /api/pool/repay → Repay a borrow position
     * Body: { contractId (or positionId), repayAmount (or amount) }
     * DECENTRALIZED: User repays directly without operator co-signature
     */
    @PostMapping("/pool/repay")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> repay(@RequestBody Map<String, Object> body) {
        // ✅ DECENTRALIZED: No operator session required!
        String borrower = authenticatedPartyProvider.getPartyOrFail();

        String contractId = body.get("contractId") == null
                ? String.valueOf(body.getOrDefault("positionId", ""))
                : String.valueOf(body.get("contractId"));
        double repayAmountRaw = parseDouble(body.get("repayAmount"));
        if (repayAmountRaw == 0.0) {
            repayAmountRaw = parseDouble(body.get("amount"));
        }
        final double repayAmount = repayAmountRaw;

        if (contractId == null || contractId.isBlank()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Position contract id is required"))
            );
        }
        if (repayAmount <= 0.0) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Repay amount must be positive"))
            );
        }

        return repo.getLendingPool()
                .map(pool -> {
                    String poolContractId = (String) pool.get("contractId");
                    ValueOuterClass.Value poolAwareArg = recordVal(
                            field("poolCid", contractIdVal(poolContractId)),
                            field("repayAmount", numericVal(repayAmount))
                    );
                    return ledger.exerciseChoice(
                            contractId,
                            "Umbra.Lending", "BorrowPosition",
                            "Repay",
                            poolAwareArg,
                            borrower  // ✅ Only borrower signs
                    ).thenApply(tx -> ResponseEntity.ok(Map.<String, Object>of(
                            "status", "repaid",
                            "transactionId", tx.getUpdateId()
                    ))).exceptionally(e -> {
                        logger.error("Repay failed", e);
                        return mapLedgerWriteFailure("Repay", e);
                    });
                })
                .orElse(CompletableFuture.completedFuture(
                        ResponseEntity.badRequest().body(Map.<String, Object>of("error", "LendingPool not found"))
                ));
    }

    /**
     * POST /api/pool/liquidate → Liquidate an undercollateralized position
     * Body: { positionId, borrowOracleCid?, collateralOracleCid? }
     * DECENTRALIZED: ANY authenticated user can call this to earn liquidation bonus
     */
    @PostMapping("/pool/liquidate")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> liquidate(@RequestBody Map<String, Object> body) {
        // ✅ DECENTRALIZED: Any authenticated user can liquidate!
        String liquidator = authenticatedPartyProvider.getPartyOrFail();

        String positionIdRaw = String.valueOf(body.getOrDefault("positionId", ""));
        if (positionIdRaw.isBlank()) {
            positionIdRaw = String.valueOf(body.getOrDefault("contractId", ""));
        }
        final String positionId = positionIdRaw;
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
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Position ID is required"))
            );
        }
        if (borrowOracleCid == null || collateralOracleCid == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Oracle contracts not initialized"))
            );
        }

        final String finalBorrowOracleCid = borrowOracleCid;
        final String finalCollateralOracleCid = collateralOracleCid;

        return repo.getLendingPool()
                .map(pool -> {
                    String poolCid = (String) pool.get("contractId");
                    ValueOuterClass.Value choiceArg = recordVal(
                            field("liquidator", partyVal(liquidator)),
                            field("poolCid", contractIdVal(poolCid)),
                            field("borrowOracleCid", contractIdVal(finalBorrowOracleCid)),
                            field("collateralOracleCid", contractIdVal(finalCollateralOracleCid))
                    );
                    return ledger.exerciseChoice(
                            positionId,
                            "Umbra.Lending", "BorrowPosition",
                            "Liquidate",
                            choiceArg,
                            liquidator  // ✅ Any party can liquidate
                    ).thenApply(tx -> ResponseEntity.ok(Map.<String, Object>of(
                            "status", "liquidated",
                            "transactionId", tx.getUpdateId(),
                            "liquidator", liquidator
                    ))).exceptionally(e -> {
                        logger.error("Liquidation failed", e);
                        return mapLedgerWriteFailure("Liquidate", e);
                    });
                })
                .orElse(CompletableFuture.completedFuture(
                        ResponseEntity.badRequest().body(Map.<String, Object>of("error", "LendingPool not found"))
                ));
    }

    /**
     * GET /api/positions/:trader → Supply + Borrow positions for trader
     */
    @GetMapping("/positions/{trader}")
    public ResponseEntity<Map<String, Object>> getPositions(@PathVariable String trader) {
        if (!canAccessPartyScope(trader)) {
            return ResponseEntity.status(403).body(
                    forbiddenPartyScopeMap(
                            "Can only view your own positions unless logged in as app-provider/operator.",
                            trader
                    )
            );
        }
        try {
            List<Map<String, Object>> supply = repo.getSupplyPositions(trader).stream().map(this::mapSupplyPosition).toList();
            List<Map<String, Object>> borrow = repo.getBorrowPositions(trader).stream().map(this::mapBorrowPosition).toList();
            return ResponseEntity.ok(Map.<String, Object>of("supply", supply, "borrow", borrow));
        } catch (Exception e) {
            logger.error("Failed to get positions", e);
            return ResponseEntity.internalServerError().body(Map.<String, Object>of("error", "Failed to load positions"));
        }
    }

    @GetMapping("/positions/me")
    public ResponseEntity<Map<String, Object>> getMyPositions() {
        return getPositions(authenticatedPartyProvider.getPartyOrFail());
    }

    /**
     * GET /api/oracle → Current CC/USD oracle price
     */
    @GetMapping("/oracle")
    public ResponseEntity<Object> getOraclePrice() {
        try {
            List<Map<String, Object>> prices = repo.getAllOraclePrices();
            Optional<Map<String, Object>> cc = repo.getOraclePrice("CC");
            double ccPrice = cc
                    .map(row -> (Map<String, Object>) row.get("payload"))
                    .map(payload -> parseDouble(payload.get("price")))
                    .orElse(0.0);
            return ResponseEntity.ok(Map.<String, Object>of("ccPrice", ccPrice, "prices", prices));
        } catch (Exception e) {
            logger.error("Failed to get oracle price", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private double parseDouble(Object value) {
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String getPartyFromBodyOrAuth(Object providedParty) {
        if (providedParty != null) {
            String p = String.valueOf(providedParty);
            if (!p.isBlank() && !"null".equalsIgnoreCase(p)) return p;
        }
        return authenticatedPartyProvider.getParty().orElse(null);
    }

    private String normalizeSide(String side) {
        if (side == null) return "Buy";
        return switch (side.trim().toLowerCase(Locale.ROOT)) {
            case "sell" -> "Sell";
            default -> "Buy";
        };
    }

    private boolean canAccessPartyScope(String requestedParty) {
        String authenticatedParty = authenticatedPartyProvider.getPartyOrFail();
        String operatorParty = config.getOperatorParty() == null ? "" : config.getOperatorParty();
        return authenticatedParty.equals(requestedParty) || authenticatedParty.equals(operatorParty);
    }

    private ResponseEntity<Map<String, Object>> forbiddenPartyScopeResponse(String message, String requestedParty) {
        return ResponseEntity.status(403).body(forbiddenPartyScopeMap(message, requestedParty));
    }

    private Map<String, Object> forbiddenPartyScopeMap(String message, String requestedParty) {
        return Map.of(
                "error", message,
                "code", "FORBIDDEN_PARTY_SCOPE"
        );
    }

    private ResponseEntity<Map<String, Object>> requireOperatorSession(String action) {
        String operatorParty = config.getOperatorParty();
        if (operatorParty == null || operatorParty.isBlank()) {
            return null;
        }
        String authenticatedParty = authenticatedPartyProvider.getParty().orElse("");
        if (operatorParty.equals(authenticatedParty)) {
            return null;
        }
        return ResponseEntity.status(403).body(Map.of(
                "error", action + " requires an operator session. Log in as app-provider.",
                "code", "OPERATOR_SESSION_REQUIRED"
        ));
    }

    private ResponseEntity<Map<String, Object>> mapLedgerWriteFailure(String action, Throwable error) {
        Throwable root = unwrap(error);
        if (root instanceof StatusRuntimeException sre && sre.getStatus().getCode() == Status.Code.PERMISSION_DENIED) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", action + " was denied by the ledger. Use app-provider/operator credentials.",
                    "code", "LEDGER_PERMISSION_DENIED"
            ));
        }
        if (root instanceof StatusRuntimeException sre && sre.getStatus().getCode() == Status.Code.UNAVAILABLE) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", action + " failed because the ledger is unavailable. Wait for canton/splice/backend to become healthy and retry.",
                    "code", "LEDGER_UNAVAILABLE"
            ));
        }
        if (root instanceof StatusRuntimeException sre && sre.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
            return ResponseEntity.status(504).body(Map.of(
                    "error", action + " timed out while waiting for the ledger.",
                    "code", "LEDGER_TIMEOUT"
            ));
        }
        logger.error("{} failed", action, root);
        return ResponseEntity.internalServerError().body(
                Map.of("error", action + " failed")
        );
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private double computeBorrowApy(Map<String, Object> payload, double utilization) {
        Object rm = payload.get("rateModel");
        if (!(rm instanceof Map<?, ?> rateModel)) {
            return 0.0;
        }
        double baseRate = parseDouble(rateModel.get("baseRate"));
        double multiplier = parseDouble(rateModel.get("multiplier"));
        double jumpMultiplier = parseDouble(rateModel.get("jumpMultiplier"));
        double kink = parseDouble(rateModel.get("kink"));

        if (utilization <= kink) {
            return baseRate + utilization * multiplier;
        }
        double normalRate = baseRate + kink * multiplier;
        double excess = utilization - kink;
        return normalRate + excess * jumpMultiplier;
    }

    private Map<String, Object> mapOrder(Map<String, Object> row) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) row.get("payload");
        String sideRaw = String.valueOf(payload.getOrDefault("side", "Buy"));
        String side = sideRaw.toLowerCase(Locale.ROOT);
        return Map.<String, Object>of(
                "id", String.valueOf(row.get("contractId")),
                "contractId", String.valueOf(row.get("contractId")),
                "trader", String.valueOf(payload.getOrDefault("trader", "")),
                "side", side,
                "price", parseDouble(payload.get("price")),
                "quantity", parseDouble(payload.get("quantity"))
        );
    }

    private Map<String, Object> mapTrade(Map<String, Object> row, String viewerParty) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) row.get("payload");
        // ✅ PRIVACY: Side is already marked by UmbraRepository (buy or sell)
        // No counterparty information is exposed
        String side = String.valueOf(row.getOrDefault("side", "buy"));
        return Map.<String, Object>of(
                "id", String.valueOf(row.get("contractId")),
                "price", parseDouble(payload.get("price")),
                "quantity", parseDouble(payload.get("quantity")),
                "executedAt", String.valueOf(payload.getOrDefault("executedAt", "")),
                "side", side
        );
    }

    private Map<String, Object> mapSupplyPosition(Map<String, Object> row) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) row.get("payload");
        return Map.<String, Object>of(
                "id", String.valueOf(row.get("contractId")),
                "type", "supply",
                "amount", parseDouble(payload.get("amount")),
                "asset", String.valueOf(payload.getOrDefault("asset", "USDC"))
        );
    }

    private Map<String, Object> mapBorrowPosition(Map<String, Object> row) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) row.get("payload");
        return Map.<String, Object>of(
                "id", String.valueOf(row.get("contractId")),
                "type", "borrow",
                "amount", parseDouble(payload.get("borrowAmount")),
                "collateral", parseDouble(payload.get("collateralAmount")),
                "asset", String.valueOf(payload.getOrDefault("borrowAsset", "USDC"))
        );
    }
}
