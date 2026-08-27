## Context

See `proposal.md` for motivation. The repository originally followed the evidence-only module direction, but a later Phase 1B
change added `agentic-diagnosis`, `agentic-messaging`, Investigation persistence, diagnostic report construction and Kafka
publication. Those capabilities now belong exclusively to DPOMAgent. The current shared conformance setup also adds Java test
sources from `${maven.multiModuleProjectDirectory}/../contracts`, so a standalone clone is not reproducible.

## Goals / Non-Goals

**Goals:**

- Restore the dependency direction `mcp -> monitoring -> adapters -> common`.
- Preserve every existing evidence-query and controlled OBS capability.
- Remove all diagnosis state, inference, reporting and Diagnosis Event messaging.
- Make `mvn clean verify` independent of sibling repositories, Kafka and LLM configuration.

**Non-Goals:**

- Moving DPOMAgent implementation into this repository.
- Changing Huawei Cloud SDK DTO coverage or evidence query semantics.
- Removing tool-level resilience, health, metrics, audit or evidence metadata persistence.
- Adding production mutation; ChangeGuard remains separate.

## Decisions

### D1: Delete diagnosis and messaging modules instead of leaving disabled code

`agentic-diagnosis`, `agentic-persistence` and `agentic-messaging` are removed from the reactor and filesystem. The persistence
module is entirely diagnosis-owned and has no evidence-owned table to retain. Feature flags are rejected because
disabled diagnosis code still creates ownership ambiguity, dependencies and regression risk.

### D2: Classify persistence by owned data

Investigation, Run, Step, Hypothesis, Conclusion, publication intent/outbox/replay and diagnosis report mappings/migrations
are removed. Evidence metadata, OBS transfer/audit, approval metadata required by existing tool contracts and tool operational
state remain. Every retained table must have a tool/evidence owner and no diagnosis foreign key.

### D3: Remove producer contracts; pin only consumer evidence contracts

Diagnosis Event and Progress conformance moves to DPOMAgent. `build-helper-maven-plugin` external test-source injection and
parent-directory scanning are removed. If Evidence Manifest validation is still required, its exact schema/fixtures are stored
under repository test resources with a provenance manifest and loaded from the classpath.

### D4: Enforce the boundary mechanically

Architecture tests scan reactor modules, Maven dependencies, packages, Spring configuration keys and source imports for
forbidden LLM, Agent runtime, Investigation, diagnosis report, Kafka producer and Diagnosis Event publication concepts. The
guard uses narrow allow-lists for words that legitimately occur in historical docs or neutral error descriptions.

### D5: Preserve tool behavior before deletion

Focused tests for every existing adapter, monitoring service, MCP tool and OBS flow run before and after module removal. A
clean-clone verification runs from a temporary directory outside `D:\code`, proving there is no accidental sibling lookup.

## Risks / Trade-offs

- [Evidence code depends on diagnosis DTOs] -> Introduce the smallest neutral evidence DTO in common before deleting the
  dependency; do not copy Investigation or Conclusion concepts.
- [Persistence migrations were already released] -> Stop wiring diagnosis migrations in this service; do not issue destructive
  DROP migrations. Existing database tables remain recoverable until separately retired.
- [Removing modules breaks MCP composition] -> Characterize tool catalog and Spring context before changes, then compare after.
- [Contract validation coverage decreases] -> Move producer coverage to DPOMAgent and retain only repository-local evidence
  consumer coverage here.

## Migration Plan

1. Capture module dependency, MCP tool catalog and focused evidence test baselines.
2. Remove diagnosis/messaging wiring from surviving modules and isolate any neutral evidence DTOs.
3. Remove the three Maven modules, Kafka dependencies/configuration, `diagnose_trace` and cloud mutation adapters/tools.
4. Remove external contracts source/path discovery and retain only classpath evidence fixtures when needed.
5. Add architecture guards and run focused tests, full `mvn clean verify`, package/startup checks and clean-clone verification.
6. Commit and push one repository-scoped change with an acceptance report.

Rollback is a Git revert to the previous repository commit. No destructive database migration is performed by this change.
