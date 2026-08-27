## 1. Characterization and Dependency Audit

- [x] 1.1 Record the current reactor dependency graph, Spring configuration, MCP tool catalog and repository status before removal.
- [x] 1.2 Add focused characterization tests for retained APM/AOM/CES/LTS/CCE, CMDB and OBS evidence behavior.
- [x] 1.3 Enumerate every surviving-module dependency on diagnosis, messaging, Kafka, Investigation persistence and external contracts paths.

## 2. Remove Diagnosis and Messaging Responsibilities

- [x] 2.1 Remove diagnosis/messaging imports, Spring beans, configuration properties and persistence wiring from retained modules; introduce only neutral evidence DTOs when required.
- [x] 2.2 Remove `agentic-diagnosis`, `agentic-persistence` and `agentic-messaging` from the Maven reactor and delete their source, tests and resources.
- [x] 2.3 Remove Kafka producer dependencies, Diagnosis Event/Progress configuration, publication worker, replay and diagnosis metrics from parent and surviving module POMs/configuration.
- [x] 2.4 Remove Investigation, Run, Step, Hypothesis, Conclusion, diagnosis report and publication persistence mappings from active service assembly without adding destructive DROP migrations.
- [x] 2.5 Remove `diagnose_trace` inference and CES/APM mutation adapters/tools while preserving read-only evidence queries and controlled OBS Artifact transfer.

## 3. Make Contract Tests Self-Contained

- [x] 3.1 Remove `build-helper-maven-plugin` external test-source injection and every `../contracts` or parent-directory contracts lookup.
- [x] 3.2 Remove Diagnosis Event/Progress producer conformance from DPOMBase and document DPOMAgent as its owner.
- [x] 3.3 Vendor only required Evidence Manifest schema/fixtures as classpath test resources with source commit and SHA-256 provenance, if retained tests require them.
- [x] 3.4 Verify `mvn clean verify` succeeds from a clean clone with no sibling repository, Kafka process or model credential.

## 4. Enforce Evidence-Only Architecture

- [x] 4.1 Add architecture tests that reject diagnosis modules/packages, Investigation state, LLM/model clients, diagnostic reports, Kafka producer and Diagnosis Event publication.
- [x] 4.2 Add tool-catalog tests proving all exposed MCP tools are evidence collection, discovery or controlled Artifact operations and no generic production-write tool exists.
- [x] 4.3 Update README, AGENTS, OpenSpec context and architecture documentation to state the evidence-only boundary and DPOMAgent diagnosis ownership.

## 5. Verification and Delivery

- [x] 5.1 Run focused retained-tool tests, full `mvn clean verify`, package/startup checks and static forbidden-dependency scans.
- [x] 5.2 Produce an acceptance report listing removed modules, retained tools, commands, Maven/JDK versions and objective PASS/FAIL evidence without credentials.
- [x] 5.3 Review the final diff for unrelated changes, commit the repository-scoped implementation and push the active branch to GitHub.
