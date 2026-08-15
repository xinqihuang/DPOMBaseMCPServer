# Change: Harden read-only runtime boundary

## Why

DPOMBaseMCPServer declares itself a read-only evidence service but currently registers CES notification-mask write tools in the default MCP tool surface. This violates the service contract and allows an investigation agent to mutate production monitoring state.

## What Changes

- Do not register write tools in the default or production runtime.
- Require both an explicit `action-enabled` Spring profile and `dpom.mcp.write-tools-enabled=true`.
- Keep read-only notification-mask discovery available for audit.
- Document that DPOMAgent must never enable or call the write tools.
- Verify the effective MCP `tools/list` surface.

## Boundaries

- No new write capability.
- No automatic production execution.
- No arbitrary shell or credential exposure.
