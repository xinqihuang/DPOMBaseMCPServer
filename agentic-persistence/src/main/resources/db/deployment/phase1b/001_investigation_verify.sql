SELECT schema_version, compatibility_state
FROM dpom_schema_state
WHERE component = 'investigation'
  AND schema_version = 1
  AND compatibility_state = 'READY';

SELECT COUNT(*) AS required_table_count
FROM information_schema.tables
WHERE LOWER(table_name) IN (
    'investigation', 'investigation_budget', 'investigation_run', 'investigation_step',
    'investigation_observation', 'investigation_hypothesis', 'investigation_conclusion',
    'investigation_checkpoint', 'investigation_progress', 'diagnosis_publication_intent',
    'investigation_audit', 'investigation_command_receipt', 'investigation_external_call'
)
  AND table_schema = DATABASE();

SELECT COUNT(*) AS publication_delivery_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'diagnosis_publication_intent'
  AND column_name IN (
    'topic_name', 'canonical_content', 'canonical_sha256', 'attempt_count', 'next_attempt_at',
    'lease_owner', 'fencing_token', 'lease_expires_at', 'acknowledged_at', 'last_failure_code'
  );
