## Context

存量工具回填。原始 spec：`docs/specs/tools/list_alarms.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T09-list-ces-alarms.md`（Done, 提交 `4c346d6`）。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类 / 方法 / 版本、字段映射、错误码映射、非功能要求、时间参数格式约定、AI 易错点。

## Goals / Non-Goals

**Goals:**
- 在 CES adapter 上无损暴露 `ListAlarmHistories` 的检索能力与常用告警元数据。
- 与 CES 指标查询链（`query_ces_metric_data`）形成"告警面 → 指标"的可衔接入口。
- SDK 类型不泄漏到 MCP 层；上游异常统一映射到 `ErrorCode`。

**Non-Goals:**
- 告警规则定义查询（`ListAlarms` 接口）。
- 告警 ACK / 静默 / 恢复等写操作。
- AOM / APM 告警事件。
- marker 游标分页（CES 该接口本身用 offset，SDK 暴露的也是 offset 风格）。
- 客户端聚合 / 排序 / datapoints 拉取。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.ces.v1.CesClient`
- **SDK 方法**：`listAlarmHistories(ListAlarmHistoriesRequest)`
- **SDK 版本**：v3.1.177（钉死，字段缺失先怀疑版本）

**入参字段映射（`CesListAlarmsRequest` → `ListAlarmHistoriesRequest`）**：

| DTO 字段 | 类型 | SDK 装配 |
|---|---|---|
| groupId | String | `setGroupId(String)` |
| alarmId | String | `setAlarmId(String)` |
| alarmName | String | `setName(String)` / `set<X>(String)` |
| namespace | String | `setNamespace(String)` |
| from / to | String | `setFrom(String)` / `setTo(String)`（毫秒时间戳字符串，长度 [1,13]） |
| alarmStatus | String | `setAlarmStatus(AlarmStatusEnum.fromValue(String))` |
| alarmLevel | Integer | `setAlarmLevel(AlarmLevelEnum.fromValue(Integer))` |
| start | int | `withStart(String.valueOf(int))` |
| limit | int | `withLimit(String.valueOf(int))` |

**响应字段映射（SDK `AlarmHistoryInfoResp` → DTO `CesAlarmHistory`）**：`alarm_id` / `alarm_name` / `alarm_description` / `alarm_level`(Integer) / `alarm_type` / `alarm_status` 顶层透传；嵌套 `metric`(`MetricInfoResp`) 拍平为 `namespace` + `metric_name`（**可能为 null**，部分告警类型无关联指标，adapter 必须三元判空）；`trigger_time` / `update_time` 为毫秒时间戳。顶层 `total` 由 `MetaDataForAlarmHistoryResp.total`（**仅 total，无 marker**）透传，可能为 null。

### 时间参数格式

- CES `list_alarms` 的 `from` / `to` 为**毫秒时间戳字符串**（长度 [1, 13]），原样透传，不做本地解析或格式转换。
- 该格式与其它工具不一致：APM `list_apm_alarm_data` 时间为上游未固定格式 String；勿照搬 ISO8601 / UTC 毫秒 / startMillis.endMillis.durationMinutes 等其它工具约定。

### Service 层校验

- `limit ∈ [1, 100]`（CES 上限 100），越界 → `INVALID_PARAM`。
- `start >= 0`（偏移分页），`start < 0` → `INVALID_PARAM`。
- `alarmStatus ∈ {ok, alarm, insufficient_data, invalid}`（**小写下划线**，与 SDK 枚举一致）。
- `alarmLevel ∈ {1, 2, 3, 4}`（1=紧急 / 2=重要 / 3=次要 / 4=提示）。
- `namespace` 匹配 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`。
- 校验放 Service 层抛 `InvalidParamException`，不在 DTO 紧凑构造抛 `IllegalArgumentException` 绕过 `ErrorCode` 映射；DTO 紧凑构造仅做默认值规范化（`limit=100`、`start=0`）。

### 错误码 → retryable 映射

| 上游情况 | error_code | retryable |
|---|---|---|
| Service 校验失败 | INVALID_PARAM | false |
| HTTP 429 | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 超时 | TIMEOUT | true |
| 未分类 | INTERNAL | false |

失败响应统一形如 `{error_code, error_message, upstream_trace_id, retryable}`；Tool 层 `catch (SmartomException e)` 返回结构化 `ErrorResponse`，不让原始异常栈透出 MCP 客户端。

### 非功能

- **限流**：复用 `ces-readonly` RateLimiter（adapter `RATE_LIMITER_NAME="ces-readonly"`，与其它 CES 只读 tool 共享 10 QPS）。
- **重试**：仅 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s。
- **超时**：10s。
- **可观测**：`mcp_tool_invocation{tool="list_alarms"}` + adapter INFO 日志（含 namespace / alarmId / status / level / limit；`ces.listAlarmHistories start`）。

## Risks / Trade-offs

- **AI 易错点（务必遵从）**：
  1. SDK 的 `limit` / `start` / `from` / `to` 全是**字符串**，要 `String.valueOf(...)`，不是 int / long。
  2. `AlarmStatusEnum` 枚举是**小写下划线**（`ok` / `alarm` / `insufficient_data` / `invalid`），不是 `OK` / `ALARM`。
  3. `AlarmLevelEnum.fromValue(Integer)` 接 `Integer`，传 String 会失败。
  4. 告警关联的 namespace / metricName 嵌套在 `MetricInfoResp`（字段名 `metric`）下，不在顶层。
  5. `MetaDataForAlarmHistoryResp` 没有 marker，仅 `total`——该接口是偏移分页，不要按 `list_ces_metrics` 的 marker 习惯做。
  6. **Tool 名是 `list_alarms` 而非 `list_ces_alarms`**——`@Tool(name=...)` 是真实契约名，写错会导致 Agent 拼装 prompt 失败。
  7. 响应里 `metric` 字段可能为 null（部分告警类型无关联指标），adapter 必须三元判空。
- **遗留**：MCP `annotations`（`readOnlyHint` / `destructiveHint` / `idempotentHint`）在当前 Spring AI `@Tool` 未实际透出，仅为语义意图。
