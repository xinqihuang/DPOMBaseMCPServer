## Why

智能运维 Agent 在 `query_lts_logs` 命中一条目标日志后，需要还原该日志前后的真实时序与因果链：看 ERROR 发生前的初始化流程、发生后的后续告警，或在 SQL 分析模式下回到原始日志流补全被截断的文本。`query_lts_log_context` 是 `query_lts_logs` 的后置工具，以"目标这一条为中心向前后扩展"，补齐 LTS 日志诊断链的上下文还原能力。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（见 tasks.md 的实现记录），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读工具 `query_lts_log_context`，封装 LTS SDK `listLogContext`，暴露 7 个 `@ToolParam`：`log_group_id` / `log_stream_id` / `line_num` / `cursor_time` / `backwards_size` / `forwards_size` / `scroll_id`。
- service 层执行入参校验：必填项非空、首次模式与 scroll 翻页模式互斥、`line_num`/`cursor_time` 成对、`backwards_size`/`forwards_size` 范围 [0, 500] 且不同时为 0。
- 复用 T16 adapter（`LtsLogAdapter.listLogContext`）与 DTO（`LtsListLogContextRequest` / `LtsListLogContextResponse`），上游异常映射到统一 `ErrorCode`，trace id 透传。
- 复用 `lts-readonly` 限流域，与 `query_lts_logs` 共享配额；无新增配置项。

## Capabilities

### New Capabilities

- `query-lts-log-context`: 给定目标日志的 `line_num` + `cursor_time`（或 scroll 续翻页 `scroll_id`），拉取其前 N + 后 N 条日志，还原事故发生时段日志流的时序上下文。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-monitoring`（新增 `LtsLogContextService`，做 7 条校验后委托 adapter）、`agentic-mcp`（新增 `LtsLogContextTool` + `McpServerConfig` 注册）。
- 复用：T16 `LtsLogAdapter` 与 `LtsListLogContextRequest` / `LtsListLogContextResponse`，不改动其 DTO。
- 配置：复用 `lts-readonly` RateLimiter（10 QPS），无新增配置项。
- 不涉及写操作；不新增 RateLimiter、健康检查、跨流上下文或自动 pre-fetch。
