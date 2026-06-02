# Spec: list_notification_masks

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 分页查询 CES 当前已配置的告警通知屏蔽规则，用于审计、清理或在删除前定位目标 ID。

典型场景:
- 删除前置: 通过 `mask_name` / `mask_status` 等过滤定位目标 ID，再调 `delete_notification_masks`
- 审计巡检: 列出当前 `MASK_EFFECTIVE`（生效中）的全部屏蔽，确认是否存在长期未清理的噪声屏蔽
- 资源关联排查: 给定 `resource_id` / `namespace` / `dimensions`，确认其是否在被屏蔽中

定位:
- 这是 `create_notification_mask` / `delete_notification_masks` 的**只读发现 tool**，与三件套配套
- 与 `list_ces_metrics` 在职责上正交（一个查指标定义，一个查屏蔽规则）

## 2. 范围边界

**做**:
- 分页查询屏蔽规则列表（华为云 `ListNotificationMasks`）
- 按 `relation_type` / `mask_name` / `mask_status` / `resource_id` / `namespace` / `dimensions` 等多条件过滤
- 支持 `create_time` / `update_time` 排序，`ASC` / `DESC` 方向

**不做**:
- 不返回屏蔽规则的告警生效历史
- 不做跨 region / 跨 projectId 查询
- 不做客户端缓存

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `list_notification_masks`
- description（Agent 看到的）:

  > Query CES alarm notification mask rules with paging and optional filters.
  > Use this to find existing mask ids before calling delete_notification_masks,
  > or to audit currently active shields. Filter by relation_type / mask_name /
  > mask_status (MASK_EFFECTIVE — active now, or MASK_INEFFECTIVE — created but
  > outside its time window), namespace, resource_id, or specific dimensions.
  > Returns mask metadata (id, name, type, time window, etc.) plus a total
  > 'count'.

- annotations:
  - `readOnlyHint=true`
  - `destructiveHint=false`
  - `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `offset` | int | 否 | 0 | 分页偏移 [0, 10000] |
| `limit` | int | 否 | 100 | 分页大小 [1, 100] |
| `sort_key` | enum | 否 | — | `create_time` / `update_time` |
| `sort_dir` | enum | 否 | — | `ASC` / `DESC` |
| `relation_type` | enum | 否 | — | `ALARM_RULE` / `RESOURCE` / `RESOURCE_POLICY_NOTIFICATION` / `RESOURCE_POLICY_ALARM` / `DEFAULT`（注意：与 create 的枚举集不同，list 多了 `DEFAULT`，少了 `EVENT.SYS`） |
| `relation_ids` | array<string> | 否 | — | 关联编号过滤 |
| `metric_name` | string | 否 | — | 指标名过滤 |
| `resource_level` | enum | 否 | — | `dimension` / `product` |
| `mask_id` | string | 否 | — | 屏蔽规则 ID 精确过滤 |
| `mask_name` | string | 否 | — | 屏蔽规则名过滤 |
| `mask_status` | enum | 否 | — | `MASK_EFFECTIVE` / `MASK_INEFFECTIVE` |
| `resource_id` | string | 否 | — | 资源维度值（例如 instance_id） |
| `namespace` | string | 否 | — | 命名空间，如 `SYS.ECS` |
| `dimensions` | array<{name,value}> | 否 | — | 资源维度列表 |

**输入校验规则**（service 层）:
- `offset` 不在 [0, 10000] → `INVALID_PARAM`
- `limit` 不在 [1, 100] → `INVALID_PARAM`
- `sort_key` / `sort_dir` / `relation_type` / `resource_level` / `mask_status` 给值但不在枚举集 → `INVALID_PARAM`
- 全部过滤字段都不给 → 允许（受 limit 限制）

### 3.3 输出契约（成功）

```json
{
  "notification_masks": [
    {
      "notification_mask_id": "nm17a...",
      "mask_name": "release-window-2026-06",
      "relation_type": "ALARM_RULE",
      "relation_id": "al17...",
      "resource_level": "dimension",
      "product_name": null,
      "mask_status": "MASK_EFFECTIVE",
      "mask_type": "START_END_TIME",
      "metric_names": ["cpu_util"],
      "product_metrics": null,
      "start_date": "2026-06-02",
      "start_time": "10:00:00",
      "end_date": "2026-06-02",
      "end_time": "12:00:00",
      "effective_timezone": "GMT+08:00"
    }
  ],
  "count": 1
}
```

字段说明:
- `notification_masks`: 屏蔽规则列表，可能为空但不会为 `null`
- `count`: 上游返回的总条数（不是 `notification_masks.length`），用于客户端推断是否还有下一页
- 各枚举类字段（`relation_type` / `mask_type` 等）已透传上游 `.getValue()` 字符串，方便与 create 工具的入参对照

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
| 输入校验失败 | INVALID_PARAM | false |
| HTTP 429 | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.ces.v2.CesClient`（**CES v2 SDK**，与 v1 client 独立）
- **SDK 方法**: `listNotificationMasks(ListNotificationMasksRequest)`
- **SDK 版本**: v3.1.177
- **客户端 bean**: `cesV2Client`

**字段映射**:

| MCP 输入 | SDK Request 字段 |
|---|---|
| `offset` | `withOffset(Integer)` |
| `limit` | `withLimit(Integer)` |
| `sort_key` | `setSortKey(ListNotificationMasksRequest.SortKeyEnum.fromValue(...))` |
| `sort_dir` | `setSortDir(ListNotificationMasksRequest.SortDirEnum.fromValue(...))` |
| `relation_type` | `body.setRelationType(ListRelationType.fromValue(...))`（**注意**：list 用 `ListRelationType`，create 用 `RelationType`，**是不同的 SDK 枚举类**） |
| `relation_ids` / `metric_name` / `mask_id` / `mask_name` / `resource_id` / `namespace` | `body.setXxx(...)` |
| `resource_level` | `body.setResourceLevel(ListNotificationMaskRequestBody.ResourceLevelEnum.fromValue(...))` |
| `mask_status` | `body.setMaskStatus(ListNotificationMaskRequestBody.MaskStatusEnum.fromValue(...))` |
| `dimensions` | `body.setDimensions(List<ResourceDimension>)` |

**AI 容易写错的点**:
1. **SDK 的 v2 包路径**：`com.huaweicloud.sdk.ces.v2.model.ListNotificationMasksRequest` / `ListNotificationMaskRequestBody`（注意单数 `Mask`）；分页参数挂在 Request 上，过滤参数挂在 body 上
2. **`ListRelationType` vs `RelationType` 是不同的枚举类**：list 接口比 create 接口多了 `DEFAULT`，少了 `EVENT.SYS`；不能用同一个 service 常量集
3. **body 为可选**：当所有过滤字段都未提供时，不要设置一个空 body（SDK 默认）；adapter 内用 `bodyHasValue` 标志位决定是否调 `setBody`
4. 响应字段中的枚举对象需 `.getValue()` 转 String 再放进 DTO，否则 JSON 序列化会输出枚举对象结构

## 5. 非功能要求

- **限流**: RateLimiter key = `ces-readonly`（与 `list_ces_metrics` 等只读 tool 共享配额），默认 10 QPS
- **重试**: 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s
- **超时**: 单次 SDK 调用 10s
- **可观测**:
  - Micrometer 指标 `mcp_tool_invocation{tool="list_notification_masks", result="...", error_code="..."}`
  - 日志 INFO: `offset` / `limit` / `maskName` / `maskStatus` / 耗时 / upstream trace id

## 6. 测试策略（Definition of Done）

### 单元测试

本期未交付。建议补：

| ID | 用例 | 期望 |
|---|---|---|
| UT-01 | 仅 offset/limit 默认值 | adapter 收到 offset=0, limit=100，body 不设置 |
| UT-02 | 给 mask_name 过滤 | body 设置 maskName，bodyHasValue=true |
| UT-03 | offset=-1 | INVALID_PARAM |
| UT-04 | limit=101 | INVALID_PARAM |
| UT-05 | sort_key=`bad` | INVALID_PARAM |
| UT-06 | relation_type=`EVENT.SYS`（list 不允许） | INVALID_PARAM |
| UT-07 | SDK 返回 null masks | adapter 返回 DTO 含空列表 |
| UT-08 | SDK 返回多条 | 枚举字段转 String，count 透传 |

### 类型契约测试

本期未交付。建议补：

| ID | 用例 | 期望 |
|---|---|---|
| TC-01 | SDK `ListNotificationMasksRequest` 反射 | 含 offset / limit / sortKey / sortDir / body |
| TC-02 | SDK `ListNotificationMaskRespNotificationMasks` 反射 | 含 notificationMaskId / maskName / maskType 等字段 |

### 部署后冒烟

本期未交付。

## 7. 验收标准 (DoD)

- [x] MCP Inspector 能看到 `list_notification_masks`，description 正确
- [x] 只读路径走 `ces-readonly` RateLimiter
- [x] 日志含 offset / limit / maskName / maskStatus / 耗时 / upstream trace id
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（`7bf5907`）
- [ ] UT / TC / 冒烟脚本（后续任务补）
- [ ] Micrometer 指标 + README 示例（后续任务补）
