## Context

See `proposal.md` for motivation. The service already contains `agentic-adapter-obs` backed by `esdk-obs-java 3.26.6`,
an OBS evidence service, package validation and a diagnosis-side `BoundedEvidenceArtifactStore` port. The missing link is a
production store implementation, while the current put path is coupled to a per-package approval service.

The target remains a production write with a narrow purpose: persist immutable diagnostic evidence. It is not a general OBS
tool and remains default-off. Credentials are supplied only through `HUAWEICLOUD_AK/HUAWEICLOUD_SK`.

## Goals / Non-Goals

**Goals:**

- Connect accepted bounded diagnostic evidence to the existing OBS SDK adapter.
- Remove approval lookup/consume from the upload path and retire its REST composition.
- Make bucket, endpoint and prefix environment-specific server configuration; use `obs-perf168w-public/evidence` only as
  runtime parameters for the authorized verification.
- Produce deterministic, content-addressed, integrity-verifiable evidence references.
- Prove the integration with an explicitly gated real OBS upload plus HEAD/GET verification.

**Non-Goals:**

- OBS list/delete/copy, bucket administration, caller-selected keys or arbitrary file upload.
- Writing credentials, raw evidence bodies or prompts to logs and reports.
- Enabling OBS automatically in every environment or weakening diagnosis evidence size bounds.
- Deleting the retained real verification object.

## Decisions

### D1: Reuse and extend the existing OBS SDK adapter

The implementation will retain `com.huaweicloud:esdk-obs-java:3.26.6` and the existing adapter boundary. Internal put DTOs will
support bounded content type and metadata required by canonical JSON evidence, while OBS SDK types remain inside
`agentic-adapter-obs`. This avoids a second client and preserves exception mapping and rate limiting.

Alternative rejected: calling OBS from monitoring or MCP. That leaks SDK types across the enforced module boundary.

### D2: Add a production `BoundedEvidenceArtifactStore`

An OBS-backed store in `agentic-monitoring` will deterministically serialize the bounded evidence value to UTF-8 JSON, compute
SHA-256, validate identity segments, derive an object key, call the adapter, verify the response and return `StoredEvidence`.
The key format will be:

`{prefix}/{serviceCode}/{investigationId}/{evidenceType}/{sha256}.json`

`serviceCode` is server configured. `investigationId` and `evidenceType` are bounded and whitelist validated. The caller never
supplies bucket, prefix or complete key.

Alternative rejected: wrapping every single evidence item in the existing ZIP handoff package. That package targets cross-zone
handoff and introduces unnecessary manifest/ZIP overhead for the runtime evidence port.

### D3: Remove approval coupling, keep deployment admission

The evidence service will no longer call approval consume/restore. Approval controllers, stores and related beans will be
removed from the OBS path. Risk control moves to default-off activation, validated server configuration, fixed destination,
restricted IAM, bounded payload validation and deployment authorization.

Alternative rejected: synthesizing an approval from the Agent. It would retain the appearance of a control while providing no
independent authorization and would contradict the user's requested operating model.

### D4: Idempotency uses content-addressed object identity

Retries of the same investigation/type/content write the same object key. A successful SDK response must match the derived key;
HEAD metadata provides verification. Different content produces a different digest and object. No list or overwrite search is
needed.

### D5: Encryption remains server controlled

The existing SSE-KMS header is retained and encryption parameters are never accepted from callers. Huawei Cloud's Java SDK
contract makes `kmsKeyId` optional: when configured, it is sent; when empty, the adapter omits only the key-ID field and OBS uses
or creates the account's default KMS master key. The adapter must no longer reject an empty key ID, but must always set
`ServerEncryption.OBS_KMS`.

### D6: Real verification is explicit and leaves an audit artifact

An integration test/profile reads endpoint, bucket, prefix, optional KMS key and credentials from environment. For this run,
the environment supplies `obs-perf168w-public` and `evidence`; neither is compiled into the test or production defaults. The
test uploads a small non-sensitive canonical verification payload below `{configuredPrefix}/verification/...`, then uses HEAD
and GET to validate size, ETag, content and SHA-256 metadata. It does not print credentials or content and does not delete the
object.

## Risks / Trade-offs

- [Removing per-package approval increases write autonomy] → Keep default-off gates, fixed destination, IAM least privilege,
  bounded content and no management operations.
- [OBS outage can block evidence creation] → Fail closed with stable retryable upstream errors; do not emit dangling references.
- [Content-addressed retries may rewrite the same key] → Bytes are identical by digest; validate key and metadata on success.
- [Existing approval endpoints may have callers] → Disable first, remove wiring in a documented compatibility step, and retain
  inert historical store files until operators confirm no dependency.
- [Real E2E creates a persistent object] → Use a bounded non-sensitive verification payload and report its object reference.

## Migration Plan

1. Add the OBS-backed bounded artifact store and adapter DTO support behind a new default-off automatic-storage gate.
2. Remove approval consume/restore from put and update unit/architecture tests.
3. Add validated environment/Helm mappings for endpoint, bucket, prefix, KMS key and service code without credential values.
4. Run offline module tests and strict OpenSpec validation.
5. Run the explicitly authorized real OBS E2E with runtime bucket `obs-perf168w-public` and prefix `evidence`, then retain the
   verification reference.
6. Enable automatic storage only after readiness succeeds; rollback by disabling the automatic-storage and OBS gates.
