package com.digitalasset.quickstart.umbra;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "umbra")
public class UmbraConfig {

    private static final Logger logger = LoggerFactory.getLogger(UmbraConfig.class);

    private String operatorParty = "";
    private String oracleParty = "";
    private String packageId = "#umbra-protocol";

    @PostConstruct
    public void validate() {
        if (operatorParty == null || operatorParty.isBlank()) {
            logger.warn("umbra.operator-party is not configured; Umbra endpoints will be limited");
        }
        if (oracleParty == null || oracleParty.isBlank()) {
            logger.warn("umbra.oracle-party is not configured; oracle updates will be disabled");
        }
    }

    // Template qualified names for PQS queries
    // These match the DAML module paths: Umbra.DarkPool:SpotOrder etc.
    // UPDATED: Reflects decentralization refactor
    public static final String SPOT_ORDER_TEMPLATE = "Umbra.DarkPool:SpotOrder";
    public static final String BUYER_CONFIRM_TEMPLATE = "Umbra.DarkPool:BuyerConfirm";
    public static final String SELLER_CONFIRM_TEMPLATE = "Umbra.DarkPool:SellerConfirm";
    public static final String DARK_POOL_OPERATOR_TEMPLATE = "Umbra.DarkPool:DarkPoolOperator";
    public static final String LENDING_POOL_TEMPLATE = "Umbra.Lending:LendingPool";
    public static final String SUPPLY_POSITION_TEMPLATE = "Umbra.Lending:SupplyPosition";
    public static final String BORROW_POSITION_TEMPLATE = "Umbra.Lending:BorrowPosition";
    public static final String ORACLE_PRICE_TEMPLATE = "Umbra.Oracle:OraclePrice";
    public static final String ACTIVITY_RECORD_TEMPLATE = "Umbra.ActivityMarker:ActivityRecord";
    public static final String PROTOCOL_PAUSE_TEMPLATE = "Umbra.Types:ProtocolPause";

    public String getOperatorParty() { return operatorParty; }
    public void setOperatorParty(String operatorParty) { this.operatorParty = operatorParty; }
    public String getOracleParty() { return oracleParty; }
    public void setOracleParty(String oracleParty) { this.oracleParty = oracleParty; }
    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }
}
