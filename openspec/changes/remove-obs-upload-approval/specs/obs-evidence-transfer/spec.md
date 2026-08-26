## MODIFIED Requirements

### Requirement: Server-side encryption
The system SHALL upload evidence with SSE-KMS. The KMS key ID SHALL come only from server configuration; when it is empty, the
system SHALL omit the key-ID field so OBS uses or creates the account's default KMS master key.

#### Scenario: SSE-KMS applied
- **WHEN** an upload proceeds
- **THEN** the object SHALL be written with SSE-KMS
- **AND** a configured KMS key ID SHALL be used when present

#### Scenario: Default SSE-KMS key applied
- **WHEN** an upload proceeds without a configured KMS key ID
- **THEN** the request SHALL still select SSE-KMS
- **AND** it SHALL omit the KMS key-ID field so OBS uses or creates the default master key

### Requirement: Structured audit for every action
The system SHALL record a structured audit event for every put, head and get, covering success and failure, with event type,
result, error code, identity and timestamp, and without evidence body or credentials.

#### Scenario: Transfer audit
- **WHEN** a transfer action occurs
- **THEN** a structured audit event SHALL be recorded with eventType, result, errorCode and identity
- **AND** the audit SHALL NOT contain the evidence body or credentials

### Requirement: Fail-closed when disabled
The evidence transfer SHALL be disabled by default. When disabled or when no real OBS adapter exists, put/head/get SHALL fail
closed with OBS_UNAVAILABLE and MUST NOT contact OBS. When enabled with valid server configuration, investigation evidence MAY
be uploaded automatically without a per-package human approval.

#### Scenario: Disabled by default
- **WHEN** the service starts without OBS enabled
- **THEN** the evidence transfer adapter SHALL be a fail-closed disabled adapter
- **AND** no OBS connection SHALL be attempted

#### Scenario: No automatic production execution
- **WHEN** the investigation Agent runs while the automatic-storage gate is disabled
- **THEN** it SHALL NOT trigger an evidence upload

#### Scenario: Automatic evidence transfer when enabled
- **GIVEN** OBS and automatic evidence storage are enabled with valid server-side configuration
- **WHEN** the investigation Agent captures accepted bounded evidence
- **THEN** the system SHALL upload the evidence without waiting for a human approval
- **AND** all package/content, size, checksum, encryption and destination controls SHALL still apply

## REMOVED Requirements

### Requirement: Explicit approval from trusted control plane
**Reason**: The approved operating model now permits automatic storage of diagnostic evidence in a fixed server-controlled OBS
bucket and prefix; per-package approval prevents the required unattended diagnosis flow.

**Migration**: Remove approval lookup/consume from the put path. Operators shall control the capability through the default-off
OBS and automatic-storage deployment gates, fixed destination configuration, IAM permissions and normal deployment approval.
