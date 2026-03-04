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
 * For DevNet MVP: mock price fluctuating around $0.16 +/- $0.01.
 */
@Component
public class OraclePriceService {

    private static final Logger logger = LoggerFactory.getLogger(OraclePriceService.class);
    private static final double BASE_PRICE = 0.16;
    private static final double PRICE_VARIANCE = 0.01;

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
    public void updatePrice() {
        String oracleParty = config.getOracleParty();
        if (oracleParty.isEmpty()) return;

        try {
            Optional<Map<String, Object>> ccOracle = repo.getOraclePrice("CC");
            if (ccOracle.isEmpty()) {
                logger.debug("No CC oracle price contract found, skipping update");
                return;
            }

            String contractId = (String) ccOracle.get().get("contractId");
            final double newPrice = Math.round((BASE_PRICE + ThreadLocalRandom.current().nextDouble(-PRICE_VARIANCE, PRICE_VARIANCE)) * 10000.0) / 10000.0;

            ValueOuterClass.Value choiceArg = recordVal(
                    field("newPrice", numericVal(newPrice))
            );

            ledger.exerciseChoice(
                    contractId,
                    "Umbra.Oracle", "OraclePrice",
                    "UpdatePrice",
                    choiceArg,
                    oracleParty
            ).thenAccept(tx -> logger.info("Updated CC price to {} (tx: {})", newPrice, tx.getUpdateId()))
             .exceptionally(e -> {
                 logger.error("Failed to update oracle price", e);
                 return null;
             });
        } catch (Exception e) {
            logger.debug("Oracle price update error (may be normal if no contracts exist)", e);
        }
    }
}
