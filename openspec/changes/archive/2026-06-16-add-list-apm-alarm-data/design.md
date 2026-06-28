## Context

存量工具回填。原始 spec：`docs/specs/tools/list_apm_alarm_data.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T20-list-apm-alarm-data.md`。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 字段映射、非功能要求、时间格式约定。

## Goals / Non-Goals

**Goals:**
- 无损暴露 APM `ListAlarmData` 的检索能力与全部告警元数据。
- 与 APM 诊断链其余工具（`list_alarm_notify` 等）形成可衔接的入口。

**Non-Goals:**
- 告警的创建 / 修改 / 删除（写操作）。
- 通知动作详情（由 `list_alarm_notify` 负责）。
- 客户端聚合 / 排序 / SQL / 维度展开。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.apm.v1.ApmClient`
- **SDK 方法**：`listAlarmData(ListAlarmDataRequest)`
- **SDK 版本**：v3.1.x（钉死，字段缺失先怀疑版本）
- **HTTP**：`POST /v1/apm2/openapi/alarm/data/get-alarm-data-list`，body=`AlarmDataListRequest`，header=`x-business-id: Long`

**响应字段映射（`AlarmDataVO` → `ApmAlarm`，27 字段全留）**：

| SDK `AlarmDataVO` | DTO `ApmAlarm` | 类型 |
|---|---|---|
| id | id | Long |
| gmt_create / gmt_modify | gmtCreate / gmtModify | String |
| region_alarm_event_id | regionAlarmEventId | Long |
| business_id / business_name | businessId / businessName | Long / String |
| app_name | appName | String |
| version_number | versionNumber | Integer |
| alarm_rule_type / process_unit | alarmRuleType / processUnit | String |
| region | region | String |
| instance_id / instance_name / ip_address | instanceId / instanceName / ipAddress | Long / String / String |
| env_id | envId | Long |
| template_id | templateId | Long |
| alarm_rule_id / alarm_rule_name / alarm_rule_expression | alarmRuleId / alarmRuleName / alarmRuleExpression | Long / String / String |
| monitor_item_id | monitorItemId | Long |
| collector_id / collector_name | collectorId / collectorName | Integer / String |
| alarm_first_time / alarm_last_time | alarmFirstTime / alarmLastTime | String |
| alarm_level / restrain_key / status | alarmLevel / restrainKey / status | String |

> 顶层 `total_count`（snake_case）→ `totalCount`。

### 易错点

1. 两个 `business_id`：header `x-business-id`(Long, 租户) vs body `business_id`(Long, 过滤) → DTO 用 `businessId` / `businessIdFilter` 区分。
2. 时间字段是 `String` 不是 `OffsetDateTime`（不同于 CES v2 alarm history）。
3. `env_list` 是 `List<Long>`；`collector_id` / `version_number` 是 `Integer`，其余 id 是 `Long`。
4. `AlarmDataListRequest` 是"列表查询的请求体"，非"List 类型的请求"。
5. 不单独建 `ApmClient` Bean，复用既有 `apmClient`。

## Risks / Trade-offs

- **17 个 `@ToolParam`**：参数规模较大，依赖 description 清晰区分两个 `business_id`；Spring AI 1.0.4 可承载（`query_lts_logs` 同量级）。
- **非功能**：复用 `apm-readonly` RateLimiter（10 QPS）；仅对 `UPSTREAM_THROTTLED`/`UPSTREAM_ERROR`/`TIMEOUT` 做 3 次指数退避重试；SDK 传输层超时 10s；Micrometer `mcp_tool_invocation{tool="list_apm_alarm_data"}`，INFO 日志含 businessId / 过滤参数摘要 / 耗时 / upstream trace id。
- **遗留**：MCP `annotations`（readOnlyHint 等）在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅为语义意图。
