#!/usr/bin/env bash
# smoke-list_ces_metrics.sh
#
# Post-deploy smoke test for the list_ces_metrics MCP tool.
# Runs three checks against a deployed DPOMBaseMCPServer.
#
# Usage:
#   ./smoke-list_ces_metrics.sh <host:port>
#
# Example:
#   ./smoke-list_ces_metrics.sh dpom-mcp-server.smartom.svc:8080
#
# Exits non-zero on the first failed assertion.

set -euo pipefail

HOST="${1:-}"
if [[ -z "$HOST" ]]; then
    echo "Usage: $0 <host:port>" >&2
    exit 2
fi

ENDPOINT="http://${HOST}/mcp/messages"

# JSON-RPC envelope for a tool call. Spring AI MCP server exposes the standard
# 'tools/call' method.
call_tool() {
    local name="$1"
    local args_json="$2"
    local id="$3"

    curl -fsS -X POST "$ENDPOINT" \
        -H 'Content-Type: application/json' \
        -d "$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": ${id},
  "method": "tools/call",
  "params": {
    "name": "${name}",
    "arguments": ${args_json}
  }
}
EOF
)"
}

# -----------------------------------------------------------------------------
# Test 1: namespace=SYS.ECS, limit=5 -> expect non-empty metrics, fields complete.
# -----------------------------------------------------------------------------
echo "[1/3] list_ces_metrics with namespace=SYS.ECS, limit=5"
RESP1=$(call_tool list_ces_metrics '{"namespace":"SYS.ECS","limit":5}' 1)
COUNT1=$(echo "$RESP1" | jq -r '.result.content[0].text' 2>/dev/null \
    | jq -r '.metrics | length' 2>/dev/null \
    || echo "PARSE_ERR")

if [[ "$COUNT1" == "PARSE_ERR" || -z "$COUNT1" ]]; then
    echo "FAIL: could not parse response."
    echo "Raw response: $RESP1"
    exit 1
fi
if [[ "$COUNT1" -lt 1 ]]; then
    echo "FAIL: expected at least 1 metric for SYS.ECS, got $COUNT1."
    echo "Raw response: $RESP1"
    exit 1
fi
echo "  OK: $COUNT1 metric(s) returned"

# -----------------------------------------------------------------------------
# Test 2: bogus namespace -> expect empty metrics, no error.
# -----------------------------------------------------------------------------
echo "[2/3] list_ces_metrics with namespace=SYS.DOESNOTEXIST"
RESP2=$(call_tool list_ces_metrics '{"namespace":"SYS.DOESNOTEXIST","limit":5}' 2)
COUNT2=$(echo "$RESP2" | jq -r '.result.content[0].text' 2>/dev/null \
    | jq -r '.metrics | length' 2>/dev/null \
    || echo "PARSE_ERR")
ERR2=$(echo "$RESP2" | jq -r '.result.content[0].text' 2>/dev/null \
    | jq -r '.error_code // empty' 2>/dev/null || echo "")

if [[ -n "$ERR2" ]]; then
    echo "FAIL: expected empty result, got error_code=$ERR2."
    echo "Raw response: $RESP2"
    exit 1
fi
if [[ "$COUNT2" != "0" ]]; then
    echo "FAIL: expected 0 metrics for bogus namespace, got $COUNT2."
    echo "Raw response: $RESP2"
    exit 1
fi
echo "  OK: empty result, no error"

# -----------------------------------------------------------------------------
# Test 3: limit=1001 -> expect INVALID_PARAM.
# -----------------------------------------------------------------------------
echo "[3/3] list_ces_metrics with limit=1001 (should be rejected)"
RESP3=$(call_tool list_ces_metrics '{"limit":1001}' 3)
ERR3=$(echo "$RESP3" | jq -r '.result.content[0].text' 2>/dev/null \
    | jq -r '.error_code // empty' 2>/dev/null || echo "")

if [[ "$ERR3" != "INVALID_PARAM" ]]; then
    echo "FAIL: expected error_code=INVALID_PARAM, got '$ERR3'."
    echo "Raw response: $RESP3"
    exit 1
fi
echo "  OK: INVALID_PARAM returned"

echo
echo "All smoke checks passed."
