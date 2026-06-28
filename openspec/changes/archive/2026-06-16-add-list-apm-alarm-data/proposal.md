## Why

智能运维 Agent 收到上游事故工单后，需要先确认华为云 APM 在该时段是否已触发应用层告警，并按应用 / 关键字 / 级别 / 状态等条件检索告警记录。这是 APM 告警诊断链的入口能力，与 CES `list_alarms`（基础设施层告警）互补。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（见 tasks.md 的实现 hash），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读工具 `list_apm_alarm_data`，封装 APM SDK `ListAlarmData`，暴露 14 个上游过滤参数 + 2 个分页参数 + 1 个 `business_id` 头部参数（共 17 个 `@ToolParam`）。
- 响应对 SDK `AlarmDataVO` 做**无损投影**（全 27 字段）。
- 复用 `huaweicloud.apm-business-id` 配置默认值与 `apm-readonly` 限流域；上游异常映射到统一 `ErrorCode`。

## Capabilities

### New Capabilities

- `list-apm-alarm-data`: 按时间窗 / 应用 / 关键字 / 级别 / 状态 / 实例 / 环境等条件查询 APM 告警记录，返回告警全量元数据，供后续 `list-alarm-notify` 下钻。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-apm`（新增 `ApmAlarmAdapter` + 3 个 DTO record）、`agentic-monitoring`（`ApmAlarmService`）、`agentic-mcp`（`ApmAlarmDataTool` + `McpServerConfig` 注册）。
- 配置：复用 `huaweicloud.apm-business-id`、`apm-readonly` RateLimiter（10 QPS），无新增配置项。
- 不涉及写操作；不改动既有 `ApmTraceAdapter` / `ApmTraceService`。
