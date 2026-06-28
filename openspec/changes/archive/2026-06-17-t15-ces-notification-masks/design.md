## Context

存量工具回填。原始 spec：`docs/specs/tools/create_notification_mask.md` / `docs/specs/tools/delete_notification_masks.md` / `docs/specs/tools/list_notification_masks.md`（均 v1.0, Approved）；原始任务卡：`docs/tasks/T15-ces-notification-masks.md`（状态 Done，commit `7bf5907`）。本文承载 OpenSpec 主 spec 放不下的重契约：CES v2 SDK 类 / 方法 / 版本、字段映射表、不一致的时间参数格式、错误码 → retryable 映射、限流 / 重试 / 超时 / 可观测，以及 AI 易错点。

三件套是 CES 告警通知屏蔽生命周期的完整闭环：`create`（建屏蔽）→ `list`（审计 / 定位 ID）→ `delete`（解除屏蔽）。三者共享 `cesV2Client` 与 `CesNotificationMaskService`，故合并设计。

## Goals / Non-Goals

**Goals:**
- 完整暴露 CES 告警通知屏蔽的创建 / 删除 / 查询能力，形成可衔接的发现 → 操作链。
- 在 service 层集中参数校验，避免无效调用计入上游错误率；将 SDK 异常统一映射到 `ErrorCode`，不泄漏到 MCP 层。
- 引入 CES v2 SDK 并与 v1 client 安全共存。

**Non-Goals:**
- 不批量创建（`create` 一次仅一条）；不做客户端去重 / 同名屏蔽预检 / 幂等键管理。
- 不按条件删除（`delete` 必须先 `list` 拿 ID）；不软删除；不级联清理告警规则本身。
- 不返回屏蔽生效历史；不做跨 region / 跨 projectId 查询；不做客户端缓存 / 聚合 / 排序。
- AOM / APM 对应屏蔽能力、Micrometer 看板、UT/TC/冒烟脚本本期不交付（见 tasks.md 遗留项）。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.ces.v2.CesClient`（**CES v2 SDK**，与 v1 `cesClient` 同名不同包）
- **SDK 版本**：v3.1.177（钉死；字段 / 枚举缺失先怀疑版本）
- **客户端 bean**：`cesV2Client`，由 `CesV2ClientConfig` 提供，**构造方法注入**（项目禁用字段注入）
- **SDK 方法**：
  - `create` → `batchUpdateNotificationMasks(BatchUpdateNotificationMasksRequest)`（同一上游接口承担创建与更新）
  - `delete` → `batchDeleteNotificationMasks(BatchDeleteNotificationMasksRequest)`
  - `list` → `listNotificationMasks(ListNotificationMasksRequest)`
- 屏蔽相关 SDK 类**全部**在 `com.huaweicloud.sdk.ces.v2.model.*`：`RelationType` / `ListRelationType` / `MaskType` / `ResourceDimension` / `Resource` / `ProductMetric` 等。

**create 字段映射（MCP 输入 → SDK Request body）：**

| MCP 输入 | SDK Request 字段 |
|---|---|
| `mask_name` | `body.withMaskName(String)` |
| `relation_type` | `body.setRelationType(RelationType.fromValue(...))` |
| `mask_type` | `body.withMaskType(MaskType.fromValue(...))` |
| `relation_ids` | `body.setRelationIds(List<String>)` |
| `resources[].namespace` / `dimensions` | `body.setResources(List<Resource>)`，dimensions 转 `ResourceDimension` |
| `metric_names` | `body.setMetricNames(List<String>)` |
| `product_metrics` | `body.setProductMetrics(List<ProductMetric>)` |
| `resource_level` | `body.setResourceLevel(ResourceLevelEnum.fromValue(...))`（**只有 setXxx，无 withXxx**） |
| `start_date` / `end_date` | `body.setStartDate(LocalDate.parse(...))` |
| `start_time` / `end_time` | `body.setStartTime(String)`（保留 `HH:mm:ss`） |
| `effective_timezone` | `body.withEffectiveTimezone(String)` |

**delete 字段映射：** `notification_mask_ids` → `body.withNotificationMaskIds(List<String>)`（请求体类 `BatchDeleteNotificationMasksRequestBody`）。

**list 字段映射：** 分页参数挂 Request，过滤参数挂 body（`ListNotificationMaskRequestBody`，注意单数 `Mask`）：

| MCP 输入 | SDK 字段 |
|---|---|
| `offset` / `limit` | `withOffset(Integer)` / `withLimit(Integer)` |
| `sort_key` / `sort_dir` | `setSortKey(ListNotificationMasksRequest.SortKeyEnum.fromValue(...))` / `setSortDir(...SortDirEnum.fromValue(...))` |
| `relation_type` | `body.setRelationType(ListRelationType.fromValue(...))` |
| `relation_ids` / `metric_name` / `mask_id` / `mask_name` / `resource_id` / `namespace` | `body.setXxx(...)` |
| `resource_level` | `body.setResourceLevel(ListNotificationMaskRequestBody.ResourceLevelEnum.fromValue(...))` |
| `mask_status` | `body.setMaskStatus(ListNotificationMaskRequestBody.MaskStatusEnum.fromValue(...))` |
| `dimensions` | `body.setDimensions(List<ResourceDimension>)` |

### 时间参数格式（不一致，务必区分）

- `create` 的 `start_date` / `end_date`：MCP 入参为 `yyyy-MM-dd` 字符串，service 层先正则 + 长度校验，再 `LocalDate.parse(...)` 转 SDK `LocalDate`（避免 `DateTimeParseException` 泄漏）。
- `create` 的 `start_time` / `end_time`：`HH:mm:ss` 字符串，正则 `^\d{2}:\d{2}:\d{2}$` 校验后**原样**作为 SDK `String` 透传（非 LocalTime）。
- `effective_timezone`：形如 `GMT+08:00` 字符串透传。
- 与 CES v2 alarm history（`OffsetDateTime`）、APM alarm（上游纯 String 时间）、CES metric data（`startMillis` / `endMillis` / `durationMinutes` UTC 毫秒）等其他工具的时间格式**互不相同**，不可凭印象复用。

### 枚举集差异（create vs list）

`relation_type` 在 create 与 list 的合法集**不同**，且对应**两个不同的 SDK 枚举类**：

- create：`RelationType`，合法值 `ALARM_RULE` / `RESOURCE` / `RESOURCE_POLICY_NOTIFICATION` / `RESOURCE_POLICY_ALARM` / `EVENT.SYS`（**有 `EVENT.SYS`，无 `DEFAULT`**）。
- list：`ListRelationType`，合法值 `ALARM_RULE` / `RESOURCE` / `RESOURCE_POLICY_NOTIFICATION` / `RESOURCE_POLICY_ALARM` / `DEFAULT`（**有 `DEFAULT`，无 `EVENT.SYS`**）。

service 层分别用 `ALLOWED_RELATION_TYPES` / `ALLOWED_LIST_RELATION_TYPES` 两个常量集；混用枚举类会编译失败，混用合法值集会误拒 / 误放。

### Service 层校验集中

三个工具共用 `CesNotificationMaskService`：

- `create`：`mask_name` 正则 `^[A-Za-z0-9_\-一-龥]{1,64}$`；`relation_type` / `mask_type` / `resource_level` 枚举集；条件必填（`ALARM_RULE`→`relation_ids`、`RESOURCE`→`resources`、`START_END_TIME`/`CYCLE_TIME`→四个时间字段）；日期 `LocalDate.parse`、时间正则。
- `delete`：`notification_mask_ids` 非空、长度 [1, 100]、每项非空白。
- `list`：`offset` [0, 10000]、`limit` [1, 100]；`sort_key` / `sort_dir` / `relation_type` / `resource_level` / `mask_status` 给值则须在枚举集；全部过滤字段缺省允许。

### MCP Tool annotations

| 工具 | readOnlyHint | destructiveHint | idempotentHint |
|---|---|---|---|
| `create_notification_mask` | false | false | false（**不幂等**：同名两次得两条不同 ID） |
| `delete_notification_masks` | false | **true** | true（删不存在 ID 无副作用） |
| `list_notification_masks` | true | false | true |

当前 `@Tool` 注解仅设置 `name` / `description`，annotations 待 Spring AI 升级后显式标注；本期靠 description 文案让 Agent 感知破坏性。

## Risks / Trade-offs

- **错误码 → retryable 映射（三个工具一致）**：输入校验失败 → `INVALID_PARAM`(false)；HTTP 429 → `UPSTREAM_THROTTLED`(true)；HTTP 401/403 → `UPSTREAM_AUTH_FAILED`(false)；HTTP 5xx → `UPSTREAM_ERROR`(true)；超时 → `TIMEOUT`(true)；序列化 / 未分类 → `INTERNAL`(false)。失败响应携带 `upstream_trace_id`。adapter 通过 `HuaweiCloudInvocation` 统一映射到 `SmartomException`，tool 层 catch 后转 `ErrorResponse`。
- **限流 / 重试 / 超时 / 可观测**：`create` / `delete` 走 `ces-write`（5 QPS，与读流量分离避免突发写挤占读）；`list` 走 `ces-readonly`（10 QPS）。仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s；单次 SDK 调用超时 10s。Micrometer `mcp_tool_invocation{tool=...,result=...,error_code=...}`（本期未交付看板）；INFO 日志含入参摘要（`maskName` / `relationType` / `maskType` / `ids.size` / `offset` / `limit`）+ 耗时 + upstream trace id。
- **写操作重试 vs 非幂等**：`create` 非幂等，但网络错误下重试是上游推荐做法，故沿用统一重试策略；上游 trace 可能出现多次相同请求，排障看上游 trace 而非本地日志。
- **AI 易错点**：
  1. v1/v2 `CesClient` 同名不同包，import 别写错；屏蔽相关类全在 `com.huaweicloud.sdk.ces.v2.model.*`。
  2. `RelationType`（create）vs `ListRelationType`（list）是不同枚举类且合法值集不同（`EVENT.SYS` 仅 create，`DEFAULT` 仅 list）。
  3. v2 SDK builder `withXxx`（链式）与 `setXxx`（void）并存，部分新枚举字段**只有 `setXxx`**（如 `setResourceLevel`），不要凭印象写 `withXxx`。
  4. `start_date` / `end_date` 是 SDK `LocalDate` 需 `LocalDate.parse`；`start_time` / `end_time` 是 `String` 保留 `HH:mm:ss`。
  5. `list` 的 body 可选：所有过滤字段缺省时不要 `setBody(空 body)`，adapter 用 `bodyHasValue` 标志位。
  6. 响应枚举字段需 `.getValue()` 转 String 再入 DTO，否则 Jackson 输出枚举对象嵌套结构污染 MCP 契约。
  7. `count` 为 `Integer`（可能 null），DTO 用包装类型不要拆箱；`delete` 响应 `notificationMaskIds` 为 null 时 adapter 兜底空 List。
  8. `delete` 返回数组短于入参是正常（ID 不存在 / 已删），不要当失败重试。
- **禁止编造入参**：发现链（`list` → 取 ID → `delete`）须严格按"先 `list` 定位真实 `notification_mask_id`，再传给 `delete`"的顺序；Agent 不得编造 `notification_mask_id` / `relation_ids`，必须来自上游真实返回。
- **遗留**：MCP `annotations` 在当前 Spring AI 版本未由 `@Tool` 实际透出，`destructiveHint=true` 等暂为语义意图（靠 description 表达）。
