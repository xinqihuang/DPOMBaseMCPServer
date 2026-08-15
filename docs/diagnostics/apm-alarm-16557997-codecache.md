# APM alarm 16557997 — read-only diagnostic report

## Verdict

This is **not a CodeCache-capacity incident**. The alarm values match `Par Eden Space` exactly at the alarm's last-observed minute, while the real `Code Cache` was only about 30.65% used. The most likely fault is an alarm-rule identity/filter defect: a generic JVM `memoryPool` series was evaluated without restricting `name=Code Cache`, then presented under a copied CodeCache rule name.

Confidence: **high** for metric misclassification; **medium** for the exact upstream rule-authoring defect because the rule definition API does not expose its group/filter dimensions.

## Target resolution

The runtime target is resolved from the mutually consistent fields:

- application: `DPSouthProxyService/china2023_pvms`
- env id: `2297428`
- instance id: `2141002`
- IP: `10.99.10.132`
- pod/node: `dpsouthproxyservice-19`
- region: `cn-north-9`
- business id: `111092`

`alarm_rule_name` contains `DPModelProxyService`, but conflicts with all runtime identity fields above and is therefore treated as a low-confidence copied label.

## Evidence chain

### 1. Monitor identity

`show_env_monitor_items(envId=2297428, businessId=111092)` returned:

- monitor item `121982`
- collector id `18`
- collector `JVM`
- display name `JVM监控`
- collection interval 60 seconds

This matches the alarm's monitor item and collector and avoids reusing a collector id from another environment.

### 2. Authoritative metric definition

`show_apm_monitor_item_view_config(collectorId=18, envId=2297428)` returned the upstream `memoryPool` view grouped by `name`, with `AVG(max)`, `AVG(used)`, `AVG(init)` and `AVG(committed)`.

### 3. Exact value correlation

The metric series below were fetched through MCP tool `show_apm_trend`. Its adapter calls Huawei Cloud SDK `ApmClient.showTrend(ShowTrendRequest)`, which is the real APM metric endpoint:

`POST /v1/apm2/openapi/view/metric/trend`

The request used `businessId=111092`, `envId=2297428`, `instanceId=2141002`, `monitorItemId=121982`, the upstream-discovered `JVM / memoryPool / groupBy=name` view, and the incident window `2026-08-15 11:30:00–12:15:00`.

The alarm content reports raw bytes:

- used: `986309632` bytes = **940.6181640625 MB**
- init: `1006632960` bytes = **960 MB**
- max: `1006632960` bytes = **960 MB**
- calculated utilization: **97.9811%**

At `2026-08-15 11:59:00 +08:00`, the upstream trend returned:

- `[name=Par Eden Space] used(MB)` = **940.6181640625**
- `[name=Par Eden Space] max(MB)` = **960**
- `[name=Code Cache] used(MB)` = **39.22607421875**
- `[name=Code Cache] max(MB)` = **128**
- actual Code Cache utilization = **30.6454%**

The alarm bytes and Eden series are numerically identical. The Code Cache series is nowhere near the 90% threshold.

### 4. Time-series behavior

During `11:30–12:14`, Code Cache used memory remained around 38.60–39.33 MB of 128 MB. It showed slow, normal growth, not exhaustion. Eden rose toward its limit and then dropped sharply around `12:00`, which is normal young-generation allocation and collection behavior and should not be labelled CodeCache exhaustion.

### 5. Log evidence

`query_logs(APP_LOG)` searched the incident window for `CodeCache`, `Compiler has been disabled`, and `Out of space in CodeCache`; it returned zero matches. This supports, but by itself does not prove, the misclassification conclusion.

## Tool findings

- `show_env_monitor_items`: useful and authoritative for env-local collector resolution.
- `show_apm_monitor_item_view_config`: useful; exposes the necessary `groupBy=name` contract.
- `show_apm_trend`: decisive; returned timestamped series with memory-pool identity in each title.
- `query_logs`: useful negative evidence, though it does not replace LTS stream-specific search.
- `list_apm_alarm_data`: returned upstream `invalid parameter` for the combined filters/time window. The existing alarm payload supplied by the operator was therefore used as the alarm source of record. This tool needs a separate contract fix.
- `query_traces`: not needed for the final conclusion; trace traffic does not diagnose memory-pool identity.

## Recommended action

1. Do not restart or scale the workload based on this alarm.
2. Inspect alarm rule `17680` / template `8465` and require a memory-pool dimension filter `name=Code Cache`.
3. Correct the copied service label from `DPModelProxyService` to the actual intended scope, or bind the rule dynamically to runtime application identity.
4. Add a regression check: alarm `used/max` must match the named memory-pool series at the same timestamp before DPOMAgent accepts the alarm label as evidence.
5. Fix `list_apm_alarm_data` request-contract handling so an agent can re-fetch a specific alarm reliably.

No production mutation was performed.
