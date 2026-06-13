package com.caas.trust.domain;

import java.util.Optional;

public interface FinancialLedgerRepositoryPort {
    Optional<FinancialProfile> findProfileByTenant(String tenantBankId);
    void saveUpdatedProfile(FinancialProfile profile);
    void saveUpdatedProfile11(FinancialProfile profile);
}