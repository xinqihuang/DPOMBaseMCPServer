# Tool Spec: `list_aom_events`

> 状态: Ready · API 版本: **AOM v2 `ListEvents`（POST /v2/{project_id}/events）** · SDK: `huaweicloud-sdk-aom:3.1.177`
> 权威 schema 来源：SDK sources jar `com.huaweicloud.sdk.aom.v2.model.{ListEventsRequest, EventQueryParam2, EventQueryParam2Sort, RelationModel, ListEventsResponse, ListEventModel, PageInfo}`（已逐字段核对，非记忆）。

## 1. 定位

查询 AOM 事件与告警（活动告警 / 历史告警 / 全部事件）。诊断 Agent 的告警入口工具之一：
拿到告警的 metadata（含资源、级别等键值）后，可接 `list_aom_metrics` → `query_aom_metric_data`
或 `query_logs` 做下钻。

## 2. 请求映射（工具入参 → SDK）

| 工具入参 (snake_case) | 类型 | 必填 | SDK 目标 | 说明 |
|---|---|---|---|---|
| `type` | `AomAlertType` 枚举 | 否 | query `type` | `active_alert`=活动告警 / `history_alert`=历史告警；**不传 = 返回全部事件告警**（§4.3 (a) 受控枚举，SDK TypeEnum 同值） |
| `time_range` | String | **是** | body `time_range` | `startMs.endMs.durationMin`，`-1` 表示由服务端推算（与 `query_aom_metric_data` 同款格式，正则校验复用 `AomPatterns.TIME_RANGE`） |
| `step` | Long | 否 | body `step` | 统计步长（毫秒），如 60000；>0 |
| `search` | String | 否 | body `search` | 对 metadata 字段的模糊匹配 |
| `sort_order_by` | List\<String\> | 否 | body `sort.order_by` | 排序字段列表；提供 `sort_order` 时必填（SDK 约束：sort 不为空时 order_by 必填） |
| `sort_order` | String | 否 | body `sort.order` | `asc` / `desc`（封闭集，service 校验） |
| `metadata_relation` | List\<AomEventMetadataRelation\> | 否 | body `metadata_relation` | 元素 `{key, value[], relation}`；`relation` ∈ `AND`/`OR`/`NOT`（SDK RelationEnum 封闭集） |
| `limit` | Integer | 否 | query `limit` | 不填 SDK 默认 1000；本地校验 ≥1 |
| `marker` | String | 否 | query `marker` | 分页标记，初始 0，后续取响应 `page_info.next_marker` |

**显式砍掉的请求字段及理由**：`Enterprise-Project-Id`（header）——本服务凭据固定单租户/单项目作用域，
Agent 无企业项目维度的输入来源，透传只会诱导编造；如未来要支持 EPS 过滤，另起任务卡。

## 3. 响应映射（SDK → DTO，§4.1 无损）

`ListEventsResponse` → `AomListEventsResponse`：

| DTO 字段 | SDK 来源 | 类型 |
|---|---|---|
| `events` | `events` | List\<AomEvent\> |
| `page_info` | `page_info` | AomEventPageInfo |

`ListEventModel` → `AomEvent`（**11 个字段全量，无删减**）：

| DTO 字段 (snake_case) | SDK @JsonProperty | 类型 |
|---|---|---|
| `id` | `id` | String |
| `event_sn` | `event_sn` | String |
| `starts_at` | `starts_at` | Long（UTC 毫秒） |
| `ends_at` | `ends_at` | Long |
| `arrives_at` | `arrives_at` | Long |
| `timeout` | `timeout` | Long（毫秒） |
| `enterprise_project_id` | `enterprise_project_id` | String |
| `metadata` | `metadata` | Map\<String, String\> |
| `annotations` | `annotations` | Map\<String, Object\> |
| `attach_rule` | `attach_rule` | Map\<String, Object\> |
| `policy` | `policy` | Map\<String, Object\> |

`PageInfo` → `AomEventPageInfo`（3 字段全量；注意与既有 `AomPagination`（offset 制）不是同一 SDK 类型，**不要复用**）：

| DTO 字段 | SDK @JsonProperty | 类型 |
|---|---|---|
| `current_count` | `current_count` | Integer |
| `previous_marker` | `previous_marker` | String |
| `next_marker` | `next_marker` | String |

## 4. 错误与校验

- `time_range` 缺失/格式错、`step` ≤ 0、`limit` < 1、`sort_order` 不在 asc/desc、
  提供 `sort_order` 但 `sort_order_by` 为空、`metadata_relation` 的 key 空白或 `relation`
  不在 AND/OR/NOT → `INVALID_PARAM`（service 层）。
- `type` 非法值由枚举反序列化在框架层拒绝（与 T24 namespace 同款约定）。
- 上游异常经 `SdkExceptionMapper` 统一映射；限流实例 `aom-readonly`，API 名 `aom.listEvents`。

## 5. 测试策略

- **UT（service）**：time_range 必填/格式、step/limit 边界、sort 成对约束、relation 枚举校验、合法请求透传 adapter。
- **UT（tool）**：成功透传、`SmartomException` → `ErrorResponse`、type 枚举入参映射。
- **TC（契约）**：`sdk-samples/aom/list-events-response.json`（含 2 条事件：一条全字段、一条最小字段）
  经 SDK 反序列化 → adapter 映射，断言 `AomEvent` 11 字段 + `page_info` 3 字段全覆盖，漂移即 fail。
- **枚举**：`AomAlertType` 封闭性（2 值）、双写法解析、未知值拒绝。
