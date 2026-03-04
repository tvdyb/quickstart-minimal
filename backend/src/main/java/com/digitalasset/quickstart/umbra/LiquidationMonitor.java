package com.digitalasset.quickstart.umbra;

import com.daml.ledger.api.v2.ValueOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.digitalasset.quickstart.umbra.ProtoHelper.*;

/**
 * Monitors all BorrowPositions and triggers liquidation when health factor < 1.0.
 * Runs every 30 seconds.
 */
@Component
public class LiquidationMonitor {

    private static final Logger logger = LoggerFactory.getLogger(LiquidationMonitor.class);

    private final UmbraRepository repo;
    private final UmbraLedgerClient ledger;
    private final UmbraConfig config;

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

            // Get oracle prices for health factor calculation
            Optional<Map<String, Object>> borrowOracle = repo.getOraclePrice("USDC");
            Optional<Map<String, Object>> collateralOracle = repo.getOraclePrice("CC");
            if (borrowOracle.isEmpty() || collateralOracle.isEmpty()) {
                logger.debug("Oracle prices not available for liquidation check");
                return;
            }

            // Get lending pool for accumulated index
            Optional<Map<String, Object>> poolOpt = repo.getLendingPool();
            if (poolOpt.isEmpty()) return;

            @SuppressWarnings("unchecked")
            Map<String, Object> poolPayload = (Map<String, Object>) poolOpt.get().get("payload");
            double accIndex = Double.parseDouble(String.valueOf(poolPayload.get("accumulatedIndex")));

            @SuppressWarnings("unchecked")
            Map<String, Object> borrowOraclePayload = (Map<String, Object>) borrowOracle.get().get("payload");
            double borrowPrice = Double.parseDouble(String.valueOf(borrowOraclePayload.get("price")));

            @SuppressWarnings("unchecked")
            Map<String, Object> collOraclePayload = (Map<String, Object>) collateralOracle.get().get("payload");
            double collPrice = Double.parseDouble(String.valueOf(collOraclePayload.get("price")));

            String borrowOracleCid = (String) borrowOracle.get().get("contractId");
            String collOracleCid = (String) collateralOracle.get().get("contractId");

            for (Map<String, Object> pos : positions) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) pos.get("payload");
                String contractId = (String) pos.get("contractId");

                double borrowAmount = Double.parseDouble(String.valueOf(payload.get("borrowAmount")));
                double collateralAmount = Double.parseDouble(String.valueOf(payload.get("collateralAmount")));
                double entryIndex = Double.parseDouble(String.valueOf(payload.get("entryIndex")));
                double liquidationThreshold = Double.parseDouble(String.valueOf(payload.get("liquidationThreshold")));

                // Calculate health factor locally
                double growthFactor = accIndex / entryIndex;
                double currentDebt = borrowAmount * growthFactor;
                double debtValue = currentDebt * borrowPrice;
                double collateralValue = collateralAmount * collPrice;
                double healthFactor = debtValue == 0 ? 999.0 : (collateralValue * liquidationThreshold) / debtValue;

                if (healthFactor < 1.0) {
                    logger.warn("Liquidating position {} with health factor {}", contractId, healthFactor);

                    ValueOuterClass.Value choiceArg = recordVal(
                            field("liquidator", partyVal(operator)),
                            field("borrowOracleCid", contractIdVal(borrowOracleCid)),
                            field("collateralOracleCid", contractIdVal(collOracleCid)),
                            field("currentIndex", numericVal(accIndex))
                    );

                    ledger.exerciseChoice(
                            contractId,
                            "Umbra.Lending", "BorrowPosition",
                            "Liquidate",
                            choiceArg,
                            operator
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
}
