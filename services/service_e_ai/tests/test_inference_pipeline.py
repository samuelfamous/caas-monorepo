import unittest
# Use underscores now!
from services.service_e_ai.src.domain.models import CounterpartyRiskProfile
from services.service_e_ai.src.usecase.synthesis_interactor import (
    CounterpartyRegistryPort, 
    STRRegulatoryOutboxPort, 
    RiskInferenceSTRSynthesisInteractor
)

class MockRegistry(CounterpartyRegistryPort):
    def __init__(self, profile: CounterpartyRiskProfile):
        self.profile = profile
        
    def fetch_profile(self, counterparty_id: str) -> CounterpartyRiskProfile:
        return self.profile

class MockOutbox(STRRegulatoryOutboxPort):
    def __init__(self):
        self.recorded = []
        
    def record_generated_manifest(self, manifest) -> None:
        self.recorded.append(manifest)

class TestAIInferencePipeline(unittest.TestCase):
    def test_high_risk_counterparty_triggers_immediate_str(self):
        profile = CounterpartyRiskProfile(
            counterparty_id="RC-CRITICAL",
            historical_velocity_score=0.90,
            known_pep_status=True,
            jurisdiction_risk_tier="HIGH"
        )
        
        mock_registry = MockRegistry(profile)
        mock_outbox = MockOutbox()
        
        engine = RiskInferenceSTRSynthesisInteractor(mock_registry, mock_outbox)
        
        manifest = engine.evaluate_and_synthesize_str(
            transaction_id="TXN-TEST-101",
            tenant_bank_id="BANK-ALPHA",
            amount_ngn=500000.00,
            asset_classification="RETAIL_EXPOSURE",
            counterparty_id="RC-CRITICAL"
        )
        
        self.assertTrue(manifest.is_immediate_filing_mandated)
        self.assertGreaterEqual(manifest.assigned_risk_score, 0.75)
        self.assertEqual(len(mock_outbox.recorded), 1)

    def test_low_risk_counterparty_remains_clear(self):
        profile = CounterpartyRiskProfile(
            counterparty_id="RC-SAFE",
            historical_velocity_score=0.10,
            known_pep_status=False,
            jurisdiction_risk_tier="LOW"
        )
        
        mock_registry = MockRegistry(profile)
        mock_outbox = MockOutbox()
        
        engine = RiskInferenceSTRSynthesisInteractor(mock_registry, mock_outbox)
        
        manifest = engine.evaluate_and_synthesize_str(
            transaction_id="TXN-TEST-102",
            tenant_bank_id="BANK-ALPHA",
            amount_ngn=2000000.00,
            asset_classification="SOVEREIGN_BONDS",
            counterparty_id="RC-SAFE"
        )
        
        self.assertFalse(manifest.is_immediate_filing_mandated)
        self.assertEqual(manifest.assigned_risk_score, 0.06)