# Spec: diagnose_trace

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent **仅凭一个 trace_id** 就一步完成 APM 调用链故障诊断，免去手工串联
`get_service_topology` → `show_trace_events` → `show_event_detail` → `show_clob_detail` 四步。

典型场景:
- Agent 从告警载荷或 `query_traces` 拿到一个 `trace_id`，需要快速定位"这条请求慢/错在哪一步、根因是什么"
- 一条 trace 有上百条 event，Agent 不想自己拉全量再逐条判断哪些是出错/最慢的

定位: APM trace 维度的**编排工具**，与跨组件编排工具 `correlate_incident` 同属 monitoring 层
orchestration；本工具不引入新的 SDK 能力，而是编排既有 `ApmTraceService` 的只读方法。

## 2. 范围边界

**做**:
- 并发拉取调用图拓扑（`getTopology`）与全部调用链事件（`showTraceEvents`）
- 从事件中挑出可疑 span：所有 `has_error=true` 的，再（可选）按 `time_used` 倒序补足最慢的，去重后截断到上限
- 并发下钻每个可疑 span 的 `showEventDetail`，并解析其 `tags` 中形如 `*_clob_id` 的 clob 引用为全文（`showClobDetail`）
- 阶段独立：任一阶段（拓扑/事件/详情/clob）失败仅记入 `errors`、对应字段置空，不中断整体

**不做**:
- 不引入新的华为云 SDK 调用（全部复用 `ApmTraceService` 既有方法）
- 不做按时间窗的 trace 搜索（用 `query_traces`）
- 不做跨组件关联（用 `correlate_incident`）
- 不缓存（实时数据）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `diagnose_trace`
- description（Agent 看到的）: 见 `DiagnoseTraceTool` 的 `@Tool` 文案（一步编排 topology+events+detail+clob，返回聚焦根因的诊断包；trace_id 不可臆造；实时数据）。
- annotations: `readOnlyHint=true` / `destructiveHint=false` / `idempotentHint=true`（注：Spring AI 1.0.4 `@Tool` 暂不实际透出 annotation，见 T20 遗留项）

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `trace_id` | string | 是 | — | APM trace id，来自告警载荷或 `query_traces`，禁止臆造 |
| `business_id` | long | 否 | null（回落 `huaweicloud.apm-business-id`） | 仅用于 clob 下钻 |
| `max_suspect_events` | int | 否 | 5 | 最多深挖的可疑 event 数；≤0 视为默认 |
| `include_slowest` | bool | 否 | true | 无出错 event 时是否仍纳入最慢的若干 span |

**输入校验**（service 层）:
- `trace_id` 为空/空白 → `INVALID_PARAM`
- 其余参数为空走默认值

### 3.3 输出契约（成功）

```json
{
  "trace_id": "trace-abc",
  "topology": { "global_trace_id": "...", "nodes": [], "lines": [] },
  "total_event_count": 12,
  "error_event_count": 1,
  "suspects": [
    {
      "span_id": "span-1",
      "event_id": "evt-100",
      "env_id": 1306682,
      "type": "SQL",
      "method": "querySql",
      "class_name": "com.example.OrderDao",
      "time_used": 2456,
      "code": 500,
      "has_error": true,
      "error_reasons": "SQLTimeoutException",
      "selected_reason": "error",
      "tags": { "sql_clob_id": "clob-778", "exceptionType": "..." },
      "clobs": [ { "tag_key": "sql_clob_id", "clob_id": "clob-778", "text": "java.sql.SQLTimeoutException: ..." } ]
    }
  ],
  "errors": []
}
```

字段说明:
- `topology` 为 `ApmGetTopologyResponse`；拓扑阶段失败时为 `null`
- `total_event_count` / `error_event_count`：事件阶段失败时为 `null`
- `suspects[].selected_reason`：`error`（出错）或 `slow`（最慢）
- `suspects[].tags`：详情返回的完整 tags；详情下钻失败时为 `null`
- `suspects[].clobs[].text`：解析全文；该 clob 下钻失败时为 `null`（原因见 `errors`）
- `errors[]`：各阶段非致命错误，`stage` 形如 `topology` / `events` / `detail:<spanId>` / `clob:<clobId>`

### 3.4 输出契约（失败）

仅入参校验失败时返回标准 `ErrorResponse`（`error_code=INVALID_PARAM`）。
上游单阶段失败**不**走整体失败，而是落到成功响应的 `errors[]`。

## 4. 与华为云 SDK 的映射

**不直接调用 SDK**。本工具是 monitoring 层编排器，复用 `ApmTraceService` 的：
`getTopology` / `showTraceEvents` / `showEventDetail` / `showClobDetail`。
各底层方法与 SDK 的字段映射、版本钉死见各自工具 spec（`get_service_topology` / `show_trace_events`
/ `show_event_detail` / `show_clob_detail`）。

**clob 引用约定**: event 详情 `tags` 中 key 以 `_clob_id` 结尾的条目（如 `stacktrace_clob_id`
/ `sql_clob_id`），其 value 即传给 `showClobDetail` 的 `clob_id`。

## 5. 非功能要求

- **限流**: 单次调用扇出到多次 APM 只读调用（1 topology + 1 events + N detail + M clob），全部经
  `apm-readonly` RateLimiter；流量增长时需关注配额（与 `correlate_incident` 同类遗留项）。
- **并发**: JDK 21 虚拟线程 `newVirtualThreadPerTaskExecutor`；topology 与 events 并发，
  可疑 span 的 detail+clob 并发。
- **重试**: 复用底层 service 的 `huaweicloud-retryable`。
- **可观测**: INFO 日志含 traceId / 入参 / 事件计数 / suspects 数 / 阶段错误数。

## 6. 测试策略（DoD）

| ID | 类 | 用例 |
|---|---|---|
| UT-S1 | Service | happy path：挑出 error+slow span，详情 `*_clob_id` 解析为全文 |
| UT-S2 | Service | 拓扑阶段失败 → `topology=null` + `errors` 含 `topology`，suspects 照常 |
| UT-S3 | Service | clob 下钻失败 → 该 clob `text=null` + `errors` 含 `clob:*` |
| UT-S4 | Service | 无出错事件 → 按 `include_slowest` 默认纳入最慢，`error_event_count=0` |
| UT-S5 | Service | `trace_id` 空白 → `INVALID_PARAM` |
| UT-T1 | Tool | 入参完整透传到 `DiagnoseTraceRequest` |
| UT-T2 | Tool | 可选参数 null 原样透传 |
| UT-T3 | Tool | service `InvalidParamException` → `ErrorResponse(INVALID_PARAM)` |
| UT-T4 | Tool | `UpstreamException` → `ErrorResponse` 携带 upstream trace id |

**不写 ContractTest**: 本工具不新增 SDK 响应 DTO 映射（仅编排既有、已各自有 ContractTest 的 service
方法），与 `correlate_incident` 一致；编排正确性由 UT-S* 覆盖。

## 7. 验收标准

- [x] 所有 UT-S / UT-T 通过（9 条）
- [x] Checkstyle 0
- [x] 通过 `McpTool` 标记自动注册，无需改 `McpServerConfig`
- [x] 日志含 traceId / 入参 / 计数 / 阶段错误数
- [ ] 冒烟脚本 `scripts/smoke/smoke-diagnose_trace.sh`（遗留）
- [ ] Micrometer 指标看板（遗留）
