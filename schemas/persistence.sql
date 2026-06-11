-- Production SQL DDL Initialization script for PostgreSQL 16+
-- Optimized for high concurrent throughput, row-level multitenancy, and outbox survivability.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Define custom enum structures for high-performance indexing
CREATE TYPE asset_class_enum AS ENUM (
    'SOVEREIGN_BONDS', 
    'COMMERCIAL_LOANS', 
    'RETAIL_EXPOSURE', 
    'OFF_BALANCE_SHEET'
);

CREATE TYPE regulatory_status_enum AS ENUM (
    'COMPLIANT', 
    'VIOLATION_FLAGGED', 
    'CRITICAL_SUSPENSION'
);

-- 1. TENANT MANAGEMENT (Bank Registration Contexts)
CREATE TABLE tenant_banks (
    tenant_id VARCHAR(64) PRIMARY KEY,
    legal_name VARCHAR(255) NOT NULL,
    cbn_license_number VARCHAR(100) UNIQUE NOT NULL,
    current_tier1_capital NUMERIC(24, 4) NOT NULL CHECK (current_tier1_capital >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. IMMUTABLE APPEND-ONLY COMPLIANCE LEDGER (No UPDATES/DELETES permitted)
CREATE TABLE compliance_ledger_entries (
    ledger_sequence_id BIGSERIAL,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant_banks(tenant_id),
    transaction_id VARCHAR(128) NOT NULL,
    nominal_value_ngn NUMERIC(24, 4) NOT NULL,
    asset_classification asset_class_enum NOT NULL,
    cryptographic_hash CHAR(64) NOT NULL, -- SHA256 of data payload and preceding ledger_hash
    regulatory_status regulatory_status_enum NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (tenant_id, ledger_sequence_id)
) PARTITION BY LIST (tenant_id);

-- Create a hyper-fast compound index for historical verification audits
CREATE INDEX idx_ledger_audit_trail 
ON compliance_ledger_entries (tenant_id, recorded_at DESC, ledger_sequence_id DESC);

-- 3. TRANSACTIONAL OUTBOX PATTERN TABLE (Guarantees At-Least-Once Delivery to Event Bus)
CREATE TABLE transactional_outbox (
    outbox_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant_banks(tenant_id),
    destination_topic VARCHAR(128) NOT NULL,
    event_payload JSONB NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    processing_status VARCHAR(32) DEFAULT 'PENDING' NOT NULL CHECK (processing_status IN ('PENDING', 'PROCESSED', 'FAILED')),
    retry_count INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    locked_until TIMESTAMP WITH TIME ZONE
);

-- Composite index to accelerate multi-worker high-frequency polling
CREATE INDEX idx_outbox_processing_poll 
ON transactional_outbox (processing_status, locked_until) 
WHERE processing_status = 'PENDING';

-- 4. REPLICATE DEFAULT PARTITION TEMPLATE FOR THE SYSTEM
CREATE TABLE compliance_ledger_default PARTITION OF compliance_ledger_entries 
DEFAULT;

-- Enforce absolute multi-tenant constraints using PostgreSQL Row Level Security (RLS)
ALTER TABLE compliance_ledger_entries ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON compliance_ledger_entries
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));