SET @report_rows=(SELECT COUNT(*) FROM diagnostic_report);
SET @report_guard=IF(@report_rows=0,'SELECT 1','SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''DIAGNOSTIC_REPORT_HISTORY_PRESENT''');
PREPARE report_guard_statement FROM @report_guard;
EXECUTE report_guard_statement;
DEALLOCATE PREPARE report_guard_statement;
DROP TABLE diagnostic_report_audit;
DROP TABLE diagnostic_report;
