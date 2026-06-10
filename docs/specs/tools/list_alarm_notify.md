# Spec: list_alarm_notify

> 状态: Draft · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 在 `list_apm_alarm_data` 命中一条告警后，查询这条告警的通知投递记录
（哪些通道、是否成功送达、内容快照）。

典型场景:
- Agent 看到告警但用户说没收到，需要 trace 通知投递状态
- Agent 调研某告警的所有外发渠道（电话/短信/邮件/Webhook）
- Agent 审计某段时间内通知失败的情况

定位: APM 告警上下文工具，**后置**于 `list_apm_alarm_data`——必须先拿到 `alarm_data_id`
（即 `list_apm_alarm_data` 返回的 `id` 字段）。

## 2. 范围边界

**做**:
- 调用 APM `ListAlarmNotify`，按 `alarm_data_id` 拉取通知记录
- 响应**无损覆盖** `FrontAlarmNotifyResult` 全部 8 字段
- 复用 `huaweicloud.apm-business-id` 默认值
- 上游异常映射

**不做**:
- 不重发通知 / 不创建通知模板
- 不做时间窗过滤（上游本接口仅支持 `alarm_data_id` 过滤）
- 不返回通知模板内容（上游不暴露）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `list_alarm_notify`
- description（Agent 看到的）:

  > List notification delivery records for a specific APM alarm. Call this AFTER
  > list_apm_alarm_data returns an alarm's id — pass that id as alarm_data_id here.
  > Returns notification metadata (notify_type such as sms/email/webhook,
  > notify_status success/failure, alarm_content snapshot, alarm_rule_id,
  > template_id, alarm_data_event_id, gmt_create). Use this to verify whether an
  > alarm has actually been delivered, and through which channels.

- annotations: `readOnlyHint=true` / `destructiveHint=false` / `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `business_id` | long | 否 | null（回落到 `huaweicloud.apm-business-id`） | APM 业务 id；用于 `x-business-id` 头 |
| `alarm_data_id` | int | 是 | — | 告警记录 id（来自 `list_apm_alarm_data` 响应的 `id` 字段） |
| `page` | int | 否 | 1 | 页码 |
| `page_size` | int | 否 | 50 | 单页大小 [1, 100] |
| `region` | string | 否 | null | 资源 region |

**输入校验**（service 层）:
- `alarm_data_id` 必填且 > 0 → 否则 `INVALID_PARAM`
- `page` ≥ 1；`page_size` ∈ [1, 100]
- `business_id` 与默认都为 null → `INVALID_PARAM`

**类型注意**：SDK 中 `alarm_data_id` 是 `Integer`，而 `list_apm_alarm_data` 响应里
`AlarmDataVO.id` 是 `Long`。理论上 alarm id 可能溢出 Integer 范围；按 T19 准则
DTO 类型贴齐 SDK（`alarm_data_id: Integer`），Agent 端传值时需自行确认未超
Int 上限。这是上游 SDK 的不一致，本工具不修正，记录在 spec 与 AI 易错点。

### 3.3 输出契约（成功）

```json
{
  "notifications": [
    {
      "id": 100001,
      "alarm_data_event_id": 67890,
      "alarm_rule_id": 22222,
      "template_id": 444,
      "notify_type": "SMS",
      "notify_status": true,
      "alarm_content": "[ALARM] order-svc latency > 500ms",
      "gmt_create": "2026-06-10T10:00:05+08:00"
    }
  ],
  "total_count": 4
}
```

字段说明：
- `notifications[]` 全 8 字段是 `FrontAlarmNotifyResult` 的无损投影
- `notify_status: true` 表示送达成功，`false` 表示失败

### 3.4 输出契约（失败）

标准 `ErrorResponse`，错误码同 `list_apm_alarm_data`。

## 4. 与华为云 SDK 的映射

- **SDK 类**：`com.huaweicloud.sdk.apm.v1.ApmClient`
- **SDK 方法**：`listAlarmNotify(ListAlarmNotifyRequest)`
- **SDK 版本**：v3.1.177
- **HTTP**：`POST /v1/apm2/openapi/alarm/data/get-alarm-notify-list`，body = `AlarmNotifyListRequest`，header = `x-business-id: Long`

**响应字段映射（8 字段全留）**：

| SDK `FrontAlarmNotifyResult` | DTO `ApmAlarmNotification` |
|---|---|
| id (Long) | id |
| gmt_create (String) | gmtCreate |
| notify_type (String) | notifyType |
| alarm_rule_id (Long) | alarmRuleId |
| template_id (Long) | templateId |
| alarm_data_event_id (Long) | alarmDataEventId |
| notify_status (Boolean) | notifyStatus |
| alarm_content (String) | alarmContent |

## 5. 非功能要求

- **限流**：复用 `apm-readonly` RateLimiter
- 重试 / 超时 / 可观测同 `list_apm_alarm_data`

## 6. 测试策略（DoD）

| ID | 类 | 用例 |
|---|---|---|
| UT-T1 | Tool | success passthrough — 5 入参装配 + 返回值透传 |
| UT-T2 | Tool | service `InvalidParamException` → `INVALID_PARAM` |
| UT-T3 | Tool | service `UpstreamException` → ErrorResponse 含 trace id |
| UT-S1 | Service | `alarm_data_id == null` → INVALID_PARAM |
| UT-S2 | Service | `alarm_data_id <= 0` → INVALID_PARAM |
| UT-S3 | Service | `page_size = 0` → INVALID_PARAM |
| UT-S4 | Service | businessId 与默认都为空 → INVALID_PARAM |
| UT-S5 | Service | 全合法 → 委托 adapter |
| UT-A1 | Adapter | SDK 调用映射 + header 注入 |
| UT-A2 | Adapter | 429 / 401 / 5xx / Timeout（4 case） |
| TC-01 | Contract | 样本 JSON 反序列化 + adapter 映射 + 8 字段全断言 |

## 7. 验收标准

- [ ] UT/TC 全部通过
- [ ] MCP Inspector 能看到 `list_alarm_notify`
- [ ] 日志含 businessId / alarmDataId / 耗时 / trace id
- [ ] Checkstyle 0
- [ ] DTO 无损（8 字段全留）
