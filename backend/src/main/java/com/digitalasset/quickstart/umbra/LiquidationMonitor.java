package com.digitalasset.quickstart.umbra;

import com.daml.ledger.api.v2.ValueOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.digitalasset.quickstart.umbra.ProtoHelper.*;

/**
 * LIQUIDATION BOT: Monitors all BorrowPositions and triggers liquidation when health factor < 1.0.
 * Runs every 30 seconds.
 *
 * The health factor calculation is on-chain in DAML. This bot calculates locally for optimization,
 * but the DAML contract validates everything atomically with fresh oracle prices.
 *
 * This is an optional bot. Anyone can liquidate directly by calling BorrowPosition.Liquidate.
 */
@Component
public class LiquidationMonitor {

    private static final Logger logger = LoggerFactory.getLogger(LiquidationMonitor.class);

    private final UmbraRepository repo;
    private final UmbraLedgerClient ledger;
    private final UmbraConfig config;

    // Track in-flight liquidations to prevent double-submission
    private final Set<String> pendingLiquidations = ConcurrentHashMap.newKeySet();

    @Autowired
    public LiquidationMonitor(UmbraRepository repo, UmbraLedgerClient ledger, UmbraConfig config) {
        this.repo = repo;
        this.ledger = ledger;
        this.config = config;
    }

    @Scheduled(fixedRate = 30_000)
    public void checkLiquidations() {
        String operator = config.getOperatorParty();
        if (operator.isEmpty()) return;

        try {
            List<Map<String, Object>> positions = repo.getAllBorrowPositions();
            if (positions.isEmpty()) return;

            // Re-fetch oracle prices for each cycle
            Optional<Map<String, Object>> borrowOracle = repo.getOraclePrice("USDC");
            Optional<Map<String, Object>> collateralOracle = repo.getOraclePrice("CC");
            if (borrowOracle.isEmpty() || collateralOracle.isEmpty()) {
                logger.debug("Oracle prices not available for liquidation check");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> borrowOraclePayload = (Map<String, Object>) borrowOracle.get().get("payload");
            @SuppressWarnings("unchecked")
            Map<String, Object> collOraclePayload = (Map<String, Object>) collateralOracle.get().get("payload");

            Object borrowPriceRaw = borrowOraclePayload.get("price");
            Object collPriceRaw = collOraclePayload.get("price");
            if (borrowPriceRaw == null || collPriceRaw == null) {
                logger.warn("Oracle price payload missing 'price' field");
                return;
            }

            double borrowPrice;
            double collPrice;
            try {
                borrowPrice = Double.parseDouble(String.valueOf(borrowPriceRaw));
                collPrice = Double.parseDouble(String.valueOf(collPriceRaw));
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse oracle prices: borrow={}, collateral={}", borrowPriceRaw, collPriceRaw);
                return;
            }

            if (borrowPrice <= 0.0 || collPrice <= 0.0) {
                logger.warn("Oracle prices non-positive: borrow={}, collateral={}", borrowPrice, collPrice);
                return;
            }

            String borrowOracleCid = (String) borrowOracle.get().get("contractId");
            String collOracleCid = (String) collateralOracle.get().get("contractId");

            for (Map<String, Object> pos : positions) {
                String contractId = (String) pos.get("contractId");

                // Skip if already attempting liquidation
                if (pendingLiquidations.contains(contractId)) {
                    continue;
                }

                // Re-fetch pool per position to get latest state
                Optional<Map<String, Object>> poolOpt = repo.getLendingPool();
                if (poolOpt.isEmpty()) return;

                @SuppressWarnings("unchecked")
                Map<String, Object> poolPayload = (Map<String, Object>) poolOpt.get().get("payload");
                double accIndex = Double.parseDouble(String.valueOf(poolPayload.get("accumulatedIndex")));
                String poolContractId = (String) poolOpt.get().get("contractId");

                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) pos.get("payload");

                double borrowAmount = Double.parseDouble(String.valueOf(payload.get("borrowAmount")));
                double collateralAmount = Double.parseDouble(String.valueOf(payload.get("collateralAmount")));
                double entryIndex = Double.parseDouble(String.valueOf(payload.get("entryIndex")));
                double liquidationThreshold = Double.parseDouble(String.valueOf(payload.get("liquidationThreshold")));

                // Calculate health factor locally (on-chain DAML validates authoritatively)
                double growthFactor = accIndex / entryIndex;
                double currentDebt = borrowAmount * growthFactor;
                double debtValue = currentDebt * borrowPrice;
                double collateralValue = collateralAmount * collPrice;
                double healthFactor = debtValue == 0 ? 999.0 : (collateralValue * liquidationThreshold) / debtValue;

                if (healthFactor < 1.0) {
                    logger.warn("Liquidating position {} with health factor {}", contractId, healthFactor);

                    pendingLiquidations.add(contractId);

                    ValueOuterClass.Value choiceArg = recordVal(
                            field("liquidator", partyVal(operator)),
                            field("poolCid", contractIdVal(poolContractId)),
                            field("borrowOracleCid", contractIdVal(borrowOracleCid)),
                            field("collateralOracleCid", contractIdVal(collOracleCid))
                    );

                    ledger.exerciseChoice(
                            contractId,
                            "Umbra.Lending", "BorrowPosition",
                            "Liquidate",
                            choiceArg,
                            operator
                    ).thenAccept(tx -> {
                        logger.info("Liquidated position {} (tx: {})", contractId, tx.getUpdateId());
                        pendingLiquidations.remove(contractId);
                    }).exceptionally(e -> {
                        logger.warn("Failed to liquidate position {}", contractId, e);
                        pendingLiquidations.remove(contractId);
                        return null;
                    });
                }
            }
        } catch (Exception e) {
            logger.warn("Liquidation check error", e);
        }
    }
}
