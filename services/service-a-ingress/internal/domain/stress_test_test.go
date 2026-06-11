package domain_test

import (
	"errors"
	"testing"
)

// Invariant Model matching Clean Architecture Layer 1. No structural visibility into Outer Databases/Network.
type StressTestEngine struct {
	MinimumCapitalAdequacyRatio float64
}

type BankMetrics struct {
	TenantID            string
	Tier1Capital        float64
	RiskWeightedAssets float64
}

func (e *StressTestEngine) EvaluateTransactionImpact(bank BankMetrics, nominalValue float64, riskWeight float64) (float64, error) {
	if nominalValue <= 0 {
		return 0.0, errors.New("invalid_nominal_value: must be positive")
	}
	if riskWeight < 0.0 || riskWeight > 2.5 {
		return 0.0, errors.New("invalid_risk_weight: parameter out of legal bounds")
	}

	newRWA := bank.RiskWeightedAssets + (nominalValue * riskWeight)
	if newRWA <= 0 {
		return 1.0, nil
	}

	postTxnRatio := bank.Tier1Capital / newRWA
	return postTxnRatio, nil
}

// Unit verification test with zero dependency drivers or mocks.
func TestEvaluateTransactionImpact_CalculatesCorrectRatioAndDetectsViolations(t *testing.T) {
	engine := &StressTestEngine{MinimumCapitalAdequacyRatio: 0.15} // 15% CBN Mandatory threshold

	t.Run("Valid transaction calculates precise risk ratio", func(t *testing.T) {
		bank := BankMetrics{
			TenantID:            "NG-BANK-033",
			Tier1Capital:        50000000.00, // 50M NGN
			RiskWeightedAssets: 200000000.00, // 200M NGN
		}

		// Txn of 10M NGN with a 100% risk weight profile (1.0)
		ratio, err := engine.EvaluateTransactionImpact(bank, 10000000.00, 1.0)
		if err != nil {
			t.Fatalf("Expected zero errors, execution failed with: %v", err)
		}

		expectedRatio := 50000000.00 / (200000000.00 + 10000000.00)
		if ratio != expectedRatio {
			t.Errorf("Mathematical error: expected %f, calculated %f", expectedRatio, ratio)
		}
	})

	t.Run("Rejects invalid negative nominal transactions out of hand", func(t *testing.T) {
		bank := BankMetrics{TenantID: "NG-BANK-011", Tier1Capital: 1000.0, RiskWeightedAssets: 5000.0}
		_, err := engine.EvaluateTransactionImpact(bank, -50000.0, 1.0)
		if err == nil {
			t.Fatal("Domain boundary failure: allowed negative nominal value into ledger calculation")
		}
	})
}