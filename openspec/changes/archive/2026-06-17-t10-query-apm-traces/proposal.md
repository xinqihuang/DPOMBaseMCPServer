## Why

智能运维 Agent 在排查延迟 / 错误问题时，需要从指标尖刺或告警下钻到 APM 调用链 span 层，按 traceId / 入口 url / 时间窗 / 错误标志 / 最小耗时等条件检索华为云 APM span。这是 APM 调用链诊断的入口能力，与 `get_service_topology`（节点 / 边视图）互补。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（提交 `4c346d6`，2026-06-02 回填文档），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读工具 `query_traces`，封装 APM SDK `ShowSpanSearch`，按多维条件搜索 APM `ClientSpanInfo` 列表。
- 暴露 8 个入参：`businessId`（`x-business-id` 头部，可走配置默认值）/ `startTimeString` / `endTimeString` / `traceId` / `source` / `hasError` / `timeUsedMin` / 分页 `page` / `pageSize`。
- 响应对 SDK `ClientSpanInfo` 做投影，返回 `{ total, spans[] }`；`tags` 始终非 null（`Map.of()` 兜底）。
- 复用 `HuaweiCloudProperties.apmBusinessId` / `apmRegion` 配置；新增独立 `apm-readonly` 限流域（10 QPS，与 `ces-readonly` 隔离）；上游异常映射到统一 `ErrorCode`。

## Capabilities

### New Capabilities

- `query-traces`: 按 traceId / source / 时间窗 / 错误标志 / 最小耗时等条件搜索 APM 调用链 span，返回分页 span 列表与上游 `total`，供延迟 / 错误问题排查及向 `get_service_topology` 下钻。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-apm`（新增 `ApmTraceAdapter` + `ApmTraceAdapterImpl` + 3 个 DTO record + `ApmClientConfig`）、`agentic-monitoring`（`ApmTraceService`）、`agentic-mcp`（`ApmTraceTool` + `McpServerConfig` 注册）。
- 配置：`HuaweiCloudProperties` 新增 `apmBusinessId` / `apmRegion`；`application.yml` 新增 `apm-readonly` RateLimiter（10 QPS）。
- 不涉及写操作；为 APM adapter 子模块首个 SDK 能力，后续 APM 工具（如 `list_apm_alarm_data`）在此基座上扩展。
