## Why

智能运维 Agent 在收到告警或执行容量分析、动作验证时，需要拉取**单条 CES 指标**在指定时间区间、聚合粒度下的真实数据点序列（max / min / average / sum / variance），以便判断告警对象在告警时刻附近的指标曲线、做单实例容量评估，或对比扩容/重启动作前后的曲线变化。`list_ces_metrics` 负责发现合法的 `(namespace, metric_name, dimensions)` 三元组，本工具负责在此基础上取出实际监控数值，是 CES 诊断链中"发现 → 取数"的取数环节，与批量版 `batch_query_ces_metric_data` 互补。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（实现 `4c346d6`，filter/period 枚举化重构 `ce6fd6c`，详见 tasks.md），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- CES adapter（`agentic-adapter-ces`）新增 `queryMetricData` 能力，封装华为云 CES SDK `ShowMetricData` 调用。
- 新增自定义 DTO record：`CesQueryMetricDataRequest` / `CesQueryMetricDataResponse` / `CesDatapoint`（datapoint 平铺 max/min/average/sum/variance，对齐上游结构）。
- 新增业务编排 `CesMetricDataService`（namespace 正则 / dimensions 长度 1-4 / from<to / filter·period·metric_name 必填校验）。
- 新增 MCP 只读工具 `query_ces_metric_data`，Tool 层把字符串 `filter` / 整数 `period` 入参解析为 `CesMetricFilter` / `CesMetricPeriod` 枚举（枚举化由 T14 `ce6fd6c` 按 ADR-004 锁死），并将上游异常映射到统一 `ErrorCode`。
- `McpServerConfig` 注册 `CesMetricDataTool`，复用 `ces-readonly` 限流域。

## Capabilities

### New Capabilities

- `query-ces-metric-data`: 查询单条 CES 指标在指定时间窗（毫秒级 UNIX 时间戳 from/to）、聚合粒度（period 秒）、聚合方式（filter）下的数据点序列，支持 1-4 个维度过滤，返回 `{ metric_name, datapoints[] }`。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-ces`（`CesMetricsAdapter` + `CesMetricsAdapterImpl` 新增 `queryMetricData` / `toShowMetricDataSdkRequest` / `toQueryMetricDataResponseDto` / `toDatapoint`，3 个 DTO record）、`agentic-monitoring`（`CesMetricDataService`）、`agentic-mcp`（`CesMetricDataTool` + `McpServerConfig` 注册）。
- 配置：复用 `ces-readonly` RateLimiter（与 `list_ces_metrics` / `batch_query_ces_metric_data` 共享配额），无新增配置项。
- 不涉及写操作；不改动既有 CES 其他 tool / adapter。
