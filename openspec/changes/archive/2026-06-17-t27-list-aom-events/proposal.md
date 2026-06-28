## Why

存量回填，工具已于早期 commit 交付。诊断 Agent 此前只能查 CES 告警历史（`list_alarms`）与 APM 告警（`list_apm_alarm_data`），缺 AOM 侧的事件/告警入口。AOM v2 `ListEvents` 一个接口同时覆盖活动告警、历史告警与全部事件（query 参数 `type` 区分），是告警下钻链（events → metadata → metrics/logs）的起点。

## What Changes

- 新增 MCP 只读工具 `list_aom_events`，封装 AOM SDK v2 `ListEvents`（`POST /v2/{project_id}/events`）。
- 受控枚举 `type`（`active_alert` / `history_alert`，不传=全部）；`time_range`、`step`、`search`、排序、`metadata_relation`、分页等过滤。
- 响应对 `ListEventModel` 无损投影（11 字段）+ marker 制 `PageInfo`（3 字段）。

## Capabilities

### New Capabilities

- `list-aom-events`: 按时间窗 / 类型 / 关键字 / 排序 / metadata 关系查询 AOM 事件与告警，返回事件全量元数据与 marker 分页，供后续 `list_aom_metrics` → `query_aom_metric_data` 或 `query_logs` 下钻。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-aom`（`AomEventAdapter` + 请求/响应 DTO + `AomAlertType` 枚举 + `AomPatterns.TIME_RANGE` 上提）、`agentic-monitoring`（`AomEventService`）、`agentic-mcp`（`AomEventTool`）。
- 配置：复用 `aom-readonly` RateLimiter，无新增配置项；不缓存（实时告警）。
- 显式不透传 `Enterprise-Project-Id`（单租户/单项目作用域，避免诱导编造）。
