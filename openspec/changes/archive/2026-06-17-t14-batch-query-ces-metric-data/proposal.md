## Why

存量回填，已于早期 commit 交付（`ce6fd6c`，2026-06-02 完成）——`batch_query_ces_metric_data` 工具及 CES 参数枚举目录已合入 master，此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

智能运维 Agent 在大盘渲染、跨资源关联分析、巡检任务等场景需要一次拉取多条 CES 指标的数据点；若对单条工具 `query_ces_metric_data` 循环调用会造成 N 次 HTTP 与 N 倍上游限流压力。本能力提供批量入口，一次最多合并 500 条 (namespace, metric_name, dimensions) 查询，共享同一 {filter, period, from, to}，按请求顺序对齐返回。

## What Changes

- 新增 MCP 只读工具 `batch_query_ces_metric_data`，封装 CES SDK `BatchListMetricData`，一次查询 1–500 条指标的数据点。
- `filter` / `period` 由散落的 `Set<String>` 校验改为类型安全的严格枚举（`CesMetricFilter` / `CesMetricPeriod`）；`namespace` / `metric_name` / `dimensions.name` 配套宽容目录枚举（`CesNamespace` / `CesMetric` / `CesDimensionKey`）作参考，DTO 仍以 String 承载以允许新 SDK 值透传（详见 ADR-004）。
- 响应对 SDK `BatchMetricData` 做无损投影，`metrics[]` 顺序与请求一致，`unit` 在父级、单 datapoint 不带 unit。
- 复用 `ces-readonly` 限流域；上游异常映射到统一 `ErrorCode`。

## Capabilities

### New Capabilities

- `batch-query-ces-metric-data`: 在一次调用中批量查询 1–500 条 CES 指标在指定时间区间、聚合粒度下的数据点，共享 filter/period/from/to，按请求顺序对齐返回；调用前应先用 `list_ces_metrics` 发现可用 metric_name 与 dimensions。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-ces`（`CesMetricsAdapter` 新增 `batchQueryMetricData` + DTO record + 5 个枚举）、`agentic-monitoring`（`CesBatchMetricDataService`，并从 `CesMetricDataService` 删除 `ALLOWED_FILTERS/PERIODS`）、`agentic-mcp`（`CesBatchMetricDataTool` + `McpServerConfig` 注册）。
- 配置：复用 `ces-readonly` RateLimiter（与 `list_ces_metrics` / `query_ces_metric_data` 共享配额），无新增配置项。
- 不涉及写操作；`query_ces_metric_data` 的 DTO `filter`/`period` 字段类型随枚举改造调整（向后兼容，Agent 仍传字符串）。
