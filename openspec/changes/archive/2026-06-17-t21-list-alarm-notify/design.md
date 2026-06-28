## Context

存量工具回填。原始 spec：`docs/specs/tools/list_alarm_notify.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T21-list-alarm-notify.md`（依赖 T20，复用其 `ApmAlarmAdapter` / `ApmAlarmService` 骨架）。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类 / 方法 / 版本、字段映射表、错误码与 retryable、非功能要求（限流 / 重试 / 超时 / 可观测）、时间参数格式约定，以及 AI 易错点。

`list_alarm_notify` 后置于 `list_apm_alarm_data`：必须先由后者拿到告警记录的 `id` 字段，再作为本工具的 `alarm_data_id` 入参。本接口上游仅支持按 `alarm_data_id` 过滤，无时间窗。

## Goals / Non-Goals

**Goals:**
- 无损暴露 APM `ListAlarmNotify` 的通知投递记录与全部 8 个字段。
- 以最小入参（仅 `alarm_data_id` 必填）衔接 `list_apm_alarm_data` 的下钻链路。
- 复用 T20 既有 adapter / service / 限流域，零新增配置项。

**Non-Goals:**
- 重发通知 / 创建或查询通知模板（写操作或上游不暴露）。
- 时间窗过滤 / 按通道聚合 / 客户端排序。
- 改动 T20 已交付的 `listAlarmData` 路径。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.apm.v1.ApmClient`
- **SDK 方法**：`listAlarmNotify(ListAlarmNotifyRequest)`
- **SDK 版本**：v3.1.177（钉死，字段缺失先怀疑版本）
- **HTTP**：`POST /v1/apm2/openapi/alarm/data/get-alarm-notify-list`，body = `AlarmNotifyListRequest`，header = `x-business-id: Long`
- **请求体 `AlarmNotifyListRequest` 仅 4 字段**：`page` / `pageSize` / `alarmDataId` / `region`——比 T20 的 `AlarmDataListRequest` 简单得多，不要错引或混入 T20 的字段。

**响应字段映射（`FrontAlarmNotifyResult` → `ApmAlarmNotification`，8 字段全留）**：

| SDK `FrontAlarmNotifyResult` | DTO `ApmAlarmNotification` | 类型 |
|---|---|---|
| id | id | Long |
| gmt_create | gmtCreate | String |
| notify_type | notifyType | String |
| alarm_rule_id | alarmRuleId | Long |
| template_id | templateId | Long |
| alarm_data_event_id | alarmDataEventId | Long |
| notify_status | notifyStatus | Boolean |
| alarm_content | alarmContent | String |

> 顶层 `total_count`（snake_case）→ `totalCount`。

### 入参与校验

- DTO `ApmAlarmNotifyRequest` 5 字段：`businessId`(Long, 头部) / `alarmDataId`(Integer, 必填) / `page`(Integer) / `pageSize`(Integer) / `region`(String)。
- service 层校验顺序：`request` 非空 → `alarmDataId` 必填且 `> 0` → `businessId` 与配置默认值至少一个非空 → `page ≥ 1` → `pageSize ∈ [1, 100]`，任一不满足抛 `InvalidParamException` 映射 `INVALID_PARAM`，且不发起上游调用。
- adapter 内：`businessId` 为 null 时回落到 `properties.getApmBusinessId()`，注入 `x-business-id` 头。

### 时间参数格式（不一致约定，重点记录）

- 本工具响应字段 `gmt_create` 为**上游原样 String**（如 `2026-06-10T10:00:05+08:00`），不做本地解析或格式转换。
- 该约定与平台内其他工具的时间参数格式**并不统一**，使用方需自行甄别：
  - APM 告警链（本工具与 `list_apm_alarm_data`）：时间为**上游字符串**，格式未固定，原样透传。
  - CES v2 alarm history：`OffsetDateTime` / **ISO8601**。
  - 部分指标 / 日志接口：**UTC 毫秒**时间戳（epoch millis），或以 `startMillis` / `endMillis` / `durationMinutes` 三元组表达时间窗。
- 本工具本身**不接受任何时间入参**（上游仅支持 `alarm_data_id` 过滤），因此不存在入参侧的时间格式歧义；仅响应侧 `gmt_create` 为字符串。

### 错误码 → retryable

| 上游情形 | ErrorCode | retryable |
|---|---|---|
| 429 限流 | `UPSTREAM_THROTTLED` | true |
| 401 / 403 鉴权失败 | `UPSTREAM_AUTH_FAILED` | false |
| 5xx 上游错误 | `UPSTREAM_ERROR` | true |
| 传输层超时 | `TIMEOUT` | true |
| 入参非法 | `INVALID_PARAM` | false |

失败响应携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。SDK 异常不得透传到 MCP 层。

### 非功能要求

- **限流 key**：复用 `apm-readonly` RateLimiter（不新增 limiter）。
- **重试**：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做指数退避重试（重试策略 `huaweicloud-retryable`）。
- **超时**：SDK 传输层 10s。
- **可观测**：Micrometer `mcp_tool_invocation{tool="list_alarm_notify"}`；INFO 日志含 `businessId` / `alarmDataId` / 耗时 / upstream trace id。
- **调用包裹**：经 `invocation.execute("apm-readonly", "huaweicloud-retryable", "apm.listAlarmNotify", ...)` 统一织入限流 / 重试 / 观测。

### 复用而非新建

- 不单独建 `ApmClient` Bean，复用既有 `apmClient`。
- 在 T20 既有 `ApmAlarmAdapter` / `ApmAlarmService` 上**追加方法**，不新建 adapter / service 类。

## Risks / Trade-offs

- **`alarm_data_id` 类型不一致（核心风险）**：SDK 中 `alarm_data_id` 为 `Integer`，而上游 `list_apm_alarm_data` 响应里 `AlarmDataVO.id` 为 `Long`。Agent 把告警的 `id`（Long）回传为本工具的 `alarm_data_id` 时，若该值超 `Integer.MAX_VALUE` 会发生截断或 NPE。按 T19 准则 DTO 类型贴齐 SDK（`alarmDataId: Integer`），**本工具不做超出 long→int 安全转换之外的修正**，仅在 spec 与 AI 易错点记录。
- **MCP `annotations`**（`readOnlyHint` 等）在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅为语义意图。
- **遗留**：冒烟脚本、Micrometer 看板配置本期未交付（见 tasks.md）。

### AI 易错点

1. **工具名是 `list_alarm_notify`（无 `apm_` 前缀）**——用户决策，对应 SDK 方法名 `ListAlarmNotify`，不要写成 `list_apm_alarm_notify`。
2. **必须先有 `alarm_data_id`**——它来自 `list_apm_alarm_data` 响应的 `id` 字段，**禁止编造入参**；调用顺序固定为 `list_apm_alarm_data` → 取 `id` → `list_alarm_notify`。
3. `notify_status` 是 **Boolean**：`true` 送达成功，`false` 失败；字段可空（上游可能未返回）。
4. `alarm_content` 是 **String**，不要二次解析为 JSON。
5. `AlarmNotifyListRequest`（body）只有 4 字段，不要错引或混入 T20 的告警查询字段。
