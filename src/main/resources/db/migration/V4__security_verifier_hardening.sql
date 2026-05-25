ALTER TABLE key_transparency_entries
    ADD COLUMN consistency_proof bytea,
    ADD COLUMN checkpoint_id text NOT NULL DEFAULT 'legacy-local-checkpoint',
    ADD COLUMN proof_version text NOT NULL DEFAULT 'legacy-hash-chain-v0';

CREATE INDEX idx_key_transparency_entries_org_log
    ON key_transparency_entries(organization_id, log_index);

ALTER TABLE api_sessions
    ADD COLUMN token_hash_version text NOT NULL DEFAULT 'sha256-or-hmac-sha256-v1',
    ADD COLUMN idle_timeout_minutes integer NOT NULL DEFAULT 60;

CREATE TABLE security_verifier_checks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid REFERENCES organizations(id),
    check_type text NOT NULL,
    status text NOT NULL,
    verifier_checkpoint_id text,
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_security_verifier_checks_org_type
    ON security_verifier_checks(organization_id, check_type, created_at DESC);
