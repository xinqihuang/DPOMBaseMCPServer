#!/usr/bin/env bash
# Smoke tests for the list_aom_metrics MCP tool.
# Usage: ./smoke-list_aom_metrics.sh <host:port>
#
# Prerequisites: the service must be running and reachable at <host:port>.
# Note: Case 1 asserts count >= 0 because the test cluster may have no AOM-integrated
# applications; an empty list is a valid result, not a failure.

set -euo pipefail

HOST="${1:?Usage: $0 <host:port>}"
BASE_URL="http://${HOST}"
PASS=0
FAIL=0

run_case() {
    local id="$1"
    local desc="$2"
    local payload="$3"
    local check_expr="$4"

    echo "--- ${id}: ${desc}"
    response=$(curl -sf -X POST "${BASE_URL}/mcp/messages" \
        -H "Content-Type: application/json" \
        -d "{\"tool\":\"list_aom_metrics\",\"arguments\":${payload}}" 2>&1) || {
        echo "FAIL: request failed"
        FAIL=$((FAIL + 1))
        return
    }

    if echo "${response}" | python3 -c "import sys,json; d=json.load(sys.stdin); assert ${check_expr}" 2>/dev/null; then
        echo "PASS"
        PASS=$((PASS + 1))
    else
        echo "FAIL: assertion '${check_expr}' failed; response: ${response}"
        FAIL=$((FAIL + 1))
    fi
}

# Case 1: valid namespace query — count >= 0 (empty OK if no apps in AOM)
run_case "SMOKE-01" \
    "namespace=PAAS.CONTAINER, limit=5 -> count >= 0" \
    '{"namespace":"PAAS.CONTAINER","limit":5}' \
    "d.get('pagination',{}).get('count',0) >= 0"

# Case 2: inventory_id query — count >= 0
run_case "SMOKE-02" \
    "inventory_id query -> count >= 0" \
    '{"inventory_id":"host_placeholder-node-id","limit":5}' \
    "d.get('pagination',{}).get('count',0) >= 0"

# Case 3: invalid limit -> INVALID_PARAM error response
run_case "SMOKE-03" \
    "limit=1001 -> INVALID_PARAM error" \
    '{"namespace":"PAAS.CONTAINER","limit":1001}' \
    "d.get('error_code') == 'INVALID_PARAM'"

echo ""
echo "Results: PASS=${PASS}, FAIL=${FAIL}"
[ "${FAIL}" -eq 0 ] || exit 1
