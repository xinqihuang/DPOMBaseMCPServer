# Smoke Tests

Post-deploy verification scripts. Run these from a host that can reach the
DPOMBaseMCPServer service (typically a CCE pod in the Guiyang One region or
a port-forwarded tunnel to one).

## Prerequisites

- `curl` and `jq` available
- Service URL of the MCP server (e.g. `dpom-mcp-server.namespace.svc:8080`)
- A valid SSE-capable MCP client OR direct HTTP POST to the message endpoint

## Scripts

- `smoke-list_ces_metrics.sh <host:port>` — exercises the `list_ces_metrics`
  tool against the real CES API behind the deployed pod. Asserts non-empty
  results for SYS.ECS, empty results for a bogus namespace, and INVALID_PARAM
  for a too-large limit.

## How they work

The MCP server exposes SSE at `/sse` and accepts JSON-RPC tool calls at
`/mcp/messages`. The scripts use `curl` to POST tool-call requests and
`jq` to parse the JSON-RPC response.

For details of the JSON-RPC envelope used by MCP, see the MCP spec or
inspect with `npx @modelcontextprotocol/inspector` against a running pod.
