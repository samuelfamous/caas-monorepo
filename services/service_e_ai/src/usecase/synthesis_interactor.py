import abc
import uuid
from typing import Any
from ..domain.models import RiskEvaluationContext, GeneratedSTRManifest

class CounterpartyRegistryPort(abc.ABC):
    @abc.abstractmethod
    def fetch_profile(self, counterparty_id: str) -> Any:
        pass

class STRRegulatoryOutboxPort(abc.ABC):
    @abc.abstractmethod
    def record_generated_manifest(self, manifest: GeneratedSTRManifest) -> None:
        pass

class RiskInferenceSTRSynthesisInteractor:
    def __init__(self, registry_port: CounterpartyRegistryPort, outbox_port: STRRegulatoryOutboxPort):
        self._registry_port = registry_port
        self._outbox_port = outbox_port
        self._model_signature = "cbn-anomaly-v3.12.2026-prod"

    def evaluate_and_synthesize_str(self, transaction_id: str, tenant_bank_id: str, amount_ngn: float, asset_classification: str, counterparty_id: str) -> GeneratedSTRManifest:
        # Retrieve context from our outbound port adapter boundary
        profile = self._registry_port.fetch_profile(counterparty_id)
        
        # Core Rule 1: Compute cumulative weighted anomaly scores
        base_anomaly_factor = profile.historical_velocity_score * 0.6
        if profile.known_pep_status:
            base_anomaly_factor += 0.3
        if profile.jurisdiction_risk_tier == "HIGH":
            base_anomaly_factor += 0.1
            
        calculated_score = min(1.0, max(0.0, base_anomaly_factor))
        
        # Core Rule 2: Determine if mandatory filings apply under the CBN Shared Fraud Framework
        is_mandated = calculated_score >= 0.75 or amount_ngn >= 10000000.00
        
        # Build out automated regulatory summaries based on compliance requirements
        verdict = "CRITICAL" if is_mandated else "EVALUATED_NORMAL"
        narrative = (
            f"Automated CaaS Intelligence Alert [{verdict}]. Transaction {transaction_id} originating from "
            f"Tenant {tenant_bank_id} evaluated for counterparty {counterparty_id}. "
            f"Computed anomaly coefficient: {calculated_score:.4f}. "
            f"Asset Category: {asset_classification}. Asset valuation threshold met."
        )
        
        str_manifest = GeneratedSTRManifest(
            str_tracking_id=f"STR-MANDATE-2026-{uuid.uuid4().hex[:12].upper()}",
            transaction_id=transaction_id,
            tenant_bank_id=tenant_bank_id,
            assigned_risk_score=calculated_score,
            regulatory_narrative=narrative,
            is_immediate_filing_mandated=is_mandated,
            cryptographic_model_version=self._model_signature
        )
        
        # Persist down via the secondary adapter port boundary
        self._outbox_port.record_generated_manifest(str_manifest)
        return str_manifest