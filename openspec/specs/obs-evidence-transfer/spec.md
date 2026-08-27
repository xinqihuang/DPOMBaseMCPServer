# obs-evidence-transfer Specification

## Purpose
为 Diagnostic Evidence Package 提供受控的 OBS put/head/get：服务端配置与服务端对象名，可信控制面显式 approval 后才可上传，
并施加大小、checksum、证据包结构校验、服务端加密、结构化审计与读取限流；不是通用 OBS 管理工具，禁止任意 key、list/delete、
源码/凭据与自动生产执行。

## Requirements

### Requirement: Server-side transfer configuration
The system SHALL resolve bucket, prefix, region, endpoint and credentials solely from server-side configuration and MUST NOT
accept them from the caller.

#### Scenario: Caller cannot override destination
- **WHEN** an evidence transfer is requested
- **THEN** bucket, prefix, region, endpoint and credentials SHALL come from server configuration only
- **AND** any caller-supplied bucket/prefix/key/credential SHALL be ignored

### Requirement: Server-generated object names with content checksum
The system SHALL generate the object key server-side from a fixed prefix, the evidence identity (serviceCode, collectionId,
packageId) and the content SHA-256 checksum, and MUST NOT use a caller-provided arbitrary object key.

#### Scenario: Deterministic object name
- **WHEN** a package is uploaded
- **THEN** the object key SHALL be generated server-side from prefix + identity + checksum
- **AND** repeated uploads of the same package SHALL yield the same object key

#### Scenario: head/get reconstruct the key
- **WHEN** head or get is requested
- **THEN** the caller SHALL provide the identity and the checksum, and the object key SHALL be reconstructed server-side
- **AND** the checksum SHALL be validated as 64 hex characters before entering the key

### Requirement: put/head/get surface only
The evidence transfer SHALL expose only put, head and get operations for Diagnostic Evidence Packages. It MUST NOT expose list,
delete, copy or any general OBS management operation.

#### Scenario: No management surface
- **WHEN** the tool surface is enumerated
- **THEN** only put, head and get SHALL be present
- **AND** no list/delete/copy operation SHALL be exposed

### Requirement: Independent transfer tool gate
The OBS evidence transfer tools SHALL be registered under the dedicated `dpom.obs.transfer-tools-enabled` gate. This Artifact
gate MUST NOT enable any production-resource mutation capability.

#### Scenario: Independent gate
- **WHEN** `dpom.obs.transfer-tools-enabled` is enabled
- **THEN** only the OBS put/head/get tools SHALL be added by this gate

### Requirement: Automatic evidence upload
The system SHALL NOT require a per-upload human approval. Deployment configuration, bounded payload validation, deterministic
keys, integrity checks, encryption and least-privilege IAM SHALL form the upload safety boundary.

#### Scenario: Valid upload under an enabled deployment gate
- **WHEN** a valid bounded evidence package is uploaded while OBS transfer is enabled
- **THEN** the service SHALL upload it without an approval token
- **AND** the operation SHALL be audited without recording evidence content or credentials

### Requirement: Package structure and content validation
The system SHALL parse and validate the content as a Diagnostic Evidence Package with a camelCase manifest (matching the DPOMAgent
PackageSerializer contract), and MUST reject an arbitrary ZIP, source code, credentials, duplicate entries, path traversal,
undeclared entries and checksum/size mismatches.

#### Scenario: Missing manifest
- **WHEN** the content is not a valid ZIP or is missing manifest.json
- **THEN** the upload SHALL fail with INVALID_PARAM and no OBS write SHALL occur

#### Scenario: Source code rejected
- **WHEN** a package entry is a source code file
- **THEN** the upload SHALL fail with INVALID_PARAM and no OBS write SHALL occur

#### Scenario: Credentials rejected
- **WHEN** a package entry contains credential markers
- **THEN** the upload SHALL fail with INVALID_PARAM and no OBS write SHALL occur

#### Scenario: Duplicate entry rejected
- **WHEN** the package contains a duplicate manifest or duplicate non-manifest entry
- **THEN** the upload SHALL fail with INVALID_PARAM and no OBS write SHALL occur

#### Scenario: Path traversal rejected
- **WHEN** an entry path contains slash/backslash traversal, a Windows drive prefix or a UNC prefix
- **THEN** the upload SHALL fail with INVALID_PARAM and no OBS write SHALL occur

#### Scenario: Undeclared or inconsistent entry rejected
- **WHEN** the entry set is not exactly manifest.json + checksums.json + the manifest-declared entries, or a declared entry's
  checksum or size does not match its content
- **THEN** the upload SHALL fail with INVALID_PARAM and no OBS write SHALL occur

#### Scenario: Redaction report schema enforced
- **WHEN** security/redaction-report.json is not an object of section names mapped to non-negative integer counts
- **THEN** the upload SHALL fail with INVALID_PARAM and no OBS write SHALL occur

### Requirement: Size bound
The system SHALL reject evidence content that exceeds the server-configured maximum size.

#### Scenario: Oversized package
- **WHEN** package content exceeds the configured maximum bytes
- **THEN** the upload SHALL fail with INVALID_PARAM and no OBS write SHALL occur

### Requirement: Base64 length limit before decode
The system SHALL bound the base64 content length before decoding, so oversized input is rejected without decoding.

#### Scenario: Oversized base64
- **WHEN** the base64 content length exceeds the derived limit
- **THEN** the upload SHALL fail with INVALID_PARAM without decoding

### Requirement: Checksum verification
The system SHALL verify the caller-provided SHA-256 checksum against the package content and record it on the object.

#### Scenario: Checksum mismatch
- **WHEN** the computed SHA-256 of the content differs from the provided checksum
- **THEN** the upload SHALL fail with INVALID_PARAM and no OBS write SHALL occur

### Requirement: Server-side encryption
The system SHALL upload evidence with server-side encryption using the server-configured KMS key.

#### Scenario: SSE-KMS applied
- **WHEN** an upload proceeds
- **THEN** the object SHALL be written with server-side encryption (SSE-KMS) using the configured KMS key

### Requirement: Structured audit for every action
The system SHALL record a structured audit event for every put, head and get, covering success and failure, with event
type, result, error code, identity and timestamp, and without evidence body or credentials.

#### Scenario: Transfer audit
- **WHEN** a transfer action occurs
- **THEN** a structured audit event SHALL be recorded with eventType, result, errorCode and identity
- **AND** the audit SHALL NOT contain the evidence body or credentials

### Requirement: Bounded get with rate limit
The get operation SHALL be rate-limited and SHALL enforce a maximum read size, rejecting objects larger than the configured bound
before reading their content.

#### Scenario: Get rate limited
- **WHEN** the get operation exceeds the configured rate
- **THEN** it SHALL fail with UPSTREAM_THROTTLED

#### Scenario: Get read bound
- **WHEN** the object metadata reports a size above the configured maximum
- **THEN** get SHALL fail with INVALID_PARAM without reading the content

### Requirement: Fail-closed when disabled
The evidence transfer SHALL be disabled by default. When disabled or when no real OBS adapter exists, put/head/get SHALL fail
closed with OBS_UNAVAILABLE and MUST NOT contact OBS.

#### Scenario: Disabled by default
- **WHEN** the service starts without OBS enabled
- **THEN** the evidence transfer adapter SHALL be a fail-closed disabled adapter
- **AND** no OBS connection SHALL be attempted
