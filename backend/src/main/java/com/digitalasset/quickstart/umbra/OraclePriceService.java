package com.digitalasset.quickstart.umbra;

import com.daml.ledger.api.v2.ValueOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.digitalasset.quickstart.umbra.ProtoHelper.*;

/**
 * Oracle price service. Updates CC/USD price every 5 minutes.
 *
 * WARNING: MOCK MODE generates random prices for development/testing only.
 * In production, set umbra.oracle.mock-mode=false and replace with real market data feeds.
 */
@Component
public class OraclePriceService {

    private static final Logger logger = LoggerFactory.getLogger(OraclePriceService.class);
    private static final double BASE_PRICE = 0.16;
    private static final double PRICE_VARIANCE = 0.01;

    @org.springframework.beans.factory.annotation.Value("${umbra.oracle.mock-mode:true}")
    private boolean mockMode;

    private final UmbraRepository repo;
    private final UmbraLedgerClient ledger;
    private final UmbraConfig config;

    @Autowired
    public OraclePriceService(UmbraRepository repo, UmbraLedgerClient ledger, UmbraConfig config) {
        this.repo = repo;
        this.ledger = ledger;
        this.config = config;
    }

    @Scheduled(fixedRate = 300_000) // 5 minutes
    public void updatePrices() {
        if (!mockMode) {
            logger.debug("Oracle mock mode disabled, skipping price update");
            return;
        }

        String oracleParty = config.getOracleParty();
        if (oracleParty.isEmpty()) return;

        // Update CC price with small random variance
        updateOraclePrice("CC", BASE_PRICE + ThreadLocalRandom.current().nextDouble(-PRICE_VARIANCE, PRICE_VARIANCE), oracleParty);

        // Update USDC price (stable at 1.0) to keep it fresh for staleness checks
        updateOraclePrice("USDC", 1.0, oracleParty);
    }

    private void updateOraclePrice(String asset, double price, String oracleParty) {
        try {
            Optional<Map<String, Object>> oracle = repo.getOraclePrice(asset);
            if (oracle.isEmpty()) {
                logger.debug("No {} oracle price contract found, skipping update", asset);
                return;
            }

            String contractId = (String) oracle.get().get("contractId");
            final double newPrice = Math.round(price * 10000.0) / 10000.0;

            ValueOuterClass.Value choiceArg = recordVal(
                    field("newPrice", numericVal(newPrice))
            );

            ledger.exerciseChoice(
                    contractId,
                    "Umbra.Oracle", "OraclePrice",
                    "UpdatePrice",
                    choiceArg,
                    oracleParty
            ).thenAccept(tx -> logger.info("Updated {} price to {} (tx: {})", asset, newPrice, tx.getUpdateId()))
             .exceptionally(e -> {
                 logger.error("Failed to update {} oracle price", asset, e);
                 return null;
             });
        } catch (Exception e) {
            logger.warn("Oracle {} price update error", asset, e);
        }
    }
}
