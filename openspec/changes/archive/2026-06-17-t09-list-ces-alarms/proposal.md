## Why

智能运维 Agent 在事故定位与关联分析时，需要先确认华为云 CES（Cloud Eye Service）在对应时间窗内是否已触发基础设施层告警，并按资源分组 / 告警规则 / 状态 / 级别 / 命名空间 / 时间区间检索告警历史。这是事故关联中的 "alarm surface" 入口，与 `query_ces_metric_data` 形成"告警 → 指标"的下钻链路，与 APM 应用层告警 `list_apm_alarm_data` 互补。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（提交 `4c346d6`，2026-06-02），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 在 CES adapter 上新增 `listAlarms` 能力，封装 CES SDK `listAlarmHistories(ListAlarmHistoriesRequest)`，覆盖 adapter / service / tool 三层。
- 新增 MCP 只读工具 `list_alarms`（`@Tool(name = "list_alarms")`），暴露资源分组 / 告警规则 / 状态 / 级别 / 命名空间 / 时间区间 / 偏移分页等过滤参数。
- 新增 DTO record：`CesListAlarmsRequest` / `CesListAlarmsResponse` / `CesAlarmHistory`；嵌套 `MetricInfoResp` 在 adapter 层拍平为 `namespace` + `metricName`，不泄漏 SDK 嵌套结构。
- 新增 `CesAlarmService` 做偏移分页与 status / level / namespace 枚举与正则校验；上游异常统一映射到 `ErrorCode`。
- 复用既有 `ces-readonly` 限流配额；`McpServerConfig` 注册 `CesAlarmTool` 到 `ToolCallbackProvider`。

## Capabilities

### New Capabilities

- `list-alarms`: 查询 CES 已发生的告警历史，按资源分组 / 告警规则 / 状态 / 级别 / 命名空间 / 时间区间过滤，偏移分页返回告警元数据（不含 datapoints），供后续 `query_ces_metric_data` 下钻。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-ces`（`CesMetricsAdapter` 新增 `listAlarms` 方法 + `CesMetricsAdapterImpl` 实现 + 3 个 DTO record）、`agentic-monitoring`（`CesAlarmService`）、`agentic-mcp`（`CesAlarmTool` + `McpServerConfig` 注册）。
- 配置：复用 `ces-readonly` RateLimiter（10 QPS 只读域），无新增配置项。
- 不涉及写操作；不查询告警规则定义（`ListAlarms` 接口）；不做告警 ACK / 静默 / 恢复；不改动既有 CES 指标查询能力。
