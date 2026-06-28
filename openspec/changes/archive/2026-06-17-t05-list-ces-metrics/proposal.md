## Why

智能运维 Agent 在排障 / 巡检时，常需先发现「某个资源或某个 namespace 下有哪些可查询的 CES 指标」，才能后续调用 `query_metric_data` 取数。`list_ces_metrics` 是 CES 指标查询链的前置发现工具，提供按 namespace / metric_name / 单维度过滤的指标定义检索能力，避免 Agent 凭印象编造 `(namespace, metric_name, dimensions)` 三元组。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（见 tasks.md 实现记录），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读工具 `list_ces_metrics`，封装华为云 CES SDK `listMetrics`，暴露 7 个 `@ToolParam`（`namespace` / `metric_name` / `dim_name` / `dim_value` / `limit` / `start` / `order`）。
- 新增 `agentic-adapter-ces` 的 `CesMetricsAdapter.listMetrics`，SDK 响应投影为 DTO record（`CesListMetricsResponse` / `CesMetricInfo` / `CesMetricDimension` / `CesPagination`）。
- 新增 `agentic-monitoring` 的 `CesMetricsService`，承载输入校验（`dim_name`/`dim_value` 成对、`limit` 范围、`namespace` 正则、`order` 枚举）。
- 复用 `ces-readonly` 限流域与 `huaweicloud-retryable` 重试策略；上游异常映射到统一 `ErrorCode`。

## Capabilities

### New Capabilities

- `list-ces-metrics`: 按 namespace / metric_name / 单维度过滤查询 CES 已注册指标定义列表，返回指标元数据（namespace / metric_name / unit / dimensions）与 marker 游标分页，供后续 `query_metric_data` 下钻取数。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-ces`（新增 `CesMetricsAdapter` + `CesMetricsAdapterImpl` + 5 个 DTO record）、`agentic-monitoring`（`CesMetricsService`）、`agentic-mcp`（`CesMetricsTool` + `McpServerConfig` 注册）。
- 配置：复用 `ces-readonly` RateLimiter（默认 10 QPS，可配置）与 `huaweicloud-retryable` 重试组，无新增配置项。
- 不涉及写操作；不返回指标数据点（由 `query_metric_data` 负责）；不改动既有 CES adapter 基础设施。
