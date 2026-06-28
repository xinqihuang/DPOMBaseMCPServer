## Why

智能运维 Agent 在事故定位 / 巡检过程中，需要按 `(category, 时间窗, key_word)` 检索华为云 AOM (Application Operations Management) 的应用 / 主机 / 自定义日志，快速捞取错误堆栈与关键字命中行。这是 AOM 诊断链中"只看日志"窄场景的查询入口，与 `query_aom_metric_data`（指标层）、`correlate_incident`（关联分析）互补。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（提交 `4c346d6`，2026-05-28 落地，见 tasks.md），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读工具 `query_logs`，封装 AOM SDK `listLogItems`（`type=querylogs`），暴露 `category` / `startTime` / `endTime` / `keyWord` / `pageSize` / `isDesc` 共 6 个参数。
- 响应对上游 `result` JSON 字符串做**透传**（不在 adapter 层解析），随附 `errorCode` / `errorMessage`。
- 复用既有 `AomMetricsAdapterImpl`、`aom-readonly` 限流域与 `huaweicloud-retryable` 重试策略；上游异常映射到统一 `ErrorCode`。

## Capabilities

### New Capabilities

- `query-logs`: 按 `(category, 时间窗, key_word)` 检索 AOM 应用 / 主机 / 自定义日志，透传上游 `result` JSON 字符串供下游自行解析。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-aom`（`AomMetricsAdapter` 新增 `queryLogs` 方法 + `AomMetricsAdapterImpl` 实现 + 2 个 DTO record：`AomQueryLogsRequest` / `AomQueryLogsResponse`）、`agentic-monitoring`（新增 `AomLogService`）、`agentic-mcp`（新增 `AomLogTool` + `McpServerConfig` 注册）。
- 配置：复用 `aom-readonly` RateLimiter 与 `huaweicloud-retryable` 重试策略，无新增配置项。
- 不涉及写操作；不新建 `AomLogAdapterImpl`（复用 `AomMetricsAdapterImpl` 以共享 `AomClient` 与限流域）；不改动既有 `listMetrics` / `queryMetricData`。
