## Purpose

为生产侧 DPOMAgent 提供**非 MCP 的、强认证的、持久化的可信控制面**，批准/撤销精确 OBS 上传（绑定 serviceCode/investigationId/packageId/sha256），使 put 侧原子消费审批；LLM 触达的 MCP 面不含任何 approve 能力。

## ADDED Requirements

### Requirement: Internal REST control plane surface
The system SHALL expose an internal REST control plane (approve and revoke) that is NOT registered as an MCP tool, is
disabled by default, and is gated independently of the MCP write tools.

#### Scenario: Not an MCP tool
- **WHEN** the MCP tool surface is enumerated
- **THEN** no approve or revoke tool SHALL be present

#### Scenario: Disabled by default
- **WHEN** the service starts without the approval control plane enabled
- **THEN** the approve/revoke endpoints SHALL be absent (fail-closed)

#### Scenario: Independent gate
- **WHEN** the approval control plane is enabled
- **THEN** it SHALL be enabled only by its own gate and SHALL NOT depend on or enable the MCP write tools

### Requirement: HMAC request authentication
The system SHALL authenticate every control-plane request with an HMAC signature over timestamp, nonce, method, path and
body hash, using a server-side secret, and MUST reject a request that carries any client AK/SK.

#### Scenario: Signature verified
- **WHEN** a request is received
- **THEN** the signature SHALL be recomputed from timestamp + nonce + method + path + body hash using the server-side secret
- **AND** comparison SHALL be constant-time

#### Scenario: Replay rejected
- **WHEN** a timestamp is outside the tolerance window or a nonce has already been seen
- **THEN** the request SHALL fail with APPROVAL_AUTH_FAILED

#### Scenario: Replay rejected across restart
- **WHEN** a nonce was accepted and the service restarts within the tolerance window
- **THEN** a request reusing that nonce SHALL still be rejected

#### Scenario: Nonce window covers the signature validity endpoint
- **WHEN** a request with a future (but in-window) timestamp is accepted
- **THEN** its nonce SHALL remain recorded until timestamp + tolerance, not now + tolerance

#### Scenario: Key rotation
- **WHEN** the server-side primary and previous HMAC secrets are both configured
- **THEN** a signature from either secret SHALL be accepted during rotation
- **AND** a secret shorter than the minimum strength SHALL fail closed

#### Scenario: Client credentials forbidden
- **WHEN** a request carries an AK/SK-style credential (Authorization header or access-key/secret-key fields)
- **THEN** the request SHALL be rejected

#### Scenario: Secret not logged
- **WHEN** authentication or any control-plane action is logged
- **THEN** the log SHALL NOT contain the HMAC secret, the signature, or the request body

### Requirement: Persistent approval record
The system SHALL persist approvals bound to serviceCode, investigationId, packageId, sha256, approverRef, reason and expiry,
and SHALL retain them across a restart.

#### Scenario: Survives restart
- **WHEN** an approval is recorded and the service restarts
- **THEN** the approval SHALL still be present and consumable until its expiry

#### Scenario: Persistence failure fails closed
- **WHEN** persisting an approve/revoke/consume/restore fails
- **THEN** the operation SHALL fail with APPROVAL_STORAGE_ERROR and the in-memory state SHALL be rolled back

#### Scenario: Corrupted store fails closed
- **WHEN** the service starts and the approval store file is unreadable or corrupt
- **THEN** startup SHALL fail rather than silently start with an empty store

### Requirement: Expiry, revocation and mismatch rejection
The system SHALL reject a put or revoke whose approval is expired, revoked, or whose identity or checksum does not match.

#### Scenario: Expired approval
- **WHEN** a put is attempted with an approval past its expiry
- **THEN** the put SHALL fail and the approval SHALL be treated as absent

#### Scenario: Revoked approval
- **WHEN** an approval has been revoked
- **THEN** a subsequent put SHALL fail with UPLOAD_NOT_APPROVED

#### Scenario: Checksum mismatch
- **WHEN** the put content sha256 differs from the approved sha256
- **THEN** the put SHALL fail and no OBS write SHALL occur

### Requirement: Atomic consume and concurrency
The system SHALL consume an approval at most once for a successful put, and SHALL ensure only one concurrent put wins.

#### Scenario: Single winner
- **WHEN** two puts race for the same approval
- **THEN** exactly one SHALL proceed and the approval SHALL be consumed

#### Scenario: Failure rollback
- **WHEN** a put consumes the approval but the OBS upload fails
- **THEN** the approval SHALL be restored so the put can be retried

### Requirement: Structured audit and metrics
The system SHALL record a structured audit event for every approve, revoke and consume (success and failure) and emit
low-cardinality metrics, without the secret, signature or body.

#### Scenario: Audit
- **WHEN** an approve, revoke or consume occurs
- **THEN** a structured audit event SHALL be recorded with eventType, result, errorCode and identity

#### Scenario: Metrics
- **WHEN** a control-plane action completes
- **THEN** a low-cardinality counter SHALL be incremented without per-package tags

#### Scenario: Unvalidated identity sanitized
- **WHEN** an audit event is written for a request whose identity or checksum is not yet validated
- **THEN** the audit SHALL NOT contain the raw unvalidated value and SHALL use a sanitized placeholder

### Requirement: Input limits and stable errors
The system SHALL bound the request body, whitelist request fields, and return stable error codes.

#### Scenario: Body bound and field whitelist
- **WHEN** a request exceeds the body limit or contains fields outside the whitelist
- **THEN** the request SHALL fail with INVALID_PARAM

#### Scenario: Nonce format and length
- **WHEN** a request nonce does not match the whitelisted format or exceeds the length limit
- **THEN** the request SHALL fail with APPROVAL_AUTH_FAILED

#### Scenario: Bounded nonce cache
- **WHEN** the nonce cache reaches capacity and no nonce is expired
- **THEN** a new nonce SHALL be rejected (fail-closed) rather than growing unbounded

#### Scenario: Stable errors
- **WHEN** a control-plane action fails
- **THEN** it SHALL return a stable error code (APPROVAL_AUTH_FAILED / INVALID_PARAM / APPROVAL_NOT_FOUND / APPROVAL_STORAGE_ERROR)

#### Scenario: Uniform auth message
- **WHEN** authentication fails for any reason
- **THEN** the response SHALL be a uniform 401 with a stable message, without revealing which part failed

#### Scenario: Generic error not echoed
- **WHEN** an unexpected exception occurs
- **THEN** the response SHALL be a stable 500 message that SHALL NOT echo the internal exception

### Requirement: Boundaries
The control plane SHALL provide only approve/revoke, SHALL NOT expose general OBS management, SHALL NOT trigger automatic
production execution, SHALL NOT execute arbitrary commands, and SHALL remain within the single-instance Java Web boundary.

#### Scenario: No escalation
- **WHEN** the control plane is used
- **THEN** it SHALL NOT expose OBS list/delete/copy, SHALL NOT run commands, and SHALL NOT auto-execute production actions
