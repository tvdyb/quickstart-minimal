// Copyright (c) 2026, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: 0BSD
package com.digitalasset.quickstart.service;

import com.daml.ledger.api.v2.PackageServiceGrpc;
import com.daml.ledger.api.v2.PackageServiceOuterClass;
import com.digitalasset.quickstart.config.LedgerConfig;
import com.digitalasset.quickstart.security.Auth;
import com.digitalasset.quickstart.security.AuthenticatedPartyProvider;
import com.digitalasset.quickstart.security.AuthenticatedUserProvider;
import com.digitalasset.quickstart.security.TokenProvider;
import com.digitalasset.quickstart.umbra.UmbraConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("${openapi.asset.base-path:}")
public class DebugLedgerController {
    private static final Logger logger = LoggerFactory.getLogger(DebugLedgerController.class);

    private final LedgerConfig ledgerConfig;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final AuthenticatedPartyProvider authenticatedPartyProvider;
    private final Optional<TokenProvider> tokenProvider;
    private final Auth auth;

    @Autowired
    public DebugLedgerController(
            LedgerConfig ledgerConfig,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AuthenticatedUserProvider authenticatedUserProvider,
            AuthenticatedPartyProvider authenticatedPartyProvider,
            Optional<TokenProvider> tokenProvider,
            Auth auth
    ) {
        this.ledgerConfig = ledgerConfig;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.authenticatedPartyProvider = authenticatedPartyProvider;
        this.tokenProvider = tokenProvider;
        this.auth = auth;
    }

    @GetMapping("/debug/ledger")
    public ResponseEntity<Map<String, Object>> getLedgerDiagnostics(
            @RequestParam(name = "sampleLimit", defaultValue = "5") int sampleLimit,
            @RequestParam(name = "eventLimit", defaultValue = "20") int eventLimit
    ) {
        int safeSampleLimit = Math.max(1, Math.min(sampleLimit, 25));
        int safeEventLimit = Math.max(1, Math.min(eventLimit, 100));
        long started = System.currentTimeMillis();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("timestamp", new Date().toInstant().toString());
        out.put("authMode", auth.name().toLowerCase(Locale.ROOT));

        out.put("identity", buildIdentity());
        out.put("backend", buildBackendMeta());

        Map<String, Object> postgresHealth = pingPostgres();
        Map<String, Object> ledgerDiagnostics = inspectLedgerPackages();
        Map<String, Object> templatesAndContracts = inspectTemplatesAndContracts(safeSampleLimit);
        List<Map<String, Object>> recentEvents = loadRecentContractEvents(safeEventLimit);

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("backend", Map.of("ok", true));
        health.put("postgres", postgresHealth);
        Map<String, Object> ledgerHealth = new LinkedHashMap<>();
        ledgerHealth.put("ok", Boolean.TRUE.equals(ledgerDiagnostics.get("reachable")));
        ledgerHealth.put("latencyMs", ledgerDiagnostics.get("latencyMs"));
        ledgerHealth.put("error", ledgerDiagnostics.get("error"));
        health.put("ledger", ledgerHealth);
        out.put("health", health);
        out.put("ledger", ledgerDiagnostics);
        out.put("templates", templatesAndContracts.get("templates"));
        out.put("activeContracts", templatesAndContracts.get("activeContracts"));
        out.put("recentEvents", recentEvents);

        out.put("latencyMs", System.currentTimeMillis() - started);
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> buildIdentity() {
        Map<String, Object> identity = new LinkedHashMap<>();
        Optional<AuthenticatedUserProvider.AuthenticatedUser> user = authenticatedUserProvider.getUser();
        identity.put("authenticated", user.isPresent());
        identity.put("partyFromContext", authenticatedPartyProvider.getParty().orElse(null));
        identity.put("user", user.map(u -> {
            Map<String, Object> userMap = new LinkedHashMap<>();
            userMap.put("name", u.username());
            userMap.put("party", u.partyId());
            userMap.put("tenant", u.tenantId());
            userMap.put("roles", u.roles());
            userMap.put("isAdmin", u.isAdmin());
            return userMap;
        }).orElse(null));
        return identity;
    }

    private Map<String, Object> buildBackendMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reachable", true);
        meta.put("ledgerHost", ledgerConfig.getHost());
        meta.put("ledgerPort", ledgerConfig.getPort());
        meta.put("applicationId", ledgerConfig.getApplicationId());
        meta.put("registryBaseUri", ledgerConfig.getRegistryBaseUri());
        return meta;
    }

    private Map<String, Object> pingPostgres() {
        long started = System.currentTimeMillis();
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            out.put("ok", one != null && one == 1);
            out.put("latencyMs", System.currentTimeMillis() - started);
        } catch (Exception e) {
            out.put("ok", false);
            out.put("latencyMs", System.currentTimeMillis() - started);
            out.put("error", e.getMessage());
        }
        return out;
    }

    private Map<String, Object> inspectLedgerPackages() {
        long started = System.currentTimeMillis();
        Map<String, Object> out = new LinkedHashMap<>();
        ManagedChannel channel = null;
        try {
            ManagedChannelBuilder<?> builder = ManagedChannelBuilder
                    .forAddress(ledgerConfig.getHost(), ledgerConfig.getPort())
                    .usePlaintext();
            tokenProvider.ifPresent(tp -> builder.intercept(new AuthInterceptor(tp)));
            channel = builder.build();

            PackageServiceGrpc.PackageServiceFutureStub packageService =
                    PackageServiceGrpc.newFutureStub(channel).withDeadlineAfter(7, TimeUnit.SECONDS);
            PackageServiceOuterClass.ListPackagesResponse response =
                    packageService.listPackages(PackageServiceOuterClass.ListPackagesRequest.newBuilder().build()).get();

            List<String> packageIds = response.getPackageIdsList();
            out.put("reachable", true);
            out.put("latencyMs", System.currentTimeMillis() - started);
            out.put("packageCount", packageIds.size());
            out.put("packageIds", packageIds);
        } catch (Exception e) {
            logger.warn("Ledger package inspection failed", e);
            out.put("reachable", false);
            out.put("latencyMs", System.currentTimeMillis() - started);
            out.put("error", e.getMessage());
            out.put("packageCount", 0);
            out.put("packageIds", List.of());
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
        }
        return out;
    }

    private Map<String, Object> inspectTemplatesAndContracts(int sampleLimit) {
        Map<String, Object> out = new LinkedHashMap<>();
        Set<String> templates = new TreeSet<>();
        templates.addAll(knownTemplateNames());

        try {
            List<String> discovered = jdbcTemplate.query(
                    "SELECT name FROM __contract_tpe ORDER BY name",
                    (rs, i) -> rs.getString("name")
            );
            templates.addAll(discovered);
        } catch (Exception e) {
            logger.warn("Could not inspect __contract_tpe table", e);
        }

        List<Map<String, Object>> contractStats = new ArrayList<>();
        for (String template : templates) {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("template", template);
            try {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM active(?)",
                        Long.class,
                        template
                );
                stat.put("activeCount", count == null ? 0L : count);
                stat.put("sample", sampleActiveContracts(template, sampleLimit));
            } catch (Exception e) {
                stat.put("activeCount", null);
                stat.put("sample", List.of());
                stat.put("error", e.getMessage());
            }
            contractStats.add(stat);
        }

        out.put("templates", templates);
        out.put("activeContracts", contractStats);
        return out;
    }

    private List<Map<String, Object>> sampleActiveContracts(String template, int limit) {
        try {
            return jdbcTemplate.query(
                    "SELECT contract_id, payload FROM active(?) LIMIT ?",
                    (rs, i) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("contractId", rs.getString("contract_id"));
                        row.put("payload", parsePayload(rs.getString("payload")));
                        return row;
                    },
                    template, limit
            );
        } catch (Exception e) {
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    private List<Map<String, Object>> loadRecentContractEvents(int limit) {
        if (!tableExists("__contracts")) {
            return List.of(Map.of("status", "Unavailable", "reason", "__contracts table not found"));
        }

        String templateColumn = firstExistingColumn("__contracts", List.of(
                "template_id_qualified_name",
                "template_id",
                "template"
        ));
        String payloadColumn = firstExistingColumn("__contracts", List.of("payload"));
        String contractIdColumn = firstExistingColumn("__contracts", List.of("contract_id", "id"));
        String createdAtColumn = firstExistingColumn("__contracts", List.of("created_at"));
        String archivedAtColumn = firstExistingColumn("__contracts", List.of("archived_at"));

        if (templateColumn == null || contractIdColumn == null) {
            return List.of(Map.of("status", "Unavailable", "reason", "Expected columns not found in __contracts"));
        }

        StringBuilder select = new StringBuilder("SELECT ")
                .append(contractIdColumn).append(" AS contract_id, ")
                .append(templateColumn).append(" AS template_name");
        if (createdAtColumn != null) select.append(", ").append(createdAtColumn).append(" AS created_at");
        if (archivedAtColumn != null) select.append(", ").append(archivedAtColumn).append(" AS archived_at");
        if (payloadColumn != null) select.append(", ").append(payloadColumn).append(" AS payload");

        String orderColumn = createdAtColumn != null ? "created_at" : "contract_id";
        String sql = select
                .append(" FROM __contracts ORDER BY ").append(orderColumn).append(" DESC LIMIT ?")
                .toString();

        try {
            return jdbcTemplate.query(sql, (rs, i) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("contractId", rs.getString("contract_id"));
                row.put("template", rs.getString("template_name"));
                if (createdAtColumn != null) row.put("createdAt", rs.getObject("created_at"));
                if (archivedAtColumn != null) row.put("archivedAt", rs.getObject("archived_at"));
                if (payloadColumn != null) row.put("payload", parsePayload(rs.getString("payload")));
                Object archivedAt = archivedAtColumn != null ? rs.getObject("archived_at") : null;
                row.put("eventType", archivedAt == null ? "created_or_active" : "archived");
                return row;
            }, limit);
        } catch (Exception e) {
            return List.of(Map.of("status", "Error", "reason", e.getMessage()));
        }
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                    Integer.class,
                    tableName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String firstExistingColumn(String tableName, List<String> candidates) {
        try {
            Set<String> columns = new HashSet<>(jdbcTemplate.query(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=?",
                    (rs, i) -> rs.getString("column_name"),
                    tableName
            ));
            for (String candidate : candidates) {
                if (columns.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Object parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return null;
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return payloadJson;
        }
    }

    private List<String> knownTemplateNames() {
        return List.of(
                "Licensing.AppInstall:AppInstall",
                "Licensing.AppInstall:AppInstallRequest",
                "Licensing.License:License",
                "Licensing.License:LicenseRenewalRequest",
                UmbraConfig.ACTIVITY_RECORD_TEMPLATE,
                UmbraConfig.BORROW_POSITION_TEMPLATE,
                UmbraConfig.DARK_POOL_OPERATOR_TEMPLATE,
                UmbraConfig.LENDING_POOL_TEMPLATE,
                UmbraConfig.ORACLE_PRICE_TEMPLATE,
                UmbraConfig.SPOT_ORDER_TEMPLATE,
                UmbraConfig.SUPPLY_POSITION_TEMPLATE,
                UmbraConfig.TRADE_CONFIRM_TEMPLATE
        );
    }

    private static class AuthInterceptor implements ClientInterceptor {
        private static final Metadata.Key<String> AUTH_HEADER =
                Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
        private final TokenProvider tokenProvider;

        AuthInterceptor(TokenProvider tokenProvider) {
            this.tokenProvider = tokenProvider;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next
        ) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    headers.put(AUTH_HEADER, "Bearer " + tokenProvider.getToken());
                    super.start(responseListener, headers);
                }
            };
        }
    }
}
