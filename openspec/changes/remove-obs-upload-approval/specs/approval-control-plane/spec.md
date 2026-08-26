## REMOVED Requirements

### Requirement: Internal REST control plane surface
**Reason**: OBS uploads no longer require per-package approve/revoke operations.

**Migration**: Disable and remove the OBS approval REST endpoints; use deployment configuration and IAM policy to control access.

### Requirement: HMAC request authentication
**Reason**: The HMAC surface authenticated only the removed OBS approve/revoke endpoints.

**Migration**: Remove its OBS approval wiring and secrets after the compatibility window; do not reuse it for MCP authentication.

### Requirement: Persistent approval record
**Reason**: No approval record is required before an evidence upload.

**Migration**: Stop reading and writing the approval store. Existing records are inert historical data and MUST NOT gate uploads.

### Requirement: Expiry, revocation and mismatch rejection
**Reason**: Expiry and revocation applied only to the removed per-package approval state.

**Migration**: Preserve checksum validation in the evidence transfer path; remove approval expiry/revocation checks.

### Requirement: Atomic consume and concurrency
**Reason**: Upload concurrency is now controlled by deterministic content-addressed object identity rather than single-use approval.

**Migration**: Replace approval consumption with idempotent deterministic object writes and digest verification.

### Requirement: Structured audit and metrics
**Reason**: Approval-specific audit and metrics have no producer after the control plane is removed.

**Migration**: Retain put/head/get and automatic-storage audit/metrics; remove approve/revoke/consume metrics.

### Requirement: Input limits and stable errors
**Reason**: These limits and errors protect only the removed approval REST request surface.

**Migration**: Retain evidence-size, identity, checksum and OBS error bounds in the storage and transfer capabilities.

### Requirement: Boundaries
**Reason**: The removed control plane has no remaining externally observable behavior.

**Migration**: The OBS adapter and evidence storage path continue to forbid list/delete/copy and arbitrary commands.
