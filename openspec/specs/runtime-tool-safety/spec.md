# runtime-tool-safety Specification

## Purpose
TBD - created by archiving change harden-readonly-runtime-boundary. Update Purpose after archive.

## Requirements

### Requirement: Fail-closed write-tool registration

The system SHALL NOT register `create_notification_mask` or `delete_notification_masks` by default. The system SHALL register them only when the `action-enabled` Spring profile and `dpom.mcp.write-tools-enabled=true` are both present.

#### Scenario: Default production runtime
- **WHEN** the service starts without action opt-in
- **THEN** `tools/list` MUST NOT contain either write tool

#### Scenario: Only one opt-in is present
- **WHEN** only the profile or only the property is enabled
- **THEN** `tools/list` MUST NOT contain either write tool

#### Scenario: Isolated manual action runtime
- **WHEN** both opt-ins are enabled deliberately
- **THEN** the two write tools MAY be registered

### Requirement: Investigation agent remains read-only

DPOMAgent MUST NOT enable or invoke DPOMBaseMCPServer write tools. Production diagnosis and mitigation SHALL remain evidence-only and artifact-only.

#### Scenario: Automated investigation
- **WHEN** DPOMAgent performs an investigation
- **THEN** all DPOMBaseMCPServer calls SHALL be read-only
