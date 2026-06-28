## Why

存量回填，已于早期 commit 交付（原任务卡 `docs/tasks/T22-show-apm-trend.md`，状态 Done）。智能运维 Agent 在 APM 告警发生时需要回看监控项指标曲线，判断是瞬时抖动还是趋势恶化，并支持「先看告警（`list_apm_alarm_data`）→ 再看告警绑定指标趋势（`show_apm_trend`）」的诊断下钻链路；也支持脱离告警入口、按 `monitor_item_id` × `view_config` × 时间窗自由查询某指标曲线。此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读工具 `show_apm_trend`，封装 APM SDK `ShowTrend`，暴露 5 个外层平铺参数（`business_id` / `instance_id` / `monitor_item_id` / `env_id` / `start_time` / `end_time`）加 1 个嵌套对象参数 `view_config`（对齐 SDK `TrendView` 12 字段，含 `field_item_list` 元素 7 字段）。
- 响应对 SDK `ShowTrendResponse` 做**无损投影**：`line_list`（`FrontLine` 6 字段，含 `point_list` 之 `FrontPoint` 2 字段）+ `latest_data_Time`。`FrontPoint.value` 保留 `Object` 类型。
- 复用 `huaweicloud.apm-business-id` 配置默认值与 `apm-readonly` 限流域；上游异常映射到统一 `ErrorCode`。

## Capabilities

### New Capabilities

- `show-apm-trend`: 按 `monitor_item_id` × `view_config` × 时间窗拉取 APM 监控项趋势数据（折线 / 汇总表 / 明细表），返回一组带 `{time, value}` 点列的曲线及 `latest_data_time`，供告警下钻或自由指标查询。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-apm`（新增 `ApmTrendAdapter` + `ApmTrendAdapterImpl` + 6 个 DTO record）、`agentic-monitoring`（新增 `ApmTrendService`）、`agentic-mcp`（新增 `ApmTrendTool` + `McpServerConfig` 注册）。
- 配置：复用 `huaweicloud.apm-business-id`、`apm-readonly` RateLimiter、`huaweicloud-retryable` 重试策略，无新增配置项。
- 不涉及写操作；不复用 / 不改动既有 `ApmAlarmAdapter` / `ApmAlarmService`（Trend 与 Alarm 属不同领域，独立模块）。MCP 工具总数 18 → 19。
