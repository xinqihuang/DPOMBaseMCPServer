# Spec: create_notification_mask

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 在已知变更窗口或维护期前，临时屏蔽 CES 告警通知，避免变更引发的误告警污染 on-call 通道。

典型场景:
- 变更前置: 发布 / 滚动重启前对受影响告警规则建临时屏蔽（`START_END_TIME`）
- 持续屏蔽: 对低优先级长期噪声告警建立 `FOREVER_TIME` 屏蔽，直到显式删除
- 周期性维护: 对每周固定窗口的批处理任务建 `CYCLE_TIME` 屏蔽

定位:
- 这是首个**写操作** tool（区别于此前全部只读 tool）
- 与 `delete_notification_masks` 配对：变更结束后调 delete 解除屏蔽
- 与 `list_notification_masks` 配套：审计当前活跃的屏蔽

## 2. 范围边界

**做**:
- 创建一条 CES 告警通知屏蔽规则（华为云 `BatchUpdateNotificationMasks` 接口，同一接口承担创建与更新）
- 支持 `ALARM_RULE` / `RESOURCE` / `RESOURCE_POLICY_NOTIFICATION` / `RESOURCE_POLICY_ALARM` / `EVENT.SYS` 五种关联类型
- 支持 `START_END_TIME` / `FOREVER_TIME` / `CYCLE_TIME` 三种屏蔽类型
- 单租户固定 region

**不做**:
- 不批量创建（一次仅一条）
- 不做客户端去重 / 幂等键管理
- 不查询既有屏蔽（用 `list_notification_masks`）
- 不删除（用 `delete_notification_masks`）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `create_notification_mask`
- description（Agent 看到的）:

  > Create a CES alarm notification mask rule. Use this to SHIELD alarm
  > notifications during planned changes / maintenance windows so they don't
  > fire false-positive notifications. Choose mask_type=START_END_TIME (one-off
  > window), FOREVER_TIME (until deleted), or CYCLE_TIME (recurring). Choose
  > relation_type=ALARM_RULE / RESOURCE / RESOURCE_POLICY_NOTIFICATION /
  > RESOURCE_POLICY_ALARM / EVENT.SYS. After the maintenance window, call
  > delete_notification_masks to remove the shield.

- annotations:
  - `readOnlyHint=false`
  - `destructiveHint=false`
  - `idempotentHint=false`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `mask_name` | string | 是 | — | 屏蔽规则名，正则 `^[A-Za-z0-9_\-一-龥]{1,64}$` |
| `relation_type` | enum | 是 | — | `ALARM_RULE` / `RESOURCE` / `RESOURCE_POLICY_NOTIFICATION` / `RESOURCE_POLICY_ALARM` / `EVENT.SYS` |
| `relation_ids` | array<string> | 条件必填 | — | `relation_type=ALARM_RULE` 时必填 |
| `resources` | array<object> | 条件必填 | — | `relation_type=RESOURCE` 时必填，元素 `{namespace, dimensions[]}` |
| `metric_names` | array<string> | 否 | — | 配合 `RESOURCE` 关联使用，省略则该资源下所有指标 |
| `product_metrics` | array<object> | 否 | — | 按云产品维度屏蔽，元素 `{dimension_name, metric_name}` |
| `resource_level` | enum | 否 | — | `dimension` / `product` |
| `product_name` | string | 否 | — | `resource_level=product` 时使用 |
| `mask_type` | enum | 是 | — | `START_END_TIME` / `FOREVER_TIME` / `CYCLE_TIME` |
| `start_date` | string | 条件必填 | — | `yyyy-MM-dd`；time-bounded mask_type 必填 |
| `start_time` | string | 条件必填 | — | `HH:mm:ss`；time-bounded mask_type 必填 |
| `end_date` | string | 条件必填 | — | `yyyy-MM-dd`；time-bounded mask_type 必填 |
| `end_time` | string | 条件必填 | — | `HH:mm:ss`；time-bounded mask_type 必填 |
| `effective_timezone` | string | 否 | — | 形如 `GMT+08:00` |

**输入校验规则**（service 层）:
- `mask_name` 不匹配正则 → `INVALID_PARAM`
- `relation_type` / `mask_type` / `resource_level` 不在枚举集 → `INVALID_PARAM`
- `relation_type=ALARM_RULE` 但 `relation_ids` 为空 → `INVALID_PARAM`
- `relation_type=RESOURCE` 但 `resources` 为空 → `INVALID_PARAM`
- `mask_type∈{START_END_TIME, CYCLE_TIME}` 但缺任一时间字段或格式错 → `INVALID_PARAM`

### 3.3 输出契约（成功）

```json
{
  "relation_ids": ["al17..."],
  "notification_mask_id": "nm17a..."
}
```

字段说明:
- `relation_ids`: 上游返回的实际生效关联 ID 列表，可能为空
- `notification_mask_id`: 新建的屏蔽规则 ID；后续 `delete_notification_masks` 传入此 ID

### 3.4 输出契约（失败）

```json
{
  "error_code": "INVALID_PARAM | UPSTREAM_THROTTLED | UPSTREAM_AUTH_FAILED | UPSTREAM_ERROR | TIMEOUT | INTERNAL",
  "error_message": "...",
  "upstream_trace_id": "...",
  "retryable": true | false
}
```

错误码映射:

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败（service 层拦截） | INVALID_PARAM | false |
| HTTP 429 | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.ces.v2.CesClient`（**注意：CES v2 SDK**，不是 v1）
- **SDK 方法**: `batchUpdateNotificationMasks(BatchUpdateNotificationMasksRequest)`
- **SDK 版本**: v3.1.177
- **客户端 bean**: `cesV2Client`（由 `CesV2ClientConfig` 提供，与 v1 `cesClient` 共存）

**字段映射**:

| MCP 输入 | SDK Request 字段 |
|---|---|
| `mask_name` | `body.withMaskName(String)` |
| `relation_type` | `body.setRelationType(RelationType.fromValue(...))` |
| `mask_type` | `body.withMaskType(MaskType.fromValue(...))` |
| `relation_ids` | `body.setRelationIds(List<String>)` |
| `resources[].namespace` / `dimensions` | `body.setResources(List<Resource>)`，dimensions 转 `ResourceDimension` |
| `metric_names` | `body.setMetricNames(List<String>)` |
| `product_metrics` | `body.setProductMetrics(List<ProductMetric>)` |
| `resource_level` | `body.setResourceLevel(ResourceLevelEnum.fromValue(...))` |
| `start_date` / `end_date` | `body.setStartDate(LocalDate.parse(...))` |
| `start_time` / `end_time` | `body.setStartTime(String)` |
| `effective_timezone` | `body.withEffectiveTimezone(String)` |

**AI 容易写错的点**:
1. **SDK 包路径是 `com.huaweicloud.sdk.ces.v2.*`**，不要把 v1 的 `MetricsDimension` 误用过来；本接口用 v2 自有的 `ResourceDimension` / `Resource` / `ProductMetric` / `RelationType` / `MaskType`
2. **写操作不可幂等**：上游同名重复调用会创建两条不同 `notification_mask_id` 的屏蔽规则；Agent 调用前应先 `list_notification_masks` 检查同名规则
3. `start_date` / `end_date` 是 `LocalDate`，要 `LocalDate.parse(yyyy-MM-dd)`；`start_time` / `end_time` 是 `String` 保留 `HH:mm:ss`
4. v2 SDK 的 `MaskType.fromValue(...)` 等枚举对未知值会抛 `IllegalArgumentException`，service 层先做枚举集校验拦截，避免泄漏 SDK 异常

## 5. 非功能要求

- **限流**: RateLimiter key = `ces-write`，默认 5 QPS（与只读路径 `ces-readonly` 分开计配额）
- **重试**: 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s。**写操作重试需注意非幂等性**，但 CES 的 `BatchUpdateNotificationMasks` 在网络错误情况下重试是上游推荐做法（详见 API 文档），故沿用统一策略
- **超时**: 单次 SDK 调用 10s
- **可观测**:
  - Micrometer 指标 `mcp_tool_invocation{tool="create_notification_mask", result="...", error_code="..."}`
  - 日志 INFO: 入参摘要（`maskName` / `relationType` / `maskType`）+ 耗时 + upstream trace id

## 6. 测试策略（Definition of Done）

### 单元测试（mock service / mock SDK）

本期未交付。建议补：

| ID | 用例 | 期望 |
|---|---|---|
| UT-01 | 全合法 `START_END_TIME` + ALARM_RULE 请求 | adapter 收到对应字段，SDK Request body 字段全部对齐 |
| UT-02 | mask_name 含空格 | INVALID_PARAM，不调 adapter |
| UT-03 | relation_type=ALARM_RULE 但 relation_ids 为空 | INVALID_PARAM |
| UT-04 | relation_type=RESOURCE 但 resources 为空 | INVALID_PARAM |
| UT-05 | mask_type=START_END_TIME 缺 start_date | INVALID_PARAM |
| UT-06 | start_date 格式错（如 `2026/06/02`） | INVALID_PARAM |
| UT-07 | SDK 抛 429 | 重试 3 次后 UPSTREAM_THROTTLED，retryable=true |
| UT-08 | SDK 抛 401 | UPSTREAM_AUTH_FAILED，retryable=false |

### 类型契约测试

本期未交付。建议补：

| ID | 用例 | 期望 |
|---|---|---|
| TC-01 | SDK `BatchUpdateNotificationMasksRequestBody` 反射 | 含 maskName / relationType / maskType / resources / metricNames |
| TC-02 | SDK 样例 JSON 反序列化 | 字段非 null |

### 部署后冒烟

本期未交付。

## 7. 验收标准 (DoD)

- [x] MCP Inspector 能看到 `create_notification_mask`，description 正确
- [x] 写路径走 `ces-write` RateLimiter
- [x] 日志含 maskName / relationType / maskType / 耗时 / upstream trace id
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（`7bf5907`）
- [ ] UT / TC / 冒烟脚本（后续任务补）
- [ ] Micrometer 指标 + README 示例（后续任务补）
