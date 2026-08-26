CREATE TABLE IF NOT EXISTS dpom_schema_state (
    component VARCHAR(64) NOT NULL,
    schema_version INT NOT NULL,
    compatibility_state VARCHAR(32) NOT NULL,
    installed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (component)
);

CREATE TABLE IF NOT EXISTS investigation (
    investigation_id VARCHAR(128) NOT NULL,
    incident_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    authority_service VARCHAR(64) NOT NULL,
    authority_epoch VARCHAR(128) NOT NULL,
    authority_active_from TIMESTAMP(6) NOT NULL,
    active_run_id VARCHAR(128),
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (investigation_id),
    CONSTRAINT uq_investigation_incident UNIQUE (incident_id)
);

CREATE TABLE IF NOT EXISTS investigation_budget (
    investigation_id VARCHAR(128) NOT NULL,
    max_steps INT NOT NULL,
    max_tool_calls INT NOT NULL,
    max_tokens BIGINT NOT NULL,
    max_duration_seconds BIGINT NOT NULL,
    used_steps INT NOT NULL,
    used_tool_calls INT NOT NULL,
    used_tokens BIGINT NOT NULL,
    used_duration_seconds BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    PRIMARY KEY (investigation_id),
    CONSTRAINT fk_budget_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

CREATE TABLE IF NOT EXISTS investigation_run (
    run_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    attempt INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    run_version BIGINT NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6),
    PRIMARY KEY (run_id),
    CONSTRAINT uq_run_attempt UNIQUE (investigation_id, attempt),
    CONSTRAINT fk_run_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

CREATE TABLE IF NOT EXISTS investigation_step (
    step_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    step_sequence INT NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    outcome_code VARCHAR(64),
    recorded_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (step_id),
    CONSTRAINT uq_step_sequence UNIQUE (run_id, step_sequence),
    CONSTRAINT fk_step_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id),
    CONSTRAINT fk_step_run FOREIGN KEY (run_id) REFERENCES investigation_run (run_id)
);

CREATE TABLE IF NOT EXISTS investigation_observation (
    observation_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    evidence_ref VARCHAR(256) NOT NULL,
    summary_code VARCHAR(64) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (observation_id),
    CONSTRAINT uq_observation_evidence UNIQUE (investigation_id, evidence_ref),
    CONSTRAINT fk_observation_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

CREATE TABLE IF NOT EXISTS investigation_hypothesis (
    hypothesis_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    statement_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    evidence_refs TEXT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (hypothesis_id),
    CONSTRAINT fk_hypothesis_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

CREATE TABLE IF NOT EXISTS investigation_conclusion (
    conclusion_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    conclusion_type VARCHAR(32) NOT NULL,
    summary_code VARCHAR(64) NOT NULL,
    evidence_refs TEXT NOT NULL,
    concluded_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (conclusion_id),
    CONSTRAINT uq_conclusion_investigation UNIQUE (investigation_id),
    CONSTRAINT fk_conclusion_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

CREATE TABLE IF NOT EXISTS investigation_checkpoint (
    checkpoint_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    next_step_sequence INT NOT NULL,
    external_call_state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (checkpoint_id),
    CONSTRAINT uq_checkpoint_version UNIQUE (investigation_id, aggregate_version),
    CONSTRAINT fk_checkpoint_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

CREATE TABLE IF NOT EXISTS investigation_progress (
    progress_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    progress_sequence BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    stage_code VARCHAR(64) NOT NULL,
    summary_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (progress_id),
    CONSTRAINT uq_progress_sequence UNIQUE (investigation_id, progress_sequence),
    CONSTRAINT fk_progress_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

CREATE TABLE IF NOT EXISTS diagnosis_publication_intent (
    intent_id VARCHAR(128) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    aggregate_sequence BIGINT NOT NULL,
    authority_service VARCHAR(64) NOT NULL,
    authority_epoch VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    topic_name VARCHAR(128),
    canonical_content BLOB,
    canonical_sha256 CHAR(64),
    publication_state VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6),
    lease_owner VARCHAR(128),
    fencing_token VARCHAR(128),
    lease_expires_at TIMESTAMP(6),
    acknowledged_at TIMESTAMP(6),
    last_failure_code VARCHAR(64),
    PRIMARY KEY (intent_id),
    CONSTRAINT uq_publication_event UNIQUE (event_id),
    CONSTRAINT uq_publication_aggregate UNIQUE (investigation_id, aggregate_sequence),
    CONSTRAINT fk_publication_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

CREATE TABLE IF NOT EXISTS investigation_audit (
    audit_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    outcome_code VARCHAR(64) NOT NULL,
    operator_ref VARCHAR(128),
    reason_code VARCHAR(64),
    aggregate_version BIGINT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (audit_id),
    CONSTRAINT fk_audit_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

CREATE TABLE IF NOT EXISTS investigation_command_receipt (
    command_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    canonical_sha256 CHAR(64) NOT NULL,
    outcome_code VARCHAR(64) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (command_id),
    CONSTRAINT fk_command_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

CREATE TABLE IF NOT EXISTS investigation_external_call (
    call_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    call_state VARCHAR(32) NOT NULL,
    attempt INT NOT NULL,
    result_code VARCHAR(64),
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (call_id),
    CONSTRAINT uq_external_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_external_investigation FOREIGN KEY (investigation_id)
        REFERENCES investigation (investigation_id)
);

INSERT INTO dpom_schema_state (component, schema_version, compatibility_state, installed_at)
VALUES ('investigation', 1, 'READY', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE schema_version = 1, compatibility_state = 'READY', installed_at = CURRENT_TIMESTAMP;
