package com.caas.trust.usecase; // Cleaned package header

import com.caas.trust.domain.FinancialLedgerRepositoryPort; // Cleaned imports
import com.caas.trust.domain.AssetExposure;
import com.caas.trust.domain.FinancialProfile;

public final class ComputeResilienceScoreInteractor {
    private final FinancialLedgerRepositoryPort ledgerRepository;
    private static final double MANDATORY_CBN_CAR_THRESHOLD = 0.15; // 15% Minimum CAR for International Banking Licenses

    public ComputeResilienceScoreInteractor(FinancialLedgerRepositoryPort ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    public TrustScoreAssessment execute(String tenantBankId, long transactionValueKobo, AssetExposure assetClass) {
        FinancialProfile profile = ledgerRepository.findProfileByTenant(tenantBankId)
            .orElseThrow(() -> new IllegalStateException("Tenant Bank is not configured in the CaaS Trust network directory."));

        // Step 1: Calculate the incremental Risk Weighted Asset (RWA) impact
        long incrementalRwaKobo = Math.round(transactionValueKobo * assetClass.getRiskWeightFactor());
        long simulatedTotalRwaKobo = profile.getTotalRiskWeightedAssetsKobo() + incrementalRwaKobo;

        // Step 2: Recalculate the post-transaction Capital Adequacy Ratio (CAR)
        FinancialProfile simulatedProfile = new FinancialProfile(
            profile.getTenantBankId(),
            profile.getTotalLiquidAssetsKobo(),
            simulatedTotalRwaKobo,
            profile.getHistoricalDefaultRate()
        );

        double postTransactionCar = simulatedProfile.computeCapitalAdequacyRatio();
        boolean isPermittedByCbn = postTransactionCar >= MANDATORY_CBN_CAR_THRESHOLD;

        // Step 3: Compute a composite trust coefficient score [0.0 - 100.0]
        double confidenceFactor = (postTransactionCar / MANDATORY_CBN_CAR_THRESHOLD) * 50.0;
        double defaultPenalty = (1.0 - profile.getHistoricalDefaultRate()) * 50.0;
        double finalTrustScore = Math.min(100.0, Math.max(0.0, confidenceFactor + defaultPenalty));

        TrustScoreAssessment assessment = new TrustScoreAssessment(
            tenantBankId,
            finalTrustScore,
            postTransactionCar,
            isPermittedByCbn
        );

        // Update ledger state records through our outbox gateway adapter boundary
        ledgerRepository.saveUpdatedProfile(simulatedProfile);

        return assessment;
    }

    public static record TrustScoreAssessment(
        String tenantBankId,
        double calculatedTrustScore,
        double projectedCapitalAdequacyRatio,
        boolean compliesWithRiskBasedCapitalMandates
    ) {}
}