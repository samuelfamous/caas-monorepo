package com.caas.trust.domain;

public final class FinancialProfile {
    private final String tenantBankId;
    private final long totalLiquidAssetsKobo; // Track currency in Kobo to eliminate float roundings
    private final long totalRiskWeightedAssetsKobo;
    private final double historicalDefaultRate;

    public FinancialProfile(String tenantBankId, long totalLiquidAssetsKobo, long totalRiskWeightedAssetsKobo, double historicalDefaultRate) {
        if (tenantBankId == null || tenantBankId.isBlank()) {
            throw new IllegalArgumentException("Domain Invariant Broken: Tenant Bank Identifier cannot be empty.");
        }
        this.tenantBankId = tenantBankId;
        this.totalLiquidAssetsKobo = totalLiquidAssetsKobo;
        this.totalRiskWeightedAssetsKobo = totalRiskWeightedAssetsKobo;
        this.historicalDefaultRate = historicalDefaultRate;
    }

    public String getTenantBankId() { return tenantBankId; }
    public long getTotalLiquidAssetsKobo() { return totalLiquidAssetsKobo; }
    public long getTotalRiskWeightedAssetsKobo() { return totalRiskWeightedAssetsKobo; }
    public double getHistoricalDefaultRate() { return historicalDefaultRate; }

    /**
     * Mathematical implementation of Capital Adequacy Ratio (CAR) calculation:
     * CAR = Total Eligible Capital / Total Risk Weighted Assets
     */
    public double computeCapitalAdequacyRatio() {
        if (totalRiskWeightedAssetsKobo == 0) return 1.0;
        return (double) totalLiquidAssetsKobo / totalRiskWeightedAssetsKobo;
    }
}