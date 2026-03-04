package com.digitalasset.quickstart.umbra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

import static com.digitalasset.quickstart.umbra.UmbraConfig.*;

/**
 * Repository for querying Umbra contracts from PQS (Postgres Query Store).
 * Returns raw JSON maps — no dependency on generated DAML bindings.
 */
@Repository
public class UmbraRepository {

    private static final Logger logger = LoggerFactory.getLogger(UmbraRepository.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public UmbraRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Dark Pool ──────────────────────────────────────────

    /**
     * Returns all active SpotOrders with status "Open".
     */
    public List<Map<String, Object>> getActiveOrders() {
        String sql = "SELECT contract_id, payload FROM active(?) WHERE payload->>'status' = 'Open'";
        try {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, SPOT_ORDER_TEMPLATE);
        } catch (Exception e) {
            logger.debug("SpotOrder template not yet available in PQS", e);
            return List.of();
        }
    }

    public List<Map<String, Object>> getActiveOrdersForTrader(String trader) {
        String sql = "SELECT contract_id, payload FROM active(?) WHERE payload->>'status' = 'Open' AND payload->>'trader' = ?";
        try {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, SPOT_ORDER_TEMPLATE, trader);
        } catch (Exception e) {
            logger.debug("SpotOrder template not yet available in PQS", e);
            return List.of();
        }
    }

    /**
     * Aggregated orderbook: buys and sells grouped by price level, no trader info.
     */
    public Map<String, Object> getOrderBook() {
        List<Map<String, Object>> orders = getActiveOrders();
        List<Map<String, Object>> buys = new ArrayList<>();
        List<Map<String, Object>> sells = new ArrayList<>();

        // Group by side and price
        Map<String, Double> buyAgg = new TreeMap<>(Comparator.reverseOrder());
        Map<String, Double> sellAgg = new TreeMap<>();

        for (Map<String, Object> order : orders) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) order.get("payload");
            String side = String.valueOf(payload.get("side"));
            double price = Double.parseDouble(String.valueOf(payload.get("price")));
            double qty = Double.parseDouble(String.valueOf(payload.get("quantity")));
            String priceKey = String.valueOf(price);

            if ("Buy".equals(side)) {
                buyAgg.merge(priceKey, qty, Double::sum);
            } else {
                sellAgg.merge(priceKey, qty, Double::sum);
            }
        }

        for (var e : buyAgg.entrySet()) {
            buys.add(Map.<String, Object>of("price", Double.parseDouble(e.getKey()), "quantity", e.getValue()));
        }
        for (var e : sellAgg.entrySet()) {
            sells.add(Map.<String, Object>of("price", Double.parseDouble(e.getKey()), "quantity", e.getValue()));
        }

        return Map.<String, Object>of(
                "buys", buys,
                "sells", sells,
                "bids", buys,
                "asks", sells
        );
    }

    /**
     * Get trade confirms for a specific trader.
     */
    public List<Map<String, Object>> getTradesForTrader(String trader) {
        String sql = "SELECT contract_id, payload FROM active(?) WHERE payload->>'buyer' = ? OR payload->>'seller' = ?";
        try {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, TRADE_CONFIRM_TEMPLATE, trader, trader);
        } catch (Exception e) {
            logger.debug("TradeConfirm template not yet available in PQS", e);
            return List.of();
        }
    }

    // ── Lending ────────────────────────────────────────────

    /**
     * Get the active LendingPool contract (expects exactly one).
     */
    public Optional<Map<String, Object>> getLendingPool() {
        String sql = "SELECT contract_id, payload FROM active(?) LIMIT 1";
        try {
            List<Map<String, Object>> results = jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, LENDING_POOL_TEMPLATE);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            logger.debug("LendingPool template not yet available in PQS", e);
            return Optional.empty();
        }
    }

    /**
     * Get supply positions for a trader.
     */
    public List<Map<String, Object>> getSupplyPositions(String trader) {
        String sql = "SELECT contract_id, payload FROM active(?) WHERE payload->>'supplier' = ?";
        try {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, SUPPLY_POSITION_TEMPLATE, trader);
        } catch (Exception e) {
            logger.debug("SupplyPosition template not yet available in PQS", e);
            return List.of();
        }
    }

    /**
     * Get borrow positions for a trader.
     */
    public List<Map<String, Object>> getBorrowPositions(String trader) {
        String sql = "SELECT contract_id, payload FROM active(?) WHERE payload->>'borrower' = ?";
        try {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, BORROW_POSITION_TEMPLATE, trader);
        } catch (Exception e) {
            logger.debug("BorrowPosition template not yet available in PQS", e);
            return List.of();
        }
    }

    /**
     * Get all borrow positions (for liquidation monitoring).
     */
    public List<Map<String, Object>> getAllBorrowPositions() {
        String sql = "SELECT contract_id, payload FROM active(?)";
        try {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, BORROW_POSITION_TEMPLATE);
        } catch (Exception e) {
            logger.debug("BorrowPosition template not yet available in PQS", e);
            return List.of();
        }
    }

    // ── Oracle ─────────────────────────────────────────────

    /**
     * Get the current oracle price for an asset.
     */
    public Optional<Map<String, Object>> getOraclePrice(String asset) {
        String sql = "SELECT contract_id, payload FROM active(?) WHERE payload->>'asset' = ?";
        try {
            List<Map<String, Object>> results = jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, ORACLE_PRICE_TEMPLATE, asset);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            logger.debug("OraclePrice template not yet available in PQS", e);
            return Optional.empty();
        }
    }

    /**
     * Get all oracle prices.
     */
    public List<Map<String, Object>> getAllOraclePrices() {
        String sql = "SELECT contract_id, payload FROM active(?)";
        try {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, ORACLE_PRICE_TEMPLATE);
        } catch (Exception e) {
            logger.debug("OraclePrice template not yet available in PQS", e);
            return List.of();
        }
    }

    // ── DarkPoolOperator ───────────────────────────────────

    /**
     * Get the DarkPoolOperator contract.
     */
    public Optional<Map<String, Object>> getDarkPoolOperator() {
        String sql = "SELECT contract_id, payload FROM active(?) LIMIT 1";
        try {
            List<Map<String, Object>> results = jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, DARK_POOL_OPERATOR_TEMPLATE);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            logger.debug("DarkPoolOperator template not yet available in PQS", e);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            logger.error("Failed to parse JSON payload", e);
            return Map.of();
        }
    }
}
