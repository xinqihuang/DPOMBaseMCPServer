# Spec: list_alarms

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 能够拉取华为云 CES 告警历史，用于事故定位与关联分析。

典型场景:
- Agent 收到事故信息后，列出对应时间窗口内的活跃 / 已恢复告警
- Agent 巡检某一类资源（如所有 ECS）当日是否出现告警
- 跨组件关联：先看告警面，再下钻指标 / 调用链

定位: 事故关联中的 "alarm surface" 入口，与 `query_ces_metric_data` 形成"告警 → 指标"链路。

## 2. 范围边界

**做**:
- 查询 CES 已发生的告警历史（`ListAlarmHistories`）
- 支持按资源分组 / 告警规则 / 状态 / 级别 / 命名空间 / 时间区间过滤
- 偏移分页（`start` 偏移 + `limit`）

**不做**:
- 不查询告警规则定义（`ListAlarms` 接口）
- 不做告警 ACK / 恢复（写操作）
- 不做 AOM / APM 告警事件
- 不做 marker 游标分页（CES 该接口本身用 offset）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `list_alarms`
- description（Agent 看到的）:

  > List CES (Cloud Eye Service) alarm history events for Huawei Cloud resources.
  > Filter by resource group, alarm rule id/name, status (ok / alarm /
  > insufficient_data / invalid), severity (1=critical, 2=major, 3=minor,
  > 4=info), namespace (e.g. SYS.ECS), or time range. Returns alarm metadata
  > only — no datapoints.

- annotations: `readOnlyHint=true` · `destructiveHint=false` · `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `groupId` | string | 否 | null | 资源分组 ID（`rg` 前缀，长度 24） |
| `alarmId` | string | 否 | null | 告警规则 ID（`al` 前缀，长度 24） |
| `alarmName` | string | 否 | null | 告警规则名，长度 [1, 128] |
| `alarmStatus` | string | 否 | null | `ok` / `alarm` / `insufficient_data` / `invalid` |
| `alarmLevel` | int | 否 | null | 1=紧急 / 2=重要 / 3=次要 / 4=提示 |
| `namespace` | string | 否 | null | 正则 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$` |
| `from` / `to` | string | 否 | null | 毫秒时间戳字符串（长度 [1, 13]） |
| `start` | int | 否 | 0 | 偏移，需 >= 0 |
| `limit` | int | 否 | 100 | 单页大小，[1, 100] |

**校验规则（Service 层）**: `limit` 越界 / `start<0` / `alarmStatus` 不在枚举集 / `alarmLevel∉{1,2,3,4}` / `namespace` 正则不通过 → `INVALID_PARAM`。

### 3.3 输出契约（成功）

```json
{
  "alarms": [
    {
      "alarm_id": "al1234567890abcdef012345",
      "alarm_name": "ecs-cpu-high",
      "alarm_description": "...",
      "alarm_level": 2,
      "alarm_type": "EVENT.SYS",
      "alarm_status": "alarm",
      "namespace": "SYS.ECS",
      "metric_name": "cpu_util",
      "trigger_time": 1700000000000,
      "update_time": 1700000060000
    }
  ],
  "total": 17
}
```

- `namespace` / `metric_name` 由 SDK 嵌套 `MetricInfoResp` 拍平，可能为 `null`
- `total` 由 `MetaDataForAlarmHistoryResp.total` 透传，可能为 `null`
- 不返回告警关联的 datapoints（需要时让 Agent 调 `query_ces_metric_data`）

### 3.4 输出契约（失败）

```json
{"error_code": "...", "error_message": "...", "upstream_trace_id": "...", "retryable": true}
```

| 上游情况 | error_code | retryable |
|---|---|---|
| Service 校验失败 | INVALID_PARAM | false |
| HTTP 429 | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 超时 | TIMEOUT | true |
| 未分类 | INTERNAL | false |

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.ces.v1.CesClient`
- **SDK 方法**: `listAlarmHistories(ListAlarmHistoriesRequest)`
- **SDK 版本**: v3.1.177

字段映射: `groupId/alarmId/alarmName/namespace/from/to` 走 `set<X>(String)`；`alarmStatus → AlarmStatusEnum.fromValue(String)`；`alarmLevel → AlarmLevelEnum.fromValue(Integer)`；`start/limit` 走 `withStart(String.valueOf(int))` / `withLimit(String.valueOf(int))`。

**AI 容易写错的点**:
1. SDK 的 `limit` / `start` / `from` / `to` 全是**字符串**（不是 int / long），要 `String.valueOf(...)`
2. `AlarmStatusEnum` 枚举值是**小写下划线**：`ok` / `alarm` / `insufficient_data` / `invalid`，不是 `OK` / `ALARM`
3. `AlarmLevelEnum.fromValue(Integer)` 接 `Integer`，传 String 会失败
4. 响应里告警关联的 namespace / metricName 嵌套在 `MetricInfoResp` 下（字段名 `metric`），不在顶层
5. `MetaDataForAlarmHistoryResp` 没有 marker，仅 `total`——该接口是偏移分页

## 5. 非功能要求

- **限流**: 复用 `ces-readonly` RateLimiter（与其他 CES 只读 tool 共享 10 QPS）
- **重试**: 仅 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s
- **超时**: 10s
- **可观测**: `mcp_tool_invocation{tool="list_alarms"}` + adapter INFO 日志（含 namespace / alarmId / status / level / limit）

## 6. 测试策略（Definition of Done）

### 单元测试 / 类型契约测试 / 部署冒烟

**本期未交付**。建议后续任务补：

- Service 层 UT（`CesAlarmServiceTest`）：覆盖 limit 越界、status 大小写错、level 越界、namespace 正则不通过、默认值透传
- Adapter 层 UT（`CesMetricsAdapterImplTest` 新增）：全字段对齐、嵌套 `metric` 拍平、SDK 429 重试
- Contract Test：`ListAlarmHistoriesRequest` / `AlarmHistoryInfoResp` / `MetaDataForAlarmHistoryResp` 字段反射 + 样例 JSON 反序列化
- 冒烟脚本 `scripts/smoke/smoke-list_alarms.sh`：(1) 正常拉取 (2) 大写 status → 400 (3) limit=101 → 400

## 7. 验收标准（DoD）

- [x] MCP Inspector 能看到 `list_alarms`，description 正确
- [x] 复用 `ces-readonly` RateLimiter（adapter `RATE_LIMITER_NAME="ces-readonly"`）
- [x] 日志含入参摘要（`ces.listAlarmHistories start` INFO）
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（提交 `4c346d6`）
- [ ] Tool / Service / Adapter / Contract Test（后续任务补）
- [ ] Micrometer 指标在 actuator/prometheus 看到
- [ ] 贵阳冒烟脚本通过
- [ ] README 含 tool 使用示例
