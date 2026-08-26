## 1. Task Contract and Characterization

- [x] 1.1 Add `docs/tasks/T39-automatic-obs-evidence-storage.md` with scope, exclusions, artifacts, security boundaries and acceptance commands derived from this change.
- [x] 1.2 Record the clean baseline and add failing characterization tests for approval-free put, optional default KMS key, deployment-scoped destination and default-off behavior before changing production code.

## 2. OBS SDK Adapter

- [x] 2.1 Test-first extend the internal OBS put DTO and adapter mapping for bounded content type and SHA-256 metadata without leaking SDK types outside `agentic-adapter-obs`.
- [x] 2.2 Test-first change SSE-KMS mapping so an explicit server key is sent when configured and an empty key ID omits only the key-ID field while retaining `ServerEncryption.OBS_KMS`.
- [x] 2.3 Preserve bounded HEAD/GET, rate limiting, stable `ObsException` mapping, secret-safe logs and the fail-closed disabled adapter; run the adapter module tests and checkstyle.

## 3. Automatic Diagnostic Evidence Storage

- [x] 3.1 Test-first implement deterministic bounded UTF-8 JSON serialization, SHA-256 calculation and whitelist validation for serviceCode, investigationId and evidenceType.
- [x] 3.2 Test-first implement the OBS-backed `BoundedEvidenceArtifactStore` using object keys `{prefix}/{serviceCode}/{investigationId}/{evidenceType}/{sha256}.json` and return verified `StoredEvidence` values.
- [x] 3.3 Wire the OBS-backed store into the executable composition root only when the new automatic-storage gate and OBS adapter are enabled; prove disabled/incomplete configuration never contacts OBS and fails readiness safely.
- [x] 3.4 Add integration tests proving an accepted correlated diagnostic result is uploaded before its `EvidenceRecord` is returned and upload failure cannot produce a dangling evidence reference.

## 4. Remove Per-Package Approval

- [x] 4.1 Remove approval consume/restore from OBS put orchestration and update tests so valid bounded content uploads without an approval while all destination, checksum, size and content controls remain enforced.
- [x] 4.2 Disable/remove the OBS approval REST composition, store wiring and approval-specific metrics/audit; retain inert historical files during the compatibility window and prove no approve/revoke MCP or REST surface is exposed.
- [x] 4.3 Update architecture/security tests and operator documentation to replace per-package approval with default-off deployment gates, deployment-scoped destination and least-privilege IAM.

## 5. Configuration and Deployment

- [x] 5.1 Add environment-backed application and Helm values for the automatic-storage gate, endpoint, bucket, prefix, optional KMS key and service code; keep AK/SK sourced only from `HUAWEICLOUD_AK/HUAWEICLOUD_SK` and commit no credential value.
- [x] 5.2 Prove production code and deployment defaults contain no test bucket; pass `obs-perf168w-public`, `evidence` and the matching endpoint only as runtime parameters to the authorized verification.

## 6. Verification and Evidence

- [x] 6.1 Add an explicitly gated real OBS E2E that uses environment-supplied bucket/prefix, uploads a bounded non-sensitive object below `{prefix}/verification/`, validates HEAD/GET size, ETag, bytes and SHA-256 metadata, and leaves the object as verification evidence.
- [x] 6.2 Run the real E2E using process-only AK/SK injection, record the resulting object reference and bounded outcome without credentials or evidence body, and confirm no secret entered Git or logs.
- [x] 6.3 Run focused module tests, full `mvn verify`, architecture scans and `openspec validate remove-obs-upload-approval --strict`; publish an acceptance report with exact commands, totals, skips and the retained OBS verification reference.
