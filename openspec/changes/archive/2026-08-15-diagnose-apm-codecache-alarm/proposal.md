# Change: Diagnose the reported APM CodeCache alarm

## Why

Alarm `16557997` contains conflicting service identity fields and labels a JVM memory-pool threshold breach as a CodeCache incident. A read-only evidence run is required before any mitigation is considered.

## Scope

- Resolve the target from env, instance, IP, application and pod evidence.
- Discover the env-local JVM collector and its upstream-defined memory-pool view.
- Compare exact alarm values with timestamp-aligned memory-pool series.
- Search application logs for CodeCache exhaustion signatures.
- Record evidence, uncertainty and safe follow-up; perform no production action.

## Boundaries

- No restart, scaling, configuration change or notification-mask operation.
- No arbitrary shell exposed through MCP.
- No claim of root cause without value-and-timestamp correlation.
