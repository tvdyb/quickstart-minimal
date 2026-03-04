package com.digitalasset.quickstart.umbra;

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
     * Body: { trader?, baseAsset?, quoteAsset?, side, price, quantity }
     */
    @PostMapping("/orders")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createOrder(@RequestBody Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> guard = requireOperatorSession("Create order");
        if (guard != null) {
            return CompletableFuture.completedFuture(guard);
        }

        final String trader = getPartyFromBodyOrAuth(body.get("trader"));
        final String baseAsset = String.valueOf(body.getOrDefault("baseAsset", "CC"));
        final String quoteAsset = String.valueOf(body.getOrDefault("quoteAsset", "USDC"));
        final String side = normalizeSide(String.valueOf(body.getOrDefault("side", "Buy")));
        final double price = parseDouble(body.get("price"));
        final double quantity = parseDouble(body.get("quantity"));

        if (trader == null || trader.isBlank()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Trader party not found"))
            );
        }
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
    public ResponseEntity<List<Map<String, Object>>> getTrades(@PathVariable String trader) {
        String authenticatedParty = authenticatedPartyProvider.getPartyOrFail();
        String operatorParty = config.getOperatorParty() == null ? "" : config.getOperatorParty();
        if (!authenticatedParty.equals(trader) && !authenticatedParty.equals(operatorParty)) {
            return ResponseEntity.status(403).body(List.of());
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
    public ResponseEntity<List<Map<String, Object>>> getMyTrades() {
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
            return ResponseEntity.internalServerError().body(Map.<String, Object>of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/pool/supply → Supply assets to the lending pool
     * Body: { supplier, amount }
     */
    @PostMapping("/pool/supply")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> supply(@RequestBody Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> guard = requireOperatorSession("Supply");
        if (guard != null) {
            return CompletableFuture.completedFuture(guard);
        }

        String supplier = getPartyFromBodyOrAuth(body.get("supplier"));
        if (supplier == null || supplier.isBlank()) {
            supplier = getPartyFromBodyOrAuth(body.get("trader"));
        }
        double amount = parseDouble(body.get("amount"));
        if (supplier == null || supplier.isBlank()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Supplier party not found"))
            );
        }
        if (amount <= 0.0) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Supply amount must be positive"))
            );
        }
        final String supplierParty = supplier;
        final double supplyAmount = amount;

        return repo.getLendingPool()
                .map(pool -> {
                    String poolCid = (String) pool.get("contractId");
                    ValueOuterClass.Value choiceArg = recordVal(
                            field("supplier", partyVal(supplierParty)),
                            field("amount", numericVal(supplyAmount))
                    );
                    return ledger.exerciseChoiceMulti(
                            poolCid,
                            "Umbra.Lending", "LendingPool",
                            "Supply",
                            choiceArg,
                            List.of(config.getOperatorParty(), supplierParty)
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
     * Body: { borrower, borrowAmount, collateralAmount, oracleCid, collateralOracleCid }
     */
    @PostMapping("/pool/borrow")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> borrow(@RequestBody Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> guard = requireOperatorSession("Borrow");
        if (guard != null) {
            return CompletableFuture.completedFuture(guard);
        }

        String borrower = getPartyFromBodyOrAuth(body.get("borrower"));
        if (borrower == null || borrower.isBlank()) {
            borrower = getPartyFromBodyOrAuth(body.get("trader"));
        }
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
        if (oracleCid == null || oracleCid.isBlank()) {
            oracleCid = repo.getOraclePrice("USDC").map(v -> (String) v.get("contractId")).orElse(null);
        }
        if (collateralOracleCid == null || collateralOracleCid.isBlank()) {
            collateralOracleCid = repo.getOraclePrice("CC").map(v -> (String) v.get("contractId")).orElse(null);
        }
        if (borrower == null || borrower.isBlank()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Borrower party not found"))
            );
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
                    return ledger.exerciseChoiceMulti(
                            poolCid,
                            "Umbra.Lending", "LendingPool",
                            "Borrow",
                            choiceArg,
                            List.of(config.getOperatorParty(), borrowerParty)
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
     * Body: { contractId, repayAmount }
     */
    @PostMapping("/pool/repay")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> repay(@RequestBody Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> guard = requireOperatorSession("Repay");
        if (guard != null) {
            return CompletableFuture.completedFuture(guard);
        }

        String contractId = body.get("contractId") == null
                ? String.valueOf(body.getOrDefault("positionId", ""))
                : String.valueOf(body.get("contractId"));
        double repayAmount = parseDouble(body.get("repayAmount"));
        if (repayAmount == 0.0) {
            repayAmount = parseDouble(body.get("amount"));
        }
        String borrower = getPartyFromBodyOrAuth(body.get("borrower"));
        if (borrower == null || borrower.isBlank()) {
            borrower = getPartyFromBodyOrAuth(body.get("trader"));
        }
        if (borrower == null || borrower.isBlank()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Borrower party not found"))
            );
        }
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
                    return ledger.exerciseChoiceMulti(
                            positionContractId,
                            "Umbra.Lending", "BorrowPosition",
                            "Repay",
                            poolAwareArg,
                            List.of(config.getOperatorParty(), borrowerParty)
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
     * GET /api/positions/:trader → Supply + Borrow positions for trader
     */
    @GetMapping("/positions/{trader}")
    public ResponseEntity<Map<String, Object>> getPositions(@PathVariable String trader) {
        try {
            List<Map<String, Object>> supply = repo.getSupplyPositions(trader).stream().map(this::mapSupplyPosition).toList();
            List<Map<String, Object>> borrow = repo.getBorrowPositions(trader).stream().map(this::mapBorrowPosition).toList();
            return ResponseEntity.ok(Map.<String, Object>of("supply", supply, "borrow", borrow));
        } catch (Exception e) {
            logger.error("Failed to get positions", e);
            return ResponseEntity.internalServerError().body(Map.<String, Object>of("error", e.getMessage()));
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
                "code", "OPERATOR_SESSION_REQUIRED",
                "authenticatedParty", authenticatedParty,
                "operatorParty", operatorParty
        ));
    }

    private ResponseEntity<Map<String, Object>> mapLedgerWriteFailure(String action, Throwable error) {
        Throwable root = unwrap(error);
        if (root instanceof StatusRuntimeException sre && sre.getStatus().getCode() == Status.Code.PERMISSION_DENIED) {
            String authenticatedParty = authenticatedPartyProvider.getParty().orElse("");
            String operatorParty = config.getOperatorParty() == null ? "" : config.getOperatorParty();
            return ResponseEntity.status(403).body(Map.of(
                    "error", action + " was denied by the ledger. Use app-provider/operator credentials.",
                    "code", "LEDGER_PERMISSION_DENIED",
                    "authenticatedParty", authenticatedParty,
                    "operatorParty", operatorParty,
                    "details", String.valueOf(sre.getStatus().getDescription())
            ));
        }
        if (root instanceof StatusRuntimeException sre && sre.getStatus().getCode() == Status.Code.UNAVAILABLE) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", action + " failed because the ledger is unavailable. Wait for canton/splice/backend to become healthy and retry.",
                    "code", "LEDGER_UNAVAILABLE",
                    "details", String.valueOf(sre.getStatus().getDescription())
            ));
        }
        if (root instanceof StatusRuntimeException sre && sre.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
            return ResponseEntity.status(504).body(Map.of(
                    "error", action + " timed out while waiting for the ledger.",
                    "code", "LEDGER_TIMEOUT",
                    "details", String.valueOf(sre.getStatus().getDescription())
            ));
        }
        return ResponseEntity.internalServerError().body(
                Map.of("error", root.getMessage() == null ? action + " failed" : root.getMessage())
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
        String buyer = String.valueOf(payload.getOrDefault("buyer", ""));
        String side = buyer.equals(viewerParty) ? "buy" : "sell";
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
