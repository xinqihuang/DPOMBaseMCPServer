## Why

智能运维 Agent 在排查应用/容器/节点级问题时，需要先发现"某个监控对象下哪些指标可查"，再决定拉取哪条时序数据、关联哪些告警。AOM（Application Operations Management，应用运维管理）覆盖应用运维层指标（应用、组件、容器、进程等），与 CES `list_ces_metrics`（基础设施层 ECS/RDS/EVS 等云资源指标）对称但不互替，运维 Agent 通常两者结合使用。本工具是 `query_aom_metric_data`（指标值查询，后续任务）的前置发现步骤。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（见 tasks.md），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。源 spec：`docs/specs/tools/list_aom_metrics_v0.2.md`（v0.2，Approved）；源任务卡：`docs/tasks/T06-list-aom-metrics.md`。

## What Changes

- 新增 MCP 只读工具 `list_aom_metrics`，封装 AOM v2 SDK `ListMetricItems`（`POST /v2/{project_id}/ams/metrics`），按命名空间 / 指标名 / 维度 / 资源 ID（inventoryId）任意组合过滤，支持 `start` offset + `limit` 分页，返回结构化 metric 列表（namespace / metric_name / unit / dimensions / dimension_value_hash）+ 分页元信息。
- 新增 `agentic-adapter-aom` 适配层：`AomMetricsAdapter` 接口 + `AomMetricsAdapterImpl`、`AomClientConfig`、业务 DTO record（`AomListMetricsRequest` / `AomListMetricsResponse` / `AomMetricInfo` / `AomMetricDimension` / `AomPagination`），所有 SDK 类型不穿透 adapter 边界。
- 新增 `agentic-monitoring` 业务校验层 `AomMetricsService`（§3.2 七条校验规则）；新增 `agentic-mcp` 的 `AomMetricsTool` 注册并接入 `McpServerConfig`。
- 配套基础设施变更：`HuaweiCloudProperties` 增加 `projectId` 字段（环境变量 `HUAWEICLOUD_PROJECT_ID`）；`HuaweiCloudCredentialsHealthIndicator` 增加 projectId 缺失检查；`application.yml` 增加 `resilience4j.ratelimiter.instances.aom-readonly`（QPS=10，与 ces-readonly 对称）。

## Capabilities

### New Capabilities

- `list-aom-metrics`: 按命名空间 / 指标名 / 维度 / inventoryId 任意组合发现 AOM 应用运维层指标定义，返回指标元数据与分页信息，供后续 `query_aom_metric_data` 下钻取值。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-aom`（新增 adapter + config + 5 个 DTO record，填充此前的占位模块）、`agentic-monitoring`（新增 `AomMetricsService`，pom 加 aom adapter 依赖）、`agentic-mcp`（新增 `AomMetricsTool`，`McpServerConfig` 注册）、`agentic-common`（`HuaweiCloudProperties` + `HuaweiCloudCredentialsHealthIndicator` 修改）。
- 配置：新增 `huaweicloud.project-id`（`${HUAWEICLOUD_PROJECT_ID:}`，保留默认空字符串避免启动期解析失败）、`resilience4j.ratelimiter.instances.aom-readonly`（10 QPS）；复用 `huaweicloud-retryable` 重试域、10s HTTP 超时。
- 不涉及写操作；不查指标值 / 日志 / 告警；不做缓存、跨 projectId、跨 region 查询；不改动既有 CES adapter。
