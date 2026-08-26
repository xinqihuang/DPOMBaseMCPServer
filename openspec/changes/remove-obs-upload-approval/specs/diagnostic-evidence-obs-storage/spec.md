## Purpose

将诊断过程中采集到的有界证据自动固化到服务端指定的华为云 OBS 位置，并返回可校验、可重建且不暴露证据正文的稳定引用。

## ADDED Requirements

### Requirement: Automatic bounded evidence storage
When OBS evidence storage is enabled, the system SHALL store each accepted bounded diagnostic evidence item before returning
an evidence record to the investigation runtime, and MUST fail closed rather than publish a dangling reference.

#### Scenario: Evidence stored before reference
- **GIVEN** OBS evidence storage is enabled and a diagnostic branch returns an accepted bounded result
- **WHEN** the result is converted to an evidence record
- **THEN** the canonical evidence bytes SHALL be written to OBS first
- **AND** the evidence record SHALL reference the successfully stored object

#### Scenario: Upload failure does not create evidence
- **GIVEN** OBS evidence storage is enabled
- **WHEN** OBS rejects or times out while storing evidence
- **THEN** no successful evidence record SHALL be returned for that item
- **AND** the failure SHALL use a stable error code without exposing credentials or evidence content

### Requirement: Fixed server-side destination
The system SHALL use only the environment-specific server-configured endpoint, bucket and prefix. Callers MUST NOT choose an
endpoint, bucket, prefix or arbitrary object key, and production code and deployment defaults MUST NOT hardcode a test bucket.

#### Scenario: Evidence object uses configured prefix
- **GIVEN** an environment-specific bucket and prefix are configured
- **WHEN** an evidence item is stored
- **THEN** its object key SHALL start with the configured prefix
- **AND** no caller input SHALL override the endpoint, bucket or prefix

#### Scenario: Verification bucket is runtime-only
- **GIVEN** the real E2E runs with bucket `obs-perf168w-public` and prefix `evidence`
- **WHEN** production code and deployment defaults are inspected
- **THEN** neither test value SHALL be hardcoded as a production destination

### Requirement: Deterministic identity and integrity
The system SHALL derive the object key from bounded whitelisted identity fields and the canonical SHA-256 digest, and SHALL
return a source reference, digest, byte size and capture time that identify the stored bytes.

#### Scenario: Same evidence produces same object identity
- **GIVEN** the same investigation identity, evidence type and canonical content
- **WHEN** storage is retried
- **THEN** the same object key and digest SHALL be produced
- **AND** the stored object metadata SHALL carry the canonical SHA-256 digest

#### Scenario: Unsafe identity rejected
- **WHEN** an identity contains traversal, separators or values outside the configured bounds
- **THEN** storage SHALL fail with `INVALID_PARAM` before contacting OBS

### Requirement: Bounded and secret-safe serialization
The system SHALL serialize evidence into deterministic bounded UTF-8 JSON, SHALL reject content above the configured limit,
and MUST NOT log or publish the evidence body, AK, SK, token or credential-like fields.

#### Scenario: Oversized evidence rejected
- **WHEN** canonical evidence bytes exceed the configured maximum
- **THEN** storage SHALL fail with `INVALID_PARAM` before uploading

#### Scenario: Operational logs remain body-safe
- **WHEN** an upload succeeds or fails
- **THEN** logs SHALL contain only bounded operation metadata such as outcome, size and error code
- **AND** logs SHALL NOT contain evidence bytes or credentials

### Requirement: Default-off admission
Automatic OBS evidence storage SHALL be disabled by default and SHALL require complete server-side endpoint, bucket and prefix
configuration before activation.

#### Scenario: Disabled storage does not contact OBS
- **WHEN** the service starts without automatic OBS evidence storage enabled
- **THEN** no automatic upload SHALL occur
- **AND** no OBS connection SHALL be attempted by the evidence-storage path

#### Scenario: Incomplete enabled configuration fails closed
- **WHEN** automatic OBS evidence storage is enabled with an empty endpoint, bucket or prefix
- **THEN** startup readiness SHALL fail without printing credentials

### Requirement: Explicit real OBS verification
The project SHALL provide an explicitly gated real OBS verification that reads credentials only from process environment,
uploads a bounded verification artifact under `evidence/`, and validates it through HEAD and GET.

#### Scenario: Real verification succeeds
- **GIVEN** the E2E gate and required environment credentials/configuration are present, including a runtime bucket and prefix
- **WHEN** the OBS verification runs
- **THEN** it SHALL upload an object below the configured prefix
- **AND** HEAD and GET SHALL confirm the expected size, ETag, bytes and SHA-256 metadata
- **AND** the object SHALL remain available as verification evidence

#### Scenario: Credentials absent
- **WHEN** the E2E gate is not enabled or required environment configuration is missing
- **THEN** the real OBS verification SHALL report `NOT_EXECUTED` rather than using fallback credentials
