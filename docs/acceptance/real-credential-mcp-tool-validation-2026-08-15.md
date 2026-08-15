# Real-credential MCP tool validation — 2026-08-15

## Scope and safety

- Runtime: local `DPOMBaseMCPServer`, SSE transport, Huawei Cloud SDK `3.1.177` unchanged.
- Credential source: operator-configured Windows user environment; AK/SK values were never printed or persisted in this report.
- Region: APM endpoint configuration plus `cn-north-9` resource region.
- Registered tools: 26 read-only/default tools.
- CES write tools `create_notification_mask` and `delete_notification_masks` were not registered or invoked.
- No production mutation was performed.

## Summary

| Result | Count | Meaning |
|---|---:|---|
| PASS | 19 | Real upstream call succeeded or returned a valid empty result |
| DEGRADED | 2 | Useful partial result, but unsafe/incomplete orchestration behavior exists |
| FAIL | 2 | Valid real request cannot be completed because of implementation/contract defect |
| BLOCKED | 3 | No suitable real prerequisite identifier was available; not treated as a credential failure |
| Total | 26 | All default registered tools accounted for |

## Tool matrix

| Tool | Result | Real validation evidence |
|---|---|---|
| `hello_world` | PASS | Returned the expected greeting; local transport/tool dispatch works. |
| `list_apm_business` | PASS | Returned real business nodes including `dpframework` (`111092`). |
| `search_apm_application` | PASS | Resolved `DPSouthProxyService/china2023_pvms`, env `2297428`, app `149064`, with online instances. |
| `show_env_monitor_items` | PASS | For env `2297428`, resolved JVM monitor item `121982` and env-local collector `18`. |
| `show_apm_monitor_item_view_config` | PASS | Returned authoritative JVM views including `memoryPool`, `groupBy=name`, and real field functions. |
| `show_apm_trend` | PASS | Called the real APM trend endpoint and returned minute-level JVM memory-pool series for instance `2141002`. |
| `query_traces` | PASS | Returned real spans and pagination metadata; an error-only query returned `total=12687`. |
| `show_trace_events` | PASS | Returned the complete event sequence for a real trace. |
| `show_event_detail` | PASS | Returned a real event with tags and attachment metadata. |
| `get_service_topology` | PASS | Returned real multi-service nodes and edges for the selected trace. |
| `diagnose_trace` | DEGRADED | Returned topology, event count and suspects, but its fan-out hit the shared local APM rate limiter; several detail stages were omitted as `UPSTREAM_THROTTLED`. |
| `show_clob_detail` | BLOCKED | Neither tested real trace exposed a clob id. Calling with an invented id would test only failure mapping, not the capability. |
| `list_apm_alarm_data` | FAIL | Both minimal and filtered real requests returned upstream `invalid parameter`; unrelated `list_alarm_notify` proves credentials and business context are valid. |
| `list_alarm_notify` | PASS | Alarm `16557997` returned three real notification records: ALARM, CONVERGENCE and RECOVER. |
| `list_ces_metrics` | PASS | Returned real `SYS.ECS` metrics and a continuation marker (`total=12380`). |
| `query_ces_metric_data` | PASS | Queried a discovered ECS disk metric and returned 5-minute datapoints. |
| `batch_query_ces_metric_data` | PASS | Returned two discovered ECS metric series in one real request. |
| `list_alarms` | PASS | Returned real CES alarm histories with conditions and datapoints. |
| `list_notification_masks` | FAIL | Request failed locally with `request field body read null value`; the adapter/SDK request shape is incorrect. |
| `list_aom_events` | PASS | Returned real active CCE scheduling alarms and a next marker. |
| `list_aom_metrics` | PASS | With a required discovery seed, returned real `PAAS.CONTAINER/cpuUsage` series definitions and dimensions. Namespace-only discovery fails upstream despite the MCP description claiming it is sufficient. |
| `query_aom_metric_data` | PASS | Used dimensions returned by discovery and obtained real 5-minute CPU datapoints. |
| `query_logs` | PASS | Real AOM log query completed successfully; the selected CodeCache search returned a valid zero-match result. |
| `correlate_incident` | DEGRADED | Returned CES and AOM sections, but the APM branch failed because `x-business-id` was null. CES retrieval was effectively unbounded (`total` about 100k), producing an unsafe context-size explosion. |
| `query_lts_logs` | BLOCKED | Requires a real LTS log-group id and log-stream id; no discovery tool or configured identifiers are available. |
| `query_lts_log_context` | BLOCKED | Depends on the same LTS identifiers plus a cursor/line from `query_lts_logs`. |

## Defects requiring changes

### P0 — bound `correlate_incident`

The tool can pull an extremely large CES alarm set and serialize it into one MCP response. It must apply an explicit limit, time filtering and evidence summarization before returning data. The APM branch must receive the effective business id instead of invoking `query_traces` with a null header context.

### P1 — repair `list_apm_alarm_data`

Minimal `{businessId, page, pageSize}` and incident-specific filters both receive upstream `invalid parameter`. Add a golden request-body contract test against SDK `ListAlarmDataRequest` and compare it with the working upstream API shape.

### P1 — repair `list_notification_masks`

The SDK request requires a non-empty body, while the current adapter leaves it null. Construct the correct body object even when all filters use defaults.

### P1 — make `diagnose_trace` rate-limit aware

The orchestration performs topology, event-list and many detail calls against the same `apm-readonly` limiter. Add a bounded detail budget and either reserve permits, pace requests, or return a deterministic truncation reason before hitting the limiter.

### P2 — correct AOM discovery contract

The MCP schema says namespace alone is enough, but upstream rejects a request when both metric name and dimensions are empty. Require or clearly request a seed metric/dimension, or implement a separate supported inventory discovery path.

### P2 — add LTS discovery/configuration

Expose a read-only log-group/log-stream discovery tool, or configure approved identifiers per environment. Until then, the two LTS tools cannot be validated or used autonomously by DPOMAgent.

## Overall verdict

Credentials, signing, regional connectivity, SSE transport and the principal APM/CES/AOM read paths are working. The inventory is **not yet fully accepted for autonomous DPOMAgent use** because two tools fail, two orchestration tools degrade unsafely, and LTS has no discoverable entry point.
