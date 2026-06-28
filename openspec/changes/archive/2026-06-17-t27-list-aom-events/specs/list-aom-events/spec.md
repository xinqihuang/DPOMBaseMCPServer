## ADDED Requirements

### Requirement: AOM 事件与告警查询

系统 SHALL 提供只读工具 `list_aom_events`，调用 AOM SDK v2 `ListEvents` 查询事件与告警，支持受控枚举 `type`（`active_alert` 活动告警 / `history_alert` 历史告警；不传则返回全部事件告警），以及 `time_range` / `step` / `search` / 排序 / `metadata_relation` 过滤与 marker 分页。该工具 MUST 为只读，不做 `CountEvents` 或告警规则 CRUD，不缓存（实时数据）。

`type` MUST 为受控枚举（来源 §4.3(a)），非法值 MUST 在框架反序列化层被拒绝。

#### Scenario: 按类型查询活动告警
- **WHEN** `type=active_alert` 且 `time_range` 合法
- **THEN** 系统 SHALL 将 `type` 作为 query 参数、其余过滤装配到 body `EventQueryParam2`
- **AND** 返回 `events[]` 与 marker 制 `page_info`

#### Scenario: 不传 type 返回全部事件
- **WHEN** 未提供 `type`
- **THEN** 系统 SHALL 返回全部事件与告警，不强制类型过滤

### Requirement: 输入校验

系统 SHALL 在 service 层校验：`time_range` 必填且匹配 `startMs.endMs.durationMin`（`-1` 表示服务端推算）；`step` MUST > 0；`limit` MUST ≥ 1；`sort_order` MUST ∈ {`asc`,`desc`}；提供 `sort_order` 时 `sort_order_by` MUST 非空；`metadata_relation` 元素的 `key` MUST 非空白且 `relation` MUST ∈ {`AND`,`OR`,`NOT`}。任一不满足 MUST 返回 `INVALID_PARAM`。

#### Scenario: time_range 缺失或格式错
- **WHEN** `time_range` 为空或不匹配格式
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

#### Scenario: 排序成对约束
- **WHEN** 提供 `sort_order` 但 `sort_order_by` 为空
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: ListEventModel 无损投影

响应 DTO `AomEvent` SHALL 无损覆盖 SDK `ListEventModel` 全部 11 个字段（`id` / `event_sn` / `starts_at` / `ends_at` / `arrives_at` / `timeout` / `enterprise_project_id` / `metadata` / `annotations` / `attach_rule` / `policy`）。`page_info` SHALL 覆盖 marker 制 `PageInfo` 的 3 字段（`current_count` / `previous_marker` / `next_marker`），不得复用 offset 制的 `AomPagination`。Map 类型字段（metadata/annotations/attach_rule/policy）MUST 原样承载、不拍平。

#### Scenario: 契约测试全字段断言
- **GIVEN** 真实 SDK 样本 `sdk-samples/aom/list-events-response.json`（含全字段与最小字段各一条）
- **WHEN** 反序列化为 SDK `ListEventsResponse` 并经 adapter 映射
- **THEN** `AomEvent` 11 字段与 `page_info` 3 字段 SHALL 逐一断言通过，漂移即 fail

### Requirement: 上游异常映射

系统 SHALL 经 `SdkExceptionMapper` 将 AOM SDK 异常映射到统一 `ErrorCode`，限流实例 `aom-readonly`，API 名 `aom.listEvents`；失败携带 `retryable` 与 `upstream_trace_id`。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`
