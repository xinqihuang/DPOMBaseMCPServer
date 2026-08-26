SELECT required.table_name
FROM (SELECT 'diagnostic_report' table_name UNION ALL SELECT 'diagnostic_report_audit') required
LEFT JOIN information_schema.tables actual ON actual.table_schema=DATABASE() AND actual.table_name=required.table_name
WHERE actual.table_name IS NULL;

SELECT report_id FROM diagnostic_report
WHERE CHAR_LENGTH(source_digest)<>64 OR CHAR_LENGTH(report_digest)<>64 OR revision_number<1
   OR (revision_number>1 AND (supersedes_report_id IS NULL OR change_reason IS NULL));
