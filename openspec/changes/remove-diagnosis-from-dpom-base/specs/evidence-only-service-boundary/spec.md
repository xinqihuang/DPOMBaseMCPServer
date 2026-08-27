## Purpose

确保 DPOMBaseMCPServer 始终只是面向 DPOMAgent 的可审计证据采集与标准化工具服务，不拥有、推断或发布任何诊断事实。

## ADDED Requirements

### Requirement: Evidence-only responsibility
DPOMBaseMCPServer SHALL only collect, normalize and return bounded evidence and evidence references from approved Huawei
Cloud, CMDB and OBS sources. Tool output SHALL contain observed facts, provenance, time bounds, integrity metadata and stable
errors; it MUST NOT contain generated hypotheses, root causes or diagnostic conclusions.

#### Scenario: DPOMAgent requests evidence
- **GIVEN** DPOMAgent sends a valid bounded evidence-tool request
- **WHEN** DPOMBaseMCPServer completes the upstream query
- **THEN** it SHALL return normalized evidence with source and request provenance
- **AND** DPOMAgent SHALL remain responsible for interpreting that evidence

### Requirement: No diagnosis runtime
DPOMBaseMCPServer MUST NOT own Incident, Investigation, Run, Step, Hypothesis or Conclusion state and MUST NOT implement
diagnosis lifecycle, budgets, checkpoints, RCA, diagnostic report generation or ToolUse decisions.

#### Scenario: Service architecture is inspected
- **GIVEN** a production DPOMBaseMCPServer build
- **WHEN** modules, dependencies, configuration and persistence mappings are inspected
- **THEN** no diagnosis domain, runtime, report builder or Investigation persistence SHALL exist
- **AND** evidence-tool behavior SHALL remain available

### Requirement: No LLM or diagnosis messaging
DPOMBaseMCPServer MUST NOT host model credentials or clients and MUST NOT build or publish Diagnosis Event or Diagnosis
Progress messages through HTTP, Kafka or another broker.

#### Scenario: Default build and startup are inspected
- **GIVEN** a clean repository clone with no sibling contracts directory or broker
- **WHEN** Maven verification and default startup configuration are evaluated
- **THEN** no LLM, Kafka producer, diagnosis outbox, replay worker or external Diagnosis Event contract source SHALL be required
- **AND** the service SHALL build using repository-contained evidence contract inputs only

### Requirement: Production mutation remains isolated
DPOMBaseMCPServer MUST NOT expose generic production-write tools. Production mutation SHALL remain behind the separately
governed HuaweiCloudAlarmChangeGuard boundary.

#### Scenario: Tool catalog is enumerated
- **GIVEN** the MCP tool server is running
- **WHEN** its available tools are enumerated
- **THEN** every DPOMBase tool SHALL be evidence collection, discovery or controlled Artifact handling
- **AND** unrestricted alarm, resource or workload mutation MUST NOT be present
