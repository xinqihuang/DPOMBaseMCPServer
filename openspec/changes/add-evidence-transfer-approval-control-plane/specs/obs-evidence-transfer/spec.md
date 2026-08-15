## MODIFIED Requirements

### Requirement: Explicit approval from trusted control plane
The system SHALL require a prior, separately recorded approval bound to the exact serviceCode, investigationId, packageId
and content SHA-256 before an upload proceeds. Approval SHALL be granted only by an authenticated non-MCP trusted control
plane, SHALL be persisted, and SHALL be consumed at most once by a successful upload.

#### Scenario: Upload without approval
- **WHEN** an upload is requested without a recorded, unexpired approval matching the identity and sha256
- **THEN** the upload SHALL fail with UPLOAD_NOT_APPROVED and no OBS write SHALL occur

#### Scenario: Approval is not an MCP tool
- **WHEN** the MCP tool surface is enumerated
- **THEN** no approve tool SHALL be present
- **AND** approval SHALL come from a non-MCP authenticated trusted control plane

#### Scenario: Approval expires
- **WHEN** a recorded approval is past its expiry
- **THEN** the upload SHALL be rejected

#### Scenario: Approval consumed once
- **WHEN** an upload succeeds with a recorded approval
- **THEN** the approval SHALL be consumed and a second upload with the same identity and sha256 SHALL be rejected
