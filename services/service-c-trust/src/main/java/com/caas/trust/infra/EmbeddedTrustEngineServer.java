package com.caas.trust.infra; // Cleaned package path

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import com.caas.trust.domain.AssetExposure;
import com.caas.trust.domain.FinancialLedgerRepositoryPort;
import com.caas.trust.domain.FinancialProfile;
import com.caas.trust.usecase.ComputeResilienceScoreInteractor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@SuppressWarnings("unused")
public final class EmbeddedTrustEngineServer {

    // Concrete In-Memory Secondary Adapter implementing our outbound Use Case Port
    private static class VolatileLedgerDatabaseAdapter implements FinancialLedgerRepositoryPort {
        private final Map<String, FinancialProfile> tableStore = new ConcurrentHashMap<>();

        public VolatileLedgerDatabaseAdapter() {
            // Seed a commercial banking node running low on baseline capital reserves
            tableStore.put("NG-BANK-033", new FinancialProfile("NG-BANK-033", 45000000000L, 280000000000L, 0.04));
        }

        @Override
        public Optional<FinancialProfile> findProfileByTenant(String tenantBankId) {
            return Optional.ofNullable(tableStore.get(tenantBankId));
        }

        @Override
        public void saveUpdatedProfile(FinancialProfile profile) {
            tableStore.put(profile.getTenantBankId(), profile);
            System.out.printf("[TRUST ARCHITECTURE ADAPTER] Persisted updated capital exposure metrics for bank %s.%n", profile.getTenantBankId());
        }

        @Override
        public void saveUpdatedProfile11(FinancialProfile profile) {
            // No longer needed
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Starting CaaS Trust Scoring & Capital Resilience Engine Engine Node...");

        // Wire up dependencies according to Clean Architecture guidelines
        FinancialLedgerRepositoryPort databaseAdapter = new VolatileLedgerDatabaseAdapter();
        ComputeResilienceScoreInteractor interactor = new ComputeResilienceScoreInteractor(databaseAdapter);

        HttpServer server = HttpServer.create(new InetSocketAddress(8083), 0);
        
        server.createContext("/api/v1/trust/evaluate", (HttpExchange exchange) -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                // Parse incoming request parameters directly
                String query = new String(exchange.getRequestBody().readAllBytes());
                Map<String, String> parameters = parseFormData(query);

                String tenantBankId = parameters.getOrDefault("tenantBankId", "NG-BANK-033");
                long txnAmountKobo = Long.parseLong(parameters.getOrDefault("amountKobo", "1000000000"));
                AssetExposure assetType = AssetExposure.valueOf(parameters.getOrDefault("assetClass", "COMMERCIAL_LOANS"));

                // Execute the isolated use case layer and bind directly to the clean Record type
                ComputeResilienceScoreInteractor.TrustScoreAssessment assessment = 
                    interactor.execute(tenantBankId, txnAmountKobo, assetType);

                String jsonResponse = String.format(
                    "{\"tenantBankId\":\"%s\",\"trustScore\":%.2f,\"projectedCar\":%.4f,\"cbnCompliant\":%b}",
                    assessment.tenantBankId(),
                    assessment.calculatedTrustScore(),
                    assessment.projectedCapitalAdequacyRatio(),
                    assessment.compliesWithRiskBasedCapitalMandates()
                );

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }
            } catch (Exception exc) {
                String errorPayload = "{\"error\":\"Parsing and verification runtime crash: " + exc.getMessage() + "\"}";
                exchange.sendResponseHeaders(400, errorPayload.length());
                exchange.getResponseBody().write(errorPayload.getBytes());
            }
        });

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); // Project Loom Virtual Threads
        server.start();
        System.out.println("Trust Engine Operational Node is listening on port :8083 via low-overhead virtual threads.");
    }

    private static Map<String, String> parseFormData(String body) {
        Map<String, String> result = new HashMap<>();
        if (body == null || body.isBlank()) return result;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) result.put(kv[0].trim(), kv[1].trim());
        }
        return result;
    }
}