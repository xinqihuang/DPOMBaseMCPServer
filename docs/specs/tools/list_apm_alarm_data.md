# Spec: list_apm_alarm_data

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 按时间窗 / 应用 / 关键字 / 级别 / 状态查询华为云 APM 的告警记录。

典型场景:
- Agent 收到上游事故工单，需要先看看 APM 在该时段是否已经触发了告警
- Agent 巡检某 `app_name` 最近 24 小时的所有告警（按级别、状态过滤）
- Agent 拿到一条告警的 `id`（即响应中的 `id` / `region_alarm_event_id`）后，再调 `list_alarm_notify` 看通知动作详情

定位: APM 告警入口 tool；与 CES `list_alarms` 互补——CES 是基础设施级告警，本工具是应用层（APM）告警。

## 2. 范围边界

**做**:
- 调用 APM `ListAlarmData`，按 14 个上游过滤参数搜索告警记录
- 复用现有 `huaweicloud.apm-business-id` 配置项；调用方亦可覆盖
- 响应**无损覆盖** `AlarmDataVO` 全部 27 个字段（按 T19 §4.1 准则）
- 上游异常映射到 `ErrorCode`，trace id 透传

**不做**:
- 不告警创建 / 修改 / 删除（写操作）
- 不返回告警通知动作（由 `list_alarm_notify` 负责）
- 不做客户端聚合 / 排序
- 不做 SQL 查询 / 维度展开

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `list_apm_alarm_data`
- description（Agent 看到的）:

  > List APM (Application Performance Management) alarm records for Huawei Cloud
  > applications. Filter by time window, app_name, status, severity (alarm_level),
  > keyword, instance IP, env id, monitor item, collector, etc. Returns full alarm
  > metadata: rule identity, severity, instance / IP / region, lifecycle timestamps
  > (alarm_first_time / alarm_last_time / gmt_create / gmt_modify), and the alarm
  > expression. Use the returned id with list_alarm_notify to fetch
  > notification delivery records for a specific alarm.

- annotations: `readOnlyHint=true` / `destructiveHint=false` / `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `business_id` | long | 否 | null（回落到 `huaweicloud.apm-business-id`） | APM 业务（应用）id；用于 `x-business-id` 头 |
| `page` | int | 否 | 1 | 页码（1 起始） |
| `page_size` | int | 否 | 50 | 单页大小，[1, 100]（service 层校验） |
| `region` | string | 否 | null | 资源 region 过滤 |
| `app_name` | string | 否 | null | 应用名过滤 |
| `business_id_filter` | long | 否 | null | 业务 id **过滤条件**（body 中的 `business_id`，与上面 header 形态的 `business_id` 区分） |
| `monitor_item_id` | long | 否 | null | 监控项 id |
| `status` | string | 否 | null | 告警状态过滤（具体取值由上游决定，service 层做非空透传，不强制集合） |
| `alarm_level` | string | 否 | null | 告警级别字符串（上游为 String） |
| `keyword` | string | 否 | null | 关键字模糊匹配 |
| `alarm_start_time` | string | 否 | null | 告警起始时间字符串（上游为 String，未明确格式；按上游样例为 ISO 或毫秒，pass-through） |
| `alarm_end_time` | string | 否 | null | 告警截止时间字符串，含义同上 |
| `collector_id` | int | 否 | null | 采集器 id |
| `ip_address` | string | 否 | null | 实例 IP 过滤 |
| `env_list` | list<long> | 否 | null | 环境 id 列表（AND 关系由上游决定） |

**输入校验**（service 层）:
- `page` ≥ 1；`page_size` ∈ [1, 100]
- 若 `business_id`（header） 与 `huaweicloud.apm-business-id` 都为 null → `INVALID_PARAM`（避免 SDK 因缺 header 报错）
- `app_name` / `keyword` / `status` / `alarm_level` 不预校验取值（上游枚举尚不稳定）

### 3.3 输出契约（成功）

```json
{
  "alarms": [
    {
      "id": 12345,
      "region_alarm_event_id": 67890,
      "business_id": 7000,
      "business_name": "order-service",
      "app_name": "order-svc",
      "process_unit": "order-pod-7d8b",
      "instance_id": 9001,
      "instance_name": "order-pod-7d8b-xyz",
      "ip_address": "10.0.0.42",
      "region": "cn-north-4",
      "env_id": 1001,
      "collector_id": 5,
      "collector_name": "default-collector",
      "alarm_rule_id": 22222,
      "alarm_rule_name": "high-latency",
      "alarm_rule_expression": "avg(latency) > 500",
      "alarm_rule_type": "STATIC",
      "monitor_item_id": 333,
      "template_id": 444,
      "version_number": 3,
      "alarm_level": "MAJOR",
      "status": "ALARM",
      "restrain_key": "rk-abc",
      "alarm_first_time": "2026-06-10T10:00:00+08:00",
      "alarm_last_time": "2026-06-10T10:30:00+08:00",
      "gmt_create": "2026-06-10T10:00:00+08:00",
      "gmt_modify": "2026-06-10T10:30:00+08:00"
    }
  ],
  "total_count": 137
}
```

字段说明：
- `alarms[]` 全 27 字段是 `AlarmDataVO` 的**无损投影**（per T19 §4.1）；任一字段缺失会让契约测试变红
- `total_count` 来自上游 `total_count`，供分页使用

### 3.4 输出契约（失败）

标准 `ErrorResponse`：`error_code` ∈ `INVALID_PARAM / UPSTREAM_THROTTLED / UPSTREAM_AUTH_FAILED / UPSTREAM_ERROR / TIMEOUT / INTERNAL`。

## 4. 与华为云 SDK 的映射

- **SDK 类**：`com.huaweicloud.sdk.apm.v1.ApmClient`
- **SDK 方法**：`listAlarmData(ListAlarmDataRequest)`
- **SDK 版本**：v3.1.177
- **HTTP**：`POST /v1/apm2/openapi/alarm/data/get-alarm-data-list`，body = `AlarmDataListRequest`，header = `x-business-id: Long`

**响应字段映射（27 字段全留，T19 §4.1 准则）**：

| SDK `AlarmDataVO` | DTO `ApmAlarm` |
|---|---|
| id (Long) | id |
| gmt_create / gmt_modify (String) | gmtCreate / gmtModify |
| region_alarm_event_id (Long) | regionAlarmEventId |
| business_id / business_name | businessId / businessName |
| app_name | appName |
| version_number (Integer) | versionNumber |
| alarm_rule_type / process_unit | alarmRuleType / processUnit |
| region | region |
| instance_id / instance_name / ip_address | instanceId / instanceName / ipAddress |
| env_id | envId |
| template_id | templateId |
| alarm_rule_id / alarm_rule_name / alarm_rule_expression | alarmRuleId / alarmRuleName / alarmRuleExpression |
| monitor_item_id | monitorItemId |
| collector_id (Integer) / collector_name | collectorId / collectorName |
| alarm_first_time / alarm_last_time (String) | alarmFirstTime / alarmLastTime |
| alarm_level / restrain_key / status | alarmLevel / restrainKey / status |

## 5. 非功能要求

- **限流**：复用现有 `apm-readonly` RateLimiter（10 QPS）
- **重试**：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避
- **超时**：SDK 传输层 10s
- **可观测**：Micrometer `mcp_tool_invocation{tool="list_apm_alarm_data"}`；INFO 日志含 businessId / 14 个过滤参数摘要 / 耗时 / upstream trace id

## 6. 测试策略（DoD）

| ID | 类 | 用例 |
|---|---|---|
| UT-T1 | Tool | success passthrough — 14 入参全部正确装配到 `ApmAlarmsRequest` |
| UT-T2 | Tool | service `InvalidParamException` → `INVALID_PARAM` ErrorResponse |
| UT-T3 | Tool | service `UpstreamException(429)` → `UPSTREAM_THROTTLED` + trace id |
| UT-S1 | Service | `request == null` → INVALID_PARAM |
| UT-S2 | Service | `page < 1` → INVALID_PARAM |
| UT-S3 | Service | `page_size > 100` → INVALID_PARAM |
| UT-S4 | Service | businessId / 配置 default 都为空 → INVALID_PARAM |
| UT-S5 | Service | 全合法 → 委托 adapter |
| UT-A1 | Adapter | SDK 调用映射：14 body 字段对齐 + x-business-id header 注入 |
| UT-A2 | Adapter | 429 / 401 / 5xx / Timeout 异常映射（4 case） |
| TC-01 | Contract | 样本 JSON 反序列化经 adapter 映射后断言 `ApmAlarm` 全 27 字段（删一字段编译失败） |

## 7. 验收标准

- [ ] UT/TC 全部通过
- [ ] MCP Inspector 能看到 `list_apm_alarm_data`
- [ ] 日志含 businessId / 过滤参数 / 耗时 / trace id
- [ ] Checkstyle 0
- [ ] DTO 无损（27 字段全留），契约测试通过
- [ ] 不破坏现有 ApmTraceAdapter / ApmTraceService 路径
