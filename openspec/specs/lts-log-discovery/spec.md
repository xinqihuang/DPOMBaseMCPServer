# lts-log-discovery Specification

## Purpose
TBD - created by archiving change add-lts-log-discovery. Update Purpose after archive.

## Requirements

### Requirement: Read-only log group discovery

The system SHALL expose `list_lts_log_groups` using SDK `ListLogGroups` and return every SDK log-group field.

#### Scenario: Discover groups

- **WHEN** an agent invokes the tool with configured read credentials
- **THEN** all accessible log groups and their real identifiers are returned without mutation

### Requirement: Read-only log stream discovery

The system SHALL expose `list_lts_log_streams` using SDK `ListLogStreams`, with optional group-name and
stream-name filters, and return every SDK log-stream field.

#### Scenario: Discover streams before querying logs

- **WHEN** an agent invokes the tool
- **THEN** real `log_group_id` and `log_stream_id` values are returned for use by `query_lts_logs`
