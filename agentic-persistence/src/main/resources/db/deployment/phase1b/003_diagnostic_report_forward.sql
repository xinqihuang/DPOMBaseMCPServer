-- Reviewed deployment-managed Phase 5 diagnosis-only report extension.
CREATE TABLE diagnostic_report (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    incident_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    revision_number BIGINT NOT NULL,
    supersedes_report_id VARCHAR(128) NULL,
    change_reason VARCHAR(64) NULL,
    source_digest CHAR(64) NOT NULL,
    canonical_json MEDIUMTEXT NOT NULL,
    report_digest CHAR(64) NOT NULL,
    completeness VARCHAR(16) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_diagnostic_report_id UNIQUE(report_id),
    CONSTRAINT uk_diagnostic_report_request UNIQUE(request_id),
    CONSTRAINT uk_diagnostic_report_request_digest UNIQUE(request_digest),
    CONSTRAINT uk_diagnostic_report_revision UNIQUE(investigation_id,revision_number),
    CONSTRAINT uk_diagnostic_report_digest UNIQUE(report_digest),
    CONSTRAINT fk_diagnostic_report_supersedes FOREIGN KEY(supersedes_report_id) REFERENCES diagnostic_report(report_id),
    CONSTRAINT ck_diagnostic_report_revision CHECK(revision_number > 0),
    CONSTRAINT ck_diagnostic_report_complete CHECK(completeness IN ('COMPLETE','INCOMPLETE'))
) ENGINE=InnoDB;

CREATE INDEX idx_diagnostic_report_history ON diagnostic_report(investigation_id,revision_number DESC);

CREATE TABLE diagnostic_report_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    audit_id VARCHAR(128) NOT NULL,
    report_id VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_diagnostic_report_audit UNIQUE(audit_id),
    CONSTRAINT fk_diagnostic_report_audit FOREIGN KEY(report_id) REFERENCES diagnostic_report(report_id)
) ENGINE=InnoDB;
