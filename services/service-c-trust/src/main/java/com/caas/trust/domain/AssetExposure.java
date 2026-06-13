package com.caas.trust.domain;

public enum AssetExposure {
    SOVEREIGN_BONDS(0.00),     // 0% Basel risk weight
    COMMERCIAL_LOANS(1.00),    // 100% Basel risk weight
    RETAIL_EXPOSURE(0.75),     // 75% Basel risk weight
    OFF_BALANCE_SHEET(0.50);   // 50% Basel risk weight

    private final double riskWeightFactor;

    AssetExposure(double riskWeightFactor) {
        this.riskWeightFactor = riskWeightFactor;
    }

    public double getRiskWeightFactor() {
        return this.riskWeightFactor;
    }
}