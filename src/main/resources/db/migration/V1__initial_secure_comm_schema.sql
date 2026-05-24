CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE organizations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    jurisdiction text NOT NULL,
    external_tenant_id text,
    status text NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    email citext NOT NULL,
    display_name text NOT NULL,
    status text NOT NULL DEFAULT 'INVITED',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, email)
);

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE devices (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    platform text NOT NULL,
    device_name text NOT NULL,
    device_signing_public_key bytea NOT NULL,
    trust_state text NOT NULL DEFAULT 'PENDING_VERIFICATION',
    hardware_backed boolean NOT NULL DEFAULT false,
    strongbox_or_secure_enclave boolean NOT NULL DEFAULT false,
    mdm_compliant boolean,
    revoked_at timestamptz,
    revoke_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE device_attestations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    attestation_format text NOT NULL,
    attestation_statement bytea NOT NULL,
    verified_claims jsonb NOT NULL DEFAULT '{}'::jsonb,
    verification_status text NOT NULL,
    verified_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE webauthn_credentials (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_id bytea NOT NULL UNIQUE,
    public_key_cose bytea NOT NULL,
    signature_count bigint NOT NULL DEFAULT 0,
    transports text[] NOT NULL DEFAULT '{}',
    attestation_type text,
    backup_eligible boolean,
    backup_state boolean,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz
);

CREATE TABLE identity_public_keys (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    algorithm text NOT NULL,
    public_key bytea NOT NULL,
    signature bytea NOT NULL,
    key_version integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, key_version)
);

CREATE TABLE signed_prekeys (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    key_id text NOT NULL,
    algorithm text NOT NULL,
    public_key bytea NOT NULL,
    signature bytea NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, key_id)
);

CREATE TABLE one_time_prekeys (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    key_id text NOT NULL,
    algorithm text NOT NULL,
    public_key bytea NOT NULL,
    claimed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, key_id)
);

CREATE TABLE pq_prekeys (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    key_id text NOT NULL,
    algorithm text NOT NULL,
    public_key bytea NOT NULL,
    signature bytea NOT NULL,
    last_resort boolean NOT NULL DEFAULT false,
    claimed_at timestamptz,
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (device_id, key_id)
);

CREATE TABLE key_transparency_entries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    subject_user_id uuid NOT NULL REFERENCES users(id),
    subject_device_id uuid REFERENCES devices(id),
    entry_type text NOT NULL,
    canonical_entry_hash bytea NOT NULL,
    previous_entry_hash bytea,
    merkle_leaf_hash bytea NOT NULL,
    log_index bigint NOT NULL UNIQUE,
    signed_tree_head bytea NOT NULL,
    inclusion_proof jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE rooms (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    name text NOT NULL,
    classification text NOT NULL,
    room_type text NOT NULL DEFAULT 'MISSION_ROOM',
    mls_group_id text,
    legal_hold_enabled boolean NOT NULL DEFAULT false,
    lockdown_state text NOT NULL DEFAULT 'NORMAL',
    created_by uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE room_members (
    room_id uuid NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id),
    role text NOT NULL DEFAULT 'MEMBER',
    membership_state text NOT NULL DEFAULT 'ACTIVE',
    joined_at timestamptz NOT NULL DEFAULT now(),
    removed_at timestamptz,
    PRIMARY KEY (room_id, user_id)
);

CREATE TABLE room_policies (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id uuid NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    min_device_trust text NOT NULL,
    retention_days integer,
    auto_delete_seconds integer,
    attachments_allowed boolean NOT NULL DEFAULT true,
    screenshot_restriction text NOT NULL DEFAULT 'BEST_EFFORT',
    forwarding_allowed boolean NOT NULL DEFAULT false,
    verified_devices_only boolean NOT NULL DEFAULT true,
    policy_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    version integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (room_id, version)
);

CREATE TABLE encrypted_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    room_id uuid REFERENCES rooms(id),
    sender_user_id uuid NOT NULL REFERENCES users(id),
    sender_device_id uuid NOT NULL REFERENCES devices(id),
    recipient_user_id uuid REFERENCES users(id),
    recipient_device_id uuid REFERENCES devices(id),
    message_kind text NOT NULL,
    ciphertext bytea NOT NULL,
    ciphertext_sha256 bytea NOT NULL,
    crypto_metadata jsonb NOT NULL,
    server_received_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz,
    deleted_at timestamptz,
    CHECK (message_kind IN ('DIRECT', 'MLS_GROUP'))
);

CREATE TABLE encrypted_attachments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    room_id uuid REFERENCES rooms(id),
    uploader_user_id uuid NOT NULL REFERENCES users(id),
    uploader_device_id uuid NOT NULL REFERENCES devices(id),
    object_key text NOT NULL UNIQUE,
    ciphertext_sha256 bytea NOT NULL,
    ciphertext_bytes bigint NOT NULL,
    crypto_metadata jsonb NOT NULL,
    retention_expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE TABLE message_delivery_receipts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id uuid NOT NULL REFERENCES encrypted_messages(id) ON DELETE CASCADE,
    device_id uuid NOT NULL REFERENCES devices(id),
    receipt_type text NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (message_id, device_id, receipt_type)
);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    actor_user_id uuid REFERENCES users(id),
    actor_device_id uuid REFERENCES devices(id),
    event_type text NOT NULL,
    target_type text,
    target_id uuid,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    previous_event_hash bytea,
    event_hash bytea NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE admin_actions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    admin_user_id uuid NOT NULL REFERENCES users(id),
    action_type text NOT NULL,
    signed_action_envelope jsonb NOT NULL,
    approval_state text NOT NULL DEFAULT 'PENDING',
    created_at timestamptz NOT NULL DEFAULT now(),
    executed_at timestamptz
);

CREATE TABLE emergency_lockdowns (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    room_id uuid REFERENCES rooms(id),
    scope text NOT NULL,
    reason text NOT NULL,
    started_by uuid NOT NULL REFERENCES users(id),
    started_at timestamptz NOT NULL DEFAULT now(),
    ended_by uuid REFERENCES users(id),
    ended_at timestamptz
);

CREATE TABLE mdm_devices (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    device_id uuid REFERENCES devices(id),
    mdm_provider text NOT NULL,
    external_device_id text NOT NULL,
    compliance_state text NOT NULL,
    posture jsonb NOT NULL DEFAULT '{}'::jsonb,
    last_seen_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, mdm_provider, external_device_id)
);

CREATE TABLE siem_exports (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id),
    sink_type text NOT NULL,
    sink_config_ref text NOT NULL,
    last_exported_event_id uuid,
    last_exported_at timestamptz,
    status text NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_org ON users(organization_id);
CREATE INDEX idx_devices_user ON devices(user_id);
CREATE INDEX idx_devices_org_trust ON devices(organization_id, trust_state);
CREATE INDEX idx_signed_prekeys_device ON signed_prekeys(device_id);
CREATE INDEX idx_one_time_prekeys_available ON one_time_prekeys(device_id) WHERE claimed_at IS NULL;
CREATE INDEX idx_pq_prekeys_available ON pq_prekeys(device_id) WHERE claimed_at IS NULL;
CREATE INDEX idx_kt_subject ON key_transparency_entries(subject_user_id, log_index);
CREATE INDEX idx_rooms_org ON rooms(organization_id);
CREATE INDEX idx_room_members_user ON room_members(user_id);
CREATE INDEX idx_messages_recipient_device ON encrypted_messages(recipient_device_id, server_received_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_messages_room ON encrypted_messages(room_id, server_received_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_messages_expiry ON encrypted_messages(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_attachments_room ON encrypted_attachments(room_id, created_at) WHERE deleted_at IS NULL;
