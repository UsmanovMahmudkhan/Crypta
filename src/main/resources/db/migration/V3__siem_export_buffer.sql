WITH ranked_exports AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY organization_id, sink_type
               ORDER BY updated_at DESC, created_at DESC, id DESC
           ) AS duplicate_rank
    FROM siem_exports
)
DELETE FROM siem_exports
WHERE id IN (
    SELECT id
    FROM ranked_exports
    WHERE duplicate_rank > 1
);

ALTER TABLE siem_exports
    ADD CONSTRAINT uq_siem_exports_org_sink UNIQUE (organization_id, sink_type);

CREATE TABLE siem_export_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    siem_export_id uuid NOT NULL REFERENCES siem_exports(id) ON DELETE CASCADE,
    audit_event_id uuid REFERENCES audit_events(id) ON DELETE SET NULL,
    normalized_event jsonb NOT NULL,
    status text NOT NULL DEFAULT 'PENDING',
    exported_at timestamptz NOT NULL DEFAULT now(),
    error_message text,
    UNIQUE (siem_export_id, audit_event_id)
);

CREATE INDEX idx_siem_export_events_export
    ON siem_export_events(siem_export_id, exported_at);

CREATE INDEX idx_siem_export_events_status
    ON siem_export_events(status, exported_at);

CREATE TABLE encrypted_attachment_download_grants (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    attachment_id uuid NOT NULL REFERENCES encrypted_attachments(id) ON DELETE CASCADE,
    requester_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
    requester_device_id uuid REFERENCES devices(id) ON DELETE SET NULL,
    token_hash bytea NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachment_download_grants_attachment
    ON encrypted_attachment_download_grants(attachment_id, created_at DESC);

CREATE INDEX idx_attachment_download_grants_active
    ON encrypted_attachment_download_grants(token_hash, expires_at)
    WHERE consumed_at IS NULL;
