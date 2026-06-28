## Why

智能运维 Agent 在收到应用级告警（如 appName=order-svc CPU 飙高）或做容量分析时，需要拉取华为云 AOM（应用运维管理）单条时序在指定时间窗口、采样粒度下的数据点序列（含 maximum / minimum / sum / average / sampleCount 等多统计组合）。这是 AOM 监控诊断链中"发现指标（list_aom_metrics）→ 取值分析"的取值环节，与 `query_ces_metric_data`（基础设施层时序）形成应用层 vs 基础设施层的对称能力。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（提交 `4c346d6`，含 Tool / Service / Adapter / DTO 全栈），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读工具 `query_aom_metric_data`，封装 AOM v2 SDK `ListSample`，按 `(namespace, metricName, dimensions)` 单条时序拉数据点序列。
- AOM adapter（`AomMetricsAdapter`）新增 `queryMetricData` 能力，新增映射方法 `toListSampleSdkRequest` / `toQueryMetricDataResponseDto` / `toSampleSeries` / `toDatapoint`。
- 新增自定义 DTO record：`AomQueryMetricDataRequest` / `AomQueryMetricDataResponse` / `AomSampleSeries` / `AomMetricDatapoint` / `AomStatisticValue`（datapoint 的 statistics 为 list-of-pair，非平铺 max/min）。
- 新增业务编排 `AomMetricDataService`（namespace 正则 / period 集合 / time_range 正则 / statistics 集合 / fill_value 集合 / dimensions 长度校验）。
- 复用 T06 引入的 `HUAWEICLOUD_PROJECT_ID` 与 `aom-readonly` 限流域；上游异常映射到统一 `ErrorCode`。

## Capabilities

### New Capabilities

- `query-aom-metric-data`: 按 `(namespace, metric_name, dimensions)` 拉取单条 AOM 时序在指定 `time_range` / `period` 下的数据点序列，支持 maximum / minimum / sum / average / sampleCount 多统计组合与 fill_value 断点插值；前置依赖 `list_aom_metrics` 发现合法的 namespace + metric_name + dimensions。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-aom`（`AomMetricsAdapter` / `AomMetricsAdapterImpl` 新增 `queryMetricData` + 映射方法、5 个 DTO record）、`agentic-monitoring`（新增 `AomMetricDataService`）、`agentic-mcp`（新增 `AomMetricDataTool` + `McpServerConfig` 注册）。
- 配置：复用 `HUAWEICLOUD_PROJECT_ID`（T06 已引入）与 `aom-readonly` RateLimiter（QPS=10，与 `list_aom_metrics` 共享配额），无新增配置项。
- 不涉及写操作；不做多条时序批量查询、客户端聚合/重采样、跨 projectId/region。
