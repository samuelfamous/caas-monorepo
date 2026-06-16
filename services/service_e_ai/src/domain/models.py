from dataclasses import dataclass, field
from datetime import datetime
from typing import Dict, Any, Optional

@dataclass(frozen=True)
class CounterpartyRiskProfile:
    counterparty_id: str
    historical_velocity_score: float  # Normalized numeric value between 0.0 and 1.0
    known_pep_status: bool            # Politically Exposed Person indicator flag
    jurisdiction_risk_tier: str       # HIGH, MEDIUM, LOW

@dataclass(frozen=True)
class RiskEvaluationContext:
    transaction_id: str
    tenant_bank_id: str
    amount_ngn: float
    asset_classification: str
    counterparty: CounterpartyRiskProfile
    timestamp: datetime = field(default_factory=datetime.utcnow)

@dataclass(frozen=True)
class GeneratedSTRManifest:
    str_tracking_id: str
    transaction_id: str
    tenant_bank_id: str
    assigned_risk_score: float
    regulatory_narrative: str
    is_immediate_filing_mandated: bool
    cryptographic_model_version: str