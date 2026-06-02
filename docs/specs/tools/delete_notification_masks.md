# Spec: delete_notification_masks

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 在维护窗口结束后，批量解除 CES 告警通知屏蔽规则，恢复正常通知流。

典型场景:
- 变更后置: 变更完成后调用本 tool 删除之前 `create_notification_mask` 建立的临时屏蔽
- 清理审计: 巡检发现冗余 / 过期屏蔽规则后批量清理
- 应急回滚: 误建的屏蔽规则需要立即移除

定位:
- 这是**首个 `destructiveHint=true` 的破坏性 tool**：删除不可逆，仅可通过 `create_notification_mask` 重建
- 与 `create_notification_mask` 配对
- 通常先调 `list_notification_masks` 找到目标 ID

## 2. 范围边界

**做**:
- 批量删除一组 CES 告警通知屏蔽规则（华为云 `BatchDeleteNotificationMasks` 接口）
- 一次最多 100 条

**不做**:
- 不按条件删除（必须先 list 拿 ID）
- 不软删除（上游为硬删除）
- 不级联清理告警规则本身（只删屏蔽规则）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `delete_notification_masks`
- description（Agent 看到的）:

  > Batch-delete CES alarm notification mask rules by id. Use this to LIFT
  > alarm shielding after a maintenance window completes, restoring normal
  > notification flow. Pass up to 100 mask ids at a time. Returns the ids that
  > were actually deleted (mismatches with the input typically mean the id no
  > longer exists).

- annotations:
  - `readOnlyHint=false`
  - `destructiveHint=true`
  - `idempotentHint=true`

> 幂等说明：对同一组 ID 重复调用上游会返回已删除的子集；客户端视角下「删除已不存在的 ID」无副作用，因此标记 `idempotentHint=true`。

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `notification_mask_ids` | array<string> | 是 | — | 屏蔽规则 ID 列表，长度 [1, 100]，每项非空白字符串 |

**输入校验规则**（service 层）:
- `notification_mask_ids` 为 null / 长度 0 / 长度 > 100 → `INVALID_PARAM`
- 任一 ID 为 null 或空白 → `INVALID_PARAM`

### 3.3 输出契约（成功）

```json
{
  "notification_mask_ids": ["nm17a...", "nm17b..."]
}
```

字段说明:
- `notification_mask_ids`: 上游实际删除成功的 ID 列表，可能为空（如全部 ID 已不存在）但不会为 `null`
- **重要**：若返回数组长度 < 入参数组长度，说明部分 ID 不存在或已被其他人删除，并非错误

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

- **SDK 类**: `com.huaweicloud.sdk.ces.v2.CesClient`（**CES v2 SDK**）
- **SDK 方法**: `batchDeleteNotificationMasks(BatchDeleteNotificationMasksRequest)`
- **SDK 版本**: v3.1.177
- **客户端 bean**: `cesV2Client`（由 `CesV2ClientConfig` 提供）

**字段映射**:

| MCP 输入 | SDK Request 字段 |
|---|---|
| `notification_mask_ids` | `body.withNotificationMaskIds(List<String>)` |

**AI 容易写错的点**:
1. **破坏性操作必须有 service 层显式长度上下限校验**：不能依赖上游限制；本地拦截可避免一次错误调用就被上游计入错误率
2. 操作幂等：上游对「ID 不存在」不抛错，只是从返回列表里剔除；Agent 应比对入参 / 返回长度做差集，不要把「少了几条」当成错误
3. v2 SDK 的请求体类名是 `BatchDeleteNotificationMasksRequestBody`，不要和 v1 的 `MetricsDimension` 等混淆——本工具与 metrics 相关接口完全不在同一包
4. 重试策略：超时 / 5xx 触发重试是安全的（幂等），但**注意上游 trace 日志可能出现多次相同请求**，故障排查时排查上游 trace 而非本地日志

## 5. 非功能要求

- **限流**: RateLimiter key = `ces-write`，默认 5 QPS（与 `create_notification_mask` 共享配额）
- **重试**: 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s；幂等性保证重试安全
- **超时**: 单次 SDK 调用 10s
- **可观测**:
  - Micrometer 指标 `mcp_tool_invocation{tool="delete_notification_masks", result="...", error_code="..."}`
  - 日志 INFO: `ids.size` + 耗时 + upstream trace id
  - **建议日志额外打印实际删除的 ID 数**（差集 = 入参 - 实际删除），便于审计

## 6. 测试策略（Definition of Done）

### 单元测试（mock service / mock SDK）

本期未交付。建议补：

| ID | 用例 | 期望 |
|---|---|---|
| UT-01 | 合法 1 条 ID | adapter 收到该 ID，SDK Request body 已设置 |
| UT-02 | 100 条 ID（上限） | 通过校验，正常调 adapter |
| UT-03 | 101 条 ID | INVALID_PARAM，不调 adapter |
| UT-04 | 0 条 ID（空列表） | INVALID_PARAM |
| UT-05 | 列表含空字符串 | INVALID_PARAM |
| UT-06 | SDK 返回 deleted < input | 返回 DTO 含实际删除子集，不报错 |
| UT-07 | SDK 返回 null `notificationMaskIds` | adapter 兜底转为空 List（不 NPE） |
| UT-08 | SDK 抛 429 | 重试后 UPSTREAM_THROTTLED |

### 类型契约测试

本期未交付。建议补：

| ID | 用例 | 期望 |
|---|---|---|
| TC-01 | SDK `BatchDeleteNotificationMasksRequestBody` 反射 | 含 notificationMaskIds 字段 |
| TC-02 | SDK `BatchDeleteNotificationMasksResponse` 反射 | 含 notificationMaskIds 字段 |

### 部署后冒烟

本期未交付。

## 7. 验收标准 (DoD)

- [x] MCP Inspector 能看到 `delete_notification_masks`，description 正确，`destructiveHint=true` 在 schema 中可见
- [x] 写路径走 `ces-write` RateLimiter
- [x] 日志含 `ids.size` / 耗时 / upstream trace id
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（`7bf5907`）
- [ ] UT / TC / 冒烟脚本（后续任务补）
- [ ] Micrometer 指标 + README 示例（后续任务补）
