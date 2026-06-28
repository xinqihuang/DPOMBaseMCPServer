## Why

智能运维 Agent 在 `list_apm_alarm_data` 命中一条告警后，常需进一步确认这条告警是否真正被投递出去：经由哪些通道（短信 / 邮件 / Webhook 等）、是否送达成功、内容快照如何。这是 APM 告警诊断链在「告警记录」之后的下钻能力，用于回答「告警触发了但用户说没收到」「审计某段时间通知失败」一类问题。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（原任务卡 `docs/tasks/T21-list-alarm-notify.md`，状态 Done；建立在 T20 的 `ApmAlarmAdapter` / `ApmAlarmService` 骨架之上），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读工具 `list_alarm_notify`，封装 APM SDK `ListAlarmNotify`，暴露 5 个 `@ToolParam`（`business_id` 头部参数 + `alarm_data_id` + `page` + `page_size` + `region`）。
- 响应对 SDK `FrontAlarmNotifyResult` 做**无损投影**（全 8 字段）。
- 在 T20 既有 `ApmAlarmAdapter` / `ApmAlarmService` 上**追加** `listAlarmNotify` 方法，不新建类；复用 `huaweicloud.apm-business-id` 配置默认值与 `apm-readonly` 限流域；上游异常映射到统一 `ErrorCode`。

## Capabilities

### New Capabilities

- `list-alarm-notify`: 按 `alarm_data_id` 查询某条 APM 告警的通知投递记录，返回通知元数据（`notify_type` / `notify_status` / `alarm_content` 快照 / 规则与模板 id / 创建时间），用于核验告警是否送达及经由哪些渠道。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-apm`（在 `ApmAlarmAdapter` / `ApmAlarmAdapterImpl` 追加 `listAlarmNotify` + 新增 3 个 DTO record）、`agentic-monitoring`（`ApmAlarmService` 追加 `listAlarmNotify`）、`agentic-mcp`（新增 `ApmAlarmNotifyTool` + `McpServerConfig` 注册）。
- 配置：复用 `huaweicloud.apm-business-id`、`apm-readonly` RateLimiter，无新增配置项。
- 不涉及写操作；不改动 T20 已交付的 `listAlarmData` 路径，亦不改动 `ApmTraceAdapter` / `ApmTraceService`。
- MCP 工具总数 +1（跟随 T20，17 → 18）。
