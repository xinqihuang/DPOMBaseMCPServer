ALTER TABLE diagnosis_publication_intent
    ADD COLUMN topic_name VARCHAR(128),
    ADD COLUMN canonical_content BLOB,
    ADD COLUMN canonical_sha256 CHAR(64),
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP(6),
    ADD COLUMN lease_owner VARCHAR(128),
    ADD COLUMN fencing_token VARCHAR(128),
    ADD COLUMN lease_expires_at TIMESTAMP(6),
    ADD COLUMN acknowledged_at TIMESTAMP(6),
    ADD COLUMN last_failure_code VARCHAR(64);

CREATE INDEX ix_publication_delivery
    ON diagnosis_publication_intent (publication_state, next_attempt_at, lease_expires_at, created_at);

ALTER TABLE investigation_audit
    ADD COLUMN operator_ref VARCHAR(128),
    ADD COLUMN reason_code VARCHAR(64);
