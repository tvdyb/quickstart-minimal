package com.digitalasset.quickstart.umbra;

import com.daml.ledger.api.v2.ValueOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.digitalasset.quickstart.umbra.ProtoHelper.*;

/**
 * Dark pool matching engine. Runs every 2 seconds.
 * Polls active SpotOrders, matches crossing orders at midpoint price,
 * and executes FillOrder choices.
 */
@Component
public class MatchingEngine {

    private static final Logger logger = LoggerFactory.getLogger(MatchingEngine.class);

    private final UmbraRepository repo;
    private final UmbraLedgerClient ledger;
    private final UmbraConfig config;

    @Autowired
    public MatchingEngine(UmbraRepository repo, UmbraLedgerClient ledger, UmbraConfig config) {
        this.repo = repo;
        this.ledger = ledger;
        this.config = config;
    }

    @Scheduled(fixedRate = 2000)
    public void matchOrders() {
        if (config.getOperatorParty().isEmpty()) return;

        try {
            List<Map<String, Object>> activeOrders = repo.getActiveOrders();
            if (activeOrders.isEmpty()) return;

            // Separate buys and sells
            List<Map<String, Object>> buys = new ArrayList<>();
            List<Map<String, Object>> sells = new ArrayList<>();

            for (Map<String, Object> order : activeOrders) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) order.get("payload");
                String side = String.valueOf(payload.get("side"));
                if ("Buy".equals(side)) {
                    buys.add(order);
                } else if ("Sell".equals(side)) {
                    sells.add(order);
                }
            }

            // Sort: buys highest price first, sells lowest price first
            buys.sort((a, b) -> Double.compare(getPrice(b), getPrice(a)));
            sells.sort((a, b) -> Double.compare(getPrice(a), getPrice(b)));

            // Match crossing orders
            int bi = 0, si = 0;
            while (bi < buys.size() && si < sells.size()) {
                Map<String, Object> buy = buys.get(bi);
                Map<String, Object> sell = sells.get(si);
                double buyPrice = getPrice(buy);
                double sellPrice = getPrice(sell);

                if (buyPrice < sellPrice) break; // No more crossing orders

                // Check same asset pair
                @SuppressWarnings("unchecked")
                Map<String, Object> buyPayload = (Map<String, Object>) buy.get("payload");
                @SuppressWarnings("unchecked")
                Map<String, Object> sellPayload = (Map<String, Object>) sell.get("payload");

                String buyBase = String.valueOf(buyPayload.get("baseAsset"));
                String buyQuote = String.valueOf(buyPayload.get("quoteAsset"));
                String sellBase = String.valueOf(sellPayload.get("baseAsset"));
                String sellQuote = String.valueOf(sellPayload.get("quoteAsset"));
                if (!buyBase.equals(sellBase) || !buyQuote.equals(sellQuote)) {
                    si++;
                    continue;
                }

                double midPrice = (buyPrice + sellPrice) / 2.0;
                String buyContractId = (String) buy.get("contractId");
                String sellContractId = (String) sell.get("contractId");
                String buyer = String.valueOf(buyPayload.get("trader"));
                String seller = String.valueOf(sellPayload.get("trader"));
                String operator = config.getOperatorParty();
                double matchQuantity = Math.min(getQuantity(buy), getQuantity(sell));
                if (matchQuantity <= 0.0) {
                    logger.warn("Skipping invalid match with non-positive quantity: buy={} sell={}", buyContractId, sellContractId);
                    bi++;
                    si++;
                    continue;
                }

                logger.info("Matching orders: buy={} sell={} at midPrice={} quantity={}", buyContractId, sellContractId, midPrice, matchQuantity);

                try {
                    // Fill the buy order (controller = operator)
                    ValueOuterClass.Value fillBuyArg = recordVal(
                            field("fillPrice", numericVal(midPrice)),
                            field("fillQuantity", numericVal(matchQuantity)),
                            field("counterparty", partyVal(seller))
                    );
                    ledger.exerciseChoice(
                            buyContractId,
                            "Umbra.DarkPool", "SpotOrder",
                            "FillOrder",
                            fillBuyArg,
                            operator
                    ).get(); // blocking - sequential matching is simpler

                    // Fill the sell order (controller = operator)
                    ValueOuterClass.Value fillSellArg = recordVal(
                            field("fillPrice", numericVal(midPrice)),
                            field("fillQuantity", numericVal(matchQuantity)),
                            field("counterparty", partyVal(buyer))
                    );
                    ledger.exerciseChoice(
                            sellContractId,
                            "Umbra.DarkPool", "SpotOrder",
                            "FillOrder",
                            fillSellArg,
                            operator
                    ).get();

                    logger.info("Matched: {} buys from {} at {}", buyBase, seller, midPrice);
                } catch (Exception e) {
                    logger.error("Failed to match orders {} and {}", buyContractId, sellContractId, e);
                }

                bi++;
                si++;
            }
        } catch (Exception e) {
            logger.debug("Matching engine cycle error (may be normal if no contracts exist)", e);
        }
    }

    private double getPrice(Map<String, Object> order) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) order.get("payload");
        return Double.parseDouble(String.valueOf(payload.get("price")));
    }

    private double getQuantity(Map<String, Object> order) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) order.get("payload");
        return Double.parseDouble(String.valueOf(payload.get("quantity")));
    }
}
