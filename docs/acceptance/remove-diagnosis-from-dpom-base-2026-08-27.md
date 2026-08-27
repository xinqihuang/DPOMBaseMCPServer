# DPOMBaseMCPServer Evidence-Only Boundary Acceptance Report

Date: 2026-08-27

## Result

PASS. DPOMBaseMCPServer is now an evidence-only MCP service. It collects, normalizes,
correlates and packages evidence for DPOMAgent; it does not own diagnosis, Investigation
state, model invocation, business orchestration or diagnosis event publication.

DPOMAgent is the sole Investigation/Diagnosis authority and Diagnosis Event/Progress
producer. Production alarm-rule changes remain the responsibility of
HuaweiCloudAlarmChangeGuard.

## Removed responsibilities

- Removed Maven modules: `agentic-diagnosis`, `agentic-persistence`, `agentic-messaging`.
- Removed Investigation lifecycle, hypothesis/conclusion, diagnosis report, progress API,
  persistence mappings and deployment SQL from the active service.
- Removed Kafka diagnosis publication, replay, outbox and related configuration.
- Removed `diagnose_trace`, APM alarm-rule mutation, and CES notification-mask create/delete
  tools and adapters.
- Removed repository-external `../contracts` test-source injection and producer conformance
  owned by DPOMAgent.
- Removed per-upload approval checks from OBS evidence transfer; OBS location and enablement
  remain environment-configurable.

No destructive database migration was added. Deleted persistence SQL was service-owned
deployment material that is no longer part of the reactor.

## Retained surface

- Read-only APM, AOM, CES, LTS and CCE evidence collection.
- Resource discovery and deterministic evidence correlation/packaging.
- CMDB topology/resource lookup.
- Controlled OBS evidence artifact Put/Head/Get with configurable bucket and prefix.
- Common error handling, resilience, audit context and evidence metadata.

The architecture guard recognizes 28 default MCP tools and 3 OBS tools gated by OBS
configuration. Every exposed tool is explicitly allowlisted as evidence collection,
discovery, correlation or controlled artifact transfer.

## Verification evidence

Environment:

- JDK: 21.0.11
- Maven: 3.9.16
- Reactor: 10 modules

| Check | Result | Evidence |
| --- | --- | --- |
| Focused architecture tests | PASS | `EvidenceOnlyArchitectureTest` and `MonitoringArchitectureTest` |
| Workspace full build | PASS | `mvn clean verify -DskipITs`; 399 tests, 0 failures, 0 errors, 1 skipped; Checkstyle 0 |
| Clean-copy independence | PASS | Same full build in a fresh clone with no sibling `contracts`; all 10 modules succeeded |
| Packaged application startup | PASS | Repackaged JAR started with configured environment credentials, registered 28 default tools, then shut down cleanly |
| Forbidden production scan | PASS | No active diagnosis, Investigation, persistence, Kafka producer, APM mutation, CES create/delete or external-contract reference |
| OpenSpec validation | PASS | `openspec validate remove-diagnosis-from-dpom-base --strict` |

The OBS real-cloud end-to-end test remains opt-in and was the single skipped test in the
default build. Existing configured credentials were used only for the runtime startup check;
no credential value, project identifier or secret is recorded in this report or committed
content.

## Commands

```text
mvn -pl agentic-mcp -am test -DskipITs -Dtest=EvidenceOnlyArchitectureTest,MonitoringArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false
mvn clean verify -DskipITs
java -jar agentic-mcp/target/agentic-mcp-0.0.1-SNAPSHOT.jar --server.port=0 --management.server.port=0
openspec validate remove-diagnosis-from-dpom-base --strict
```

The clean-copy verification applied the staged repository diff to a fresh clone and ran the
same Maven verification without any parent-directory contract source.
