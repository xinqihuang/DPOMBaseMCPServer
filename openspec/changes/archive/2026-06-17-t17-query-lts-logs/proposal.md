## Why

智能运维 Agent 在事故排查时，常需在收到 CES / APM 告警后，按时间窗到对应 LTS 日志流里检索 `ERROR` / `Exception` / 自定义关键字的原始日志，或对结构化日志跑 SQL 分析。`query_lts_logs` 是 LTS 第一条业务工具，是日志诊断链的入口能力。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（见 tasks.md 的实现说明），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读工具 `query_lts_logs`，封装 LTS SDK `listLogs`，在给定 `log_group_id` + `log_stream_id` 下按时间区间 / 关键字 / 标签 / SQL 检索日志，暴露 17 个 `@ToolParam`（2 个必填 id + 15 个可选检索 / 分页 / 模式参数）。
- 支持 `line_num` + `cursor_time` 游标分页与 `scroll_id` 分页，支持 `is_analysis_query=true` 的 SQL 分析模式（结果走 `analysis_logs`）。
- service 层做 5 类输入校验；上游异常映射到统一 `ErrorCode`，`upstream_trace_id` 透传。复用 `lts-readonly` 限流域与 T16 adapter。

## Capabilities

### New Capabilities

- `query-lts-logs`: 在已知 `log_group_id` + `log_stream_id` 下按时间窗 / 关键字 / 标签 / SQL 检索 LTS 原始日志，返回带 `line_num` 游标的日志条目，供 `query_lts_log_context`（T18）下钻上下文。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-monitoring`（新增 `LtsLogService`，做校验 + 委托）、`agentic-mcp`（新增 `LtsLogTool` + `McpServerConfig` 注册）。复用 T16 的 `LtsLogAdapter` 与 `LtsListLogsRequest` / `LtsListLogsResponse`。
- 配置：复用 `lts-readonly` RateLimiter（10 QPS），无新增配置项。
- 不涉及写操作；不改动既有 adapter / service。
