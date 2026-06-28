## Why

存量回填，工具已于早期 commit 交付。APM trace 根因诊断需要从"哪个组件慢/错"一路下钻到"组件内哪一步、根因文本是什么"。本变更补齐诊断链的第 3、4、5 步，与已有的 `query_traces`（第 1 步）/ `get_service_topology`（第 2 步）衔接成完整链路。

## What Changes

- 新增 `show_trace_events`（v1 `ShowTraceEvents`，按 trace_id 返回全部调用链事件）。
- 新增 `show_event_detail`（v1 `ShowEventDetail`，按四元组返回单事件完整 tags）。
- 新增 `show_clob_detail`（v1 `ShowClobDetail`，按 clob_id 取超长堆栈/SQL 全文）。
- `show_trace_events` 与 `show_event_detail` 共享同一 38 字段 `ApmSpanEvent` record。三者均实时、不缓存。

## Capabilities

### New Capabilities

- `show-trace-events`: 按 trace_id 获取全部调用链事件序列（诊断链第 3 步）。
- `show-event-detail`: 按四元组获取单事件完整详情与根因 tags（第 4 步）。
- `show-clob-detail`: 按 clob_id 取回超长字段全文（第 5 步，终点）。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-apm`（3 个 adapter 方法 + `ApmSpanEvent`/`ApmDiscardInfo`/`ApmClobDetailResponse` 等 DTO）、`agentic-monitoring`（`ApmTraceService` 追加方法）、`agentic-mcp`（3 个 tool）。
- 配置：复用 `apm-readonly` RateLimiter、`huaweicloud.apm-business-id` 默认值；不缓存。
- 衔接：本链路是 `diagnose_trace`（T30）编排工具的底层能力。
