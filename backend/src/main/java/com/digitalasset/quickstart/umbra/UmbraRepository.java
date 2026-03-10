package com.digitalasset.quickstart.umbra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
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
    // ObjectReader is thread-safe, unlike ObjectMapper for read operations with shared config
    private static final ObjectReader mapReader = new ObjectMapper().readerFor(Map.class);

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
     * Get buyer confirmations for a specific buyer.
     * PRIVACY: Only returns trades where this party was the buyer.
     */
    public List<Map<String, Object>> getBuyerConfirms(String buyer) {
        String sql = "SELECT contract_id, payload FROM active(?) WHERE payload->>'buyer' = ?";
        try {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                row.put("side", "buy");  // Mark as buy side
                return row;
            }, BUYER_CONFIRM_TEMPLATE, buyer);
        } catch (Exception e) {
            logger.debug("BuyerConfirm template not yet available in PQS", e);
            return List.of();
        }
    }

    /**
     * Get seller confirmations for a specific seller.
     * PRIVACY: Only returns trades where this party was the seller.
     */
    public List<Map<String, Object>> getSellerConfirms(String seller) {
        String sql = "SELECT contract_id, payload FROM active(?) WHERE payload->>'seller' = ?";
        try {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                row.put("side", "sell");  // Mark as sell side
                return row;
            }, SELLER_CONFIRM_TEMPLATE, seller);
        } catch (Exception e) {
            logger.debug("SellerConfirm template not yet available in PQS", e);
            return List.of();
        }
    }

    /**
     * Get all trade confirmations for a trader (both as buyer and seller).
     * PRIVACY: Each party only sees their own side - no counterparty information exposed.
     */
    public List<Map<String, Object>> getTradesForTrader(String trader) {
        List<Map<String, Object>> trades = new ArrayList<>();

        // Get buyer confirms (trades where this party bought)
        trades.addAll(getBuyerConfirms(trader));

        // Get seller confirms (trades where this party sold)
        trades.addAll(getSellerConfirms(trader));

        return trades;
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

    // ── ProtocolPause ─────────────────────────────────────

    public Optional<Map<String, Object>> getProtocolPause() {
        String sql = "SELECT contract_id, payload FROM active(?) LIMIT 1";
        try {
            List<Map<String, Object>> results = jdbc.query(sql, (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("payload", parseJson(rs.getString("payload")));
                return row;
            }, PROTOCOL_PAUSE_TEMPLATE);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            logger.debug("ProtocolPause template not yet available in PQS", e);
            return Optional.empty();
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
            return mapReader.readValue(json);
        } catch (Exception e) {
            logger.error("Failed to parse JSON payload", e);
            return Map.of();
        }
    }
}
