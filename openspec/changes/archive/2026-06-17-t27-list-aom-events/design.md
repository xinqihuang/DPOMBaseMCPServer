## Context

存量回填。原始 spec `docs/specs/tools/list_aom_events.md`、任务卡 `docs/tasks/T27-list-aom-events.md`。本文承载主 spec 放不下的重契约：SDK 字段映射、时间格式、非功能要求。

## Goals / Non-Goals

**Goals:**
- 为诊断 Agent 提供 AOM 侧统一的事件/告警入口（活动 / 历史 / 全部）。
- 无损暴露事件全量元数据，衔接 metadata → metrics/logs 下钻。

**Non-Goals:**
- `CountEvents`、告警规则 CRUD（写操作）。
- 透传 `Enterprise-Project-Id`（单租户作用域，避免诱导编造）。
- 缓存（实时告警）。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.aom.v2.AomClient`，方法 `listEvents(ListEventsRequest)`，`POST /v2/{project_id}/events`，SDK v3.1.x。
- 权威 schema：`com.huaweicloud.sdk.aom.v2.model.{ListEventsRequest, EventQueryParam2, EventQueryParam2Sort, RelationModel, ListEventsResponse, ListEventModel, PageInfo}`。

**请求映射（工具入参 → SDK）**：`type`→query `type`（枚举 TypeEnum 同值）；`time_range`/`step`/`search`→body；`sort_order_by`/`sort_order`→body `sort.{order_by,order}`；`metadata_relation`→body（元素 `{key,value[],relation}`，relation∈AND/OR/NOT）；`limit`/`marker`→query。

**响应映射**：`ListEventModel`→`AomEvent`（11 字段全留）；`PageInfo`→`AomEventPageInfo`（`current_count`/`previous_marker`/`next_marker`）。

### 时间格式

`time_range` = `startMillis.endMillis.durationMinutes`，`-1` 表示服务端推算（与 `query_aom_metric_data` 同款，正则复用 `AomPatterns.TIME_RANGE`，从 `AomMetricDataService` 私有常量上提共用）。

### 易错点

1. `PageInfo`（marker 制）≠ offset 制 `AomPagination`，不要复用。
2. `metadata`/`annotations`/`attach_rule`/`policy` 是 Map，原样承载不拍平。
3. `type` 是 query 参数，不是 body 字段。
4. sort：`order` 提供时 `order_by` 必填（SDK 约束），校验放 service。

## Risks / Trade-offs

- **限流**：复用 `aom-readonly`，API 名 `aom.listEvents`；upstreamTraceId 由 `HuaweiCloudInvocation` 统一在 finally 记录。
- **枚举演进**：`AomAlertType` 当前 2 值；上游新增类型需同步枚举（非法值框架层拒绝）。
