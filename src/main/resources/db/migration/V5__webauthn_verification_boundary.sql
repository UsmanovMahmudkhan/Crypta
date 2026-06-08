ALTER TABLE webauthn_challenges
    ADD COLUMN request_options_json jsonb;

ALTER TABLE webauthn_credentials
    ADD COLUMN verification_status text NOT NULL DEFAULT 'LEGACY_DEMO';

CREATE INDEX idx_webauthn_credentials_verified_user
    ON webauthn_credentials(user_id, credential_id)
    WHERE verification_status = 'VERIFIED';
