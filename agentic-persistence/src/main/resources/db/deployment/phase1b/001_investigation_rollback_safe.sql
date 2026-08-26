UPDATE dpom_schema_state
SET compatibility_state = 'ROLLBACK_REQUESTED', installed_at = CURRENT_TIMESTAMP
WHERE component = 'investigation' AND schema_version = 1;

SELECT COUNT(*) AS durable_investigation_count FROM investigation;
SELECT COUNT(*) AS pending_publication_count
FROM diagnosis_publication_intent
WHERE publication_state NOT IN ('ACKNOWLEDGED', 'TERMINAL_FAILED');
