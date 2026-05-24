CREATE TABLE webauthn_challenges (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ceremony_type text NOT NULL,
    challenge_hash bytea NOT NULL UNIQUE,
    public_challenge text NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (ceremony_type IN ('REGISTRATION', 'LOGIN'))
);

CREATE TABLE api_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id uuid REFERENCES devices(id) ON DELETE SET NULL,
    token_hash bytea NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz
);

CREATE TABLE idempotency_keys (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid REFERENCES organizations(id),
    actor_user_id uuid REFERENCES users(id),
    idempotency_key text NOT NULL,
    request_fingerprint bytea NOT NULL,
    response_body jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    UNIQUE (actor_user_id, idempotency_key)
);

CREATE INDEX idx_webauthn_challenges_user_active
    ON webauthn_challenges(user_id, ceremony_type, expires_at)
    WHERE consumed_at IS NULL;

CREATE INDEX idx_api_sessions_active
    ON api_sessions(token_hash, expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_idempotency_expiry
    ON idempotency_keys(expires_at);
