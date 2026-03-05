package com.digitalasset.quickstart.umbra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static com.digitalasset.quickstart.umbra.ProtoHelper.*;

/**
 * OPTIONAL CONVENIENCE SCHEDULER: Calls AccrueInterest on the LendingPool every 60 seconds
 * to keep the index fresh.
 *
 * IMPORTANT: This is NOT required for correctness! Interest accrual is now calculated
 * inline in DAML for every Supply/Borrow/Repay/Liquidate operation. This scheduler exists
 * purely for convenience to update the pool state periodically.
 *
 * If this service stops, the protocol REMAINS CORRECT because every operation recalculates
 * interest based on elapsed time. There is NO risk of "free borrows" anymore.
 *
 * DECENTRALIZATION STATUS: This is an optional optimization, not a critical dependency.
 */
@Component
public class InterestAccrual {

    private static final Logger logger = LoggerFactory.getLogger(InterestAccrual.class);

    private final UmbraRepository repo;
    private final UmbraLedgerClient ledger;
    private final UmbraConfig config;

    @Autowired
    public InterestAccrual(UmbraRepository repo, UmbraLedgerClient ledger, UmbraConfig config) {
        this.repo = repo;
        this.ledger = ledger;
        this.config = config;
    }

    @Scheduled(fixedRate = 60_000)
    public void accrueInterest() {
        String operator = config.getOperatorParty();
        if (operator.isEmpty()) return;

        try {
            Optional<Map<String, Object>> poolOpt = repo.getLendingPool();
            if (poolOpt.isEmpty()) return;

            String contractId = (String) poolOpt.get().get("contractId");

            ledger.exerciseChoice(
                    contractId,
                    "Umbra.Lending", "LendingPool",
                    "AccrueInterest",
                    unitVal(),
                    operator
            ).thenAccept(tx -> logger.debug("Accrued interest (tx: {})", tx.getUpdateId()))
             .exceptionally(e -> {
                 logger.warn("Failed to accrue interest", e);
                 return null;
             });
        } catch (Exception e) {
            logger.warn("Interest accrual error (may be normal if no pool exists)", e);
        }
    }
}
