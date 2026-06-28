## Context

存量回填。原始 spec `docs/specs/tools/{show_trace_events,show_event_detail,show_clob_detail}.md`，任务卡 `docs/tasks/T29-trace-diagnosis-chain.md`。本文承载主 spec 放不下的 SDK 映射与非功能要求。

## Goals / Non-Goals

**Goals:**
- 提供 trace 内部下钻到根因文本的完整链路（事件序列 → 单事件详情 → clob 全文）。

**Non-Goals:**
- trace 搜索（`query_traces`）、拓扑（`get_service_topology`）——已在前序变更交付。
- 缓存（三者均实时数据）。

## Decisions

### SDK 映射

- `show_trace_events`：`ShowTraceEvents`（`GET /v1/apm2/openapi/view/trace/get-trace-events`），query `trace_id`。`ShowTraceEventsResponse.span_event_list`→`List<ApmSpanEvent>`。
- `show_event_detail`：`ShowEventDetail`（`GET .../get-event-detail`），query 四参 `trace_id`/`span_id`/`event_id`/`env_id`（全必填）。`event_info`→`ApmSpanEvent`（单对象）。
- `show_clob_detail`：`ShowClobDetail`（`POST .../get-clob-detail`），header `x-business-id`（null 回落配置）+ body `env_id`/`clob_id`。`ShowClobDetailResponse.clob_string`→`ApmClobDetailResponse`。
- SDK v3.1.x。

### DTO 共享与命名

- `ApmSpanEvent` = 38 字段，被 `show_trace_events`（list）与 `show_event_detail`（单对象）共享。
- SDK 原样混合命名照抄：`next_spanId`（事件）、`totalTime`（`ApmDiscardInfo`）。
- clob 引用约定：`tags` / `attachment` 中 key 以 `_clob_id` 结尾的 value 即 `clob_id`。

## Risks / Trade-offs

- **限流**：三者复用 `apm-readonly`，API 名 `apm.showTraceEvents` / `apm.showEventDetail` / `apm.showClobDetail`。
- **链路依赖真值转发**：`show_event_detail` 四元组、`show_clob_detail` 的 clob_id 必须来自前序响应，禁止编造——这是 §4.3(b) 的标准案例，工具描述须写明来源与顺序。
- **大响应**：一条大 trace 的事件序列可能很长；`diagnose_trace`（T30）在其上做过滤编排以降噪。
