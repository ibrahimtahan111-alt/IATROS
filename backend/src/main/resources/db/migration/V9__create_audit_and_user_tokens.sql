-- Audit trail of sensitive actions (security.md §23). Metadata must never contain
-- medical content, passwords, or tokens.

CREATE TABLE audit_event (
    id             uuid        PRIMARY KEY,
    clinic_id      uuid        REFERENCES clinic (id),
    actor_user_id  uuid        REFERENCES app_user (id),
    action         varchar(60) NOT NULL,
    entity_type    varchar(60) NOT NULL,
    entity_id      uuid,
    occurred_at    timestamptz NOT NULL DEFAULT now(),
    metadata       jsonb       NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX audit_event_clinic_time_idx ON audit_event (clinic_id, occurred_at DESC);
CREATE INDEX audit_event_entity_idx ON audit_event (entity_type, entity_id, occurred_at DESC);
CREATE INDEX audit_event_actor_idx ON audit_event (actor_user_id, occurred_at DESC);

CREATE FUNCTION reject_audit_event_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_event rows are immutable' USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER audit_event_immutable_rows
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_audit_event_change();

CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_event_change();

-- Single-use invitation and password-reset tokens, stored only as SHA-256 hashes (decisions.md D-021).

CREATE TABLE user_token (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id),
    purpose     varchar(30) NOT NULL CHECK (purpose IN ('INVITATION', 'PASSWORD_RESET')),
    token_hash  bytea       NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT user_token_hash_uk UNIQUE (token_hash),
    CONSTRAINT user_token_expiry_after_creation CHECK (expires_at > created_at)
);

CREATE INDEX user_token_user_open_idx ON user_token (user_id, purpose) WHERE used_at IS NULL;
