---
name: huawei-apm-alarm-diagnosis
description: 诊断华为云 APM（应用性能管理）告警的根因排查工作流，基于 DPOMBaseMCPServer 提供的只读监控工具（list_apm_alarm_data / show_apm_trend / query_traces / show_trace_events / correlate_incident 等）。当用户给出一条 APM 告警（或 app 名 + 时间窗）并要求"诊断 / 排查 / 定位根因 / 为什么告警"时使用；也适用于 AOM/CES 关联排查。
---

# 华为云 APM 告警诊断

把一条 APM 告警从"触发了"推进到"根因是什么 + 证据链 + 建议动作"。本 Skill 编排 DPOMBaseMCPServer 暴露的**只读**监控工具，按固定的发现链 / 下钻链调用，避免漏证据、避免乱编上游标识。

## 适用与边界

- **适用**：拿到一条 APM 告警（或 `app_name` + 时间窗），要查"为什么告警 / 根因在哪 / 影响范围"。
- **只读**：所有工具均为只读查询。**不做**故障恢复 / 配置变更 / 屏蔽规则的写操作（除非用户单独明确要求，且对应工具确实是写工具如 `create_notification_mask`）。
- **诊断完输出**一份结构化报告，不要止步于堆数据。

## 铁律（违反会查错方向，务必遵守）

来自项目 `AGENTS.md` §4.3：

1. **禁止凭记忆/先验捏造上游标识**：`business_id`、`env_id`、`collector_id`、`monitor_item_id`、`metric_set`、`collector_name`、`function`、`trace_id`、`view_config` 等，**必须**来自前置发现工具的真实响应或告警载荷，绝不自己编。
2. **`collector_id` 是 env 局部的**：同一数字在不同 env 指向不同 collector。每次都用 `show_env_monitor_items` 重新解析，禁止硬编码或跨 env 复用。
3. **严格按调用顺序**：发现链 / 趋势链 / 下钻链各有固定顺序（见下），跳步会拿不到必需入参。
4. **缺参数就先调发现工具补齐**，不要猜。

## 时间格式速查（各工具不一致，最常见的踩坑点）

| 工具 | 时间参数与格式 |
|---|---|
| `list_apm_alarm_data` | `alarm_start_time` / `alarm_end_time`：上游格式字符串 |
| `show_apm_trend` | `start_time` / `end_time`：字符串，原样透传上游 |
| `list_alarms` (CES) | `from` / `to`：ISO 8601，如 `2024-02-11T10:00:00+08:00` |
| `query_logs` (AOM) | `start_time` / `end_time`：UTC 毫秒 |
| `list_aom_events`、`query_aom_metric_data` | `time_range`：`startMillis.endMillis.durationMinutes`，`-1` 表示服务端计算（如 `-1.-1.60` = 近 60 分钟） |
| `query_lts_logs` | `start_time_millis` / `end_time_millis`：UTC 毫秒 |

诊断时围绕告警的 `alarm_first_time`/`alarm_last_time` 取窗口，建议前后各放宽 5~15 分钟看清趋势拐点。

## 诊断主流程

### 步骤 0 — 定位告警，提取关键字段
调 `list_apm_alarm_data`，按 `app_name` / `alarm_start_time` / `alarm_end_time` / `severity(alarm_level)` / `status` / 实例 IP 过滤。
从命中的告警记录提取后续要用的字段并记下来：
- `id`（→ 给 `list_alarm_notify` 查投递）
- `monitor_item`（→ 解析 collector_id 看趋势）
- `env_id` / 实例 IP / region
- 告警表达式 `alarm expression`、严重度、生命周期时间戳
- 若载荷里带 `trace_id`，记下（→ 直接进下钻链）

> 可选：`list_alarm_notify(alarm_data_id = 上面的 id)` 确认告警是否真的投递、走了哪些渠道（sms/email/webhook）、成功与否。注意 SDK 类型是 Integer，id 超过 Integer.MAX_VALUE 会被上游拒。

### 步骤 1 — 量化指标趋势（确认异常真实存在 + 拐点时刻）
APM 趋势发现链，**严格三步**：
1. `show_env_monitor_items(env_id)` → 从 `monitor_item_info_list[]` 用告警的 `monitor_item_id` 解析出 `collector_id`（env 局部！）。
2. `show_apm_monitor_item_view_config(collector_id, env_id)` → 从 `view_row_list[*].view_list[*]` 里**挑且仅挑一个** view。
3. `show_apm_trend(view_config = 上一步选中 view 的字段，原样复制)` → 拿 `line_list[*]{time,value}`。
   - 复制时注意：STEP2 的 `ViewBase.latest` 是 Boolean，本步 `TrendView.latest` 是 String，转 `"true"`/`"false"`。
   - 不要自己编 `collector_name` / `metric_set` / `function`，必须来自 STEP2 响应。

用趋势确认：异常是否真实、量级多大、什么时刻开始劣化。

### 步骤 2 — 定位故障服务（哪个组件慢/错）
1. `query_traces`：按时间窗 + `error` 标志 + 最小耗时 `min elapsed` + 入口 url/method 过滤，拿到候选 span 摘要与 `traceId`。
2. `get_service_topology(trace_id)`：还原调用图，看时间/错误集中在哪个服务节点、哪条边。

### 步骤 3 — 下钻到根因（组件内部哪一步）
1. `show_trace_events(trace_id)`：拿该 trace 全部调用链事件（方法调用 / SQL / 远程调用，含耗时、状态码、错误标志）。挑出可疑事件。
2. `show_event_detail(trace_id, span_id, event_id/id, env_id)`：**四个参数全部从上一步响应复制**。tags 里就是根因数据——异常类与消息、SQL 语句、HTTP 状态等。
3. 若出现 clob 引用（超长字段，如完整堆栈/完整 SQL）→ `show_clob_detail(clob_id, env_id)` 取全文。`clob_id` 必须来自上一步响应。

### 步骤 4 — 关联证据（按需，强化结论）
- **快速横切**：`correlate_incident`（一次并发查 CES 告警 + AOM 日志 + APM trace/topology，某分支缺参或失败只标 skipped 不影响其他分支）。想快速看"事发时刻全栈在发生什么"用它。
- **应用日志佐证**：`query_logs`（AOM）或 `query_lts_logs`（+ `query_lts_log_context` 取上下文行）在实例/时间附近捞 ERROR/Exception。
- **基础设施层**：`list_alarms`（CES，看 ECS/RDS/EVS 等基础资源告警）、`list_aom_events`（AOM 告警/事件）。
- **资源饱和度**：`list_aom_metrics`（先发现真实 metric/dimension 名）→ `query_aom_metric_data`（看 CPU/内存/连接数等是否打满）。

### 步骤 5 — 输出诊断报告
按下面结构汇总（见 `report-template.md`）：告警摘要 → 异常确认（趋势量级+拐点）→ 故障服务定位 → 根因（具体异常/SQL/状态码 + 证据引用）→ 关联信号 → 建议动作。每个结论都要能指回某次工具响应的具体字段，不臆断。

## 典型调用链一图流

```
list_apm_business ─► search_apm_application ─► show_env_monitor_items
   (business_id)         (env_id, 探针状态)        (monitor_item↔collector_id)
                                                          │
告警入口: list_apm_alarm_data ──(id)──► list_alarm_notify │
   │ monitor_item_id / env_id / trace_id                  ▼
   │                              show_apm_monitor_item_view_config ─► show_apm_trend
   │ trace_id                                                            (确认异常+量级)
   ▼
query_traces ─► get_service_topology ─► show_trace_events ─► show_event_detail ─► show_clob_detail
 (找慢/错trace)     (定位服务节点)         (定位内部步骤)        (拿根因tags)        (取超长堆栈/SQL)

横切/佐证: correlate_incident | query_logs / query_lts_logs(+context) | list_alarms(CES) | list_aom_events | list_aom_metrics→query_aom_metric_data
```

若用户只给了 `app_name` 没有 `business_id`：先 `list_apm_business` 拿 `id`，再 `search_apm_application` 拿 `env_id`。多数工具 `business_id` 省略时会回落到服务端配置的默认租户。

## 错误处理

工具返回的错误带 `errorCode` 和 `retryable`：
- `UPSTREAM_THROTTLED` / `TIMEOUT` 且 `retryable=true`：可短暂退避后重试。
- `UPSTREAM_AUTH_FAILED`：鉴权问题，停下来告知用户（AK/SK 配置），别空转重试。
- `INVALID_PARAM`：多半是入参没从发现工具拿、或时间格式用错——回查上面的格式表与调用顺序。
- 报告里如实标注哪些分支 skipped / 失败，不要假装覆盖全了。
