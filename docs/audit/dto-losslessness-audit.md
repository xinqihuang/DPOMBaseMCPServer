# DTO Losslessness Audit

> 跟踪 T19 PR-1 ~ PR-5 中每个 response DTO 对 SDK 模型的字段覆盖情况。**权威字段集**以
> sparse-checkout 的 SDK 源码 `model/*.java` 为准（不是 console API Explorer）。

| 列含义 | 说明 |
|---|---|
| **SDK 字段** | SDK 模型类中所有 `@JsonProperty` 顶层字段 |
| **DTO 已覆盖** | 自定义 record 中可访问的字段集（含嵌套子 record 暴露的） |
| **缺失项** | 出现在 SDK 但 DTO 不暴露的字段 |
| **处置** | `OK / 待补齐 / 已补齐(PR-X)` |

---

## PR-1 — CES `ListAlarmHistories`（v2）

| 项 | 内容 |
|---|---|
| SDK 类 | `ces.v2.model.ListAlarmHistoriesResponse` → `AlarmHistoryItemV2`（+ Metric / Condition / AdditionalInfo / DataPointInfo / AlarmActions 等） |
| DTO | `CesListAlarmsResponse` → `CesAlarmHistory`（+ CesAlarmMetric / CesAlarmCondition / CesAlarmAdditionalInfo / CesAlarmAction / CesAlarmDataPoint） |
| 切版本 | v1 → **v2**（v1 字段集严重不足，且 `record_id` / `mask_status` / 5 个时间戳只在 v2） |
| 处置 | **已补齐(PR-1)**；唯一未建模：`mask_status`（3.1.177 v2 尚未提供，3.1.194+ 才有，待 SDK 升级补） |

字段对照（v2 3.1.177）：

| SDK 顶层字段 | DTO 字段 | 备注 |
|---|---|---|
| record_id | recordId | v2 新增 |
| alarm_id | alarmId | |
| name | alarmName | v1 风格命名保留 |
| status (StatusEnum) | alarmStatus (String) | `.getValue()` |
| level (LevelEnum, Integer-backed) | alarmLevel (Integer) | `.getValue()` |
| type (TypeEnum) | alarmType (String) | `.getValue()` |
| action_enabled | actionEnabled | v2 新增 |
| begin_time / end_time / first_alarm_time / last_alarm_time / alarm_recovery_time | 同名 (OffsetDateTime) + 派生的 triggerTime/updateTime (Long millis) | v2 新增；triggerTime/updateTime 维持 v1 兼容 |
| metric (AlarmHistoryItemV2Metric) | metric → `CesAlarmMetric` | namespace / metricName 也派生回顶层 |
| condition (AlarmHistoryItemV2Condition) | condition → `CesAlarmCondition` | PeriodEnum/SuppressDurationEnum → Integer |
| additional_info (AdditionalInfo) | additionalInfo → `CesAlarmAdditionalInfo` | |
| alarm_actions / ok_actions | alarmActions / okActions → `List<CesAlarmAction>` | action.type 取 `.getValue()` |
| data_points | dataPoints → `List<CesAlarmDataPoint>` | DataPointInfo.time **是 String 不是 OffsetDateTime** |
| (mask_status) | — | 3.1.177 SDK 不暴露此字段，待 SDK 升级补 |
| alarm_description | alarmDescription (= null) | v2 不提供，保留 record 位以兼容历史 caller |

契约测试：`CesListAlarmHistoriesContractTest`（2 cases），样本 `sdk-samples/ces/list-alarm-histories-v2-response.json`。

---

## PR-2 — CES 其余 response（metrics / metric-data / batch / notification-masks）

### 1. `ListMetrics`（v1）

| 项 | 内容 |
|---|---|
| SDK 类 | `ces.v1.model.ListMetricsResponse` → `MetricInfoList`(+ `MetricsDimension`) + `MetaData` |
| DTO | `CesListMetricsResponse` → `CesMetricInfo`(+ `CesMetricDimension`) + `CesPagination` |
| 处置 | **OK**（已无损） |

| SDK 字段 | DTO 字段 |
|---|---|
| metrics | metrics |
| meta_data.count | pagination.count |
| meta_data.total | pagination.total |
| meta_data.marker | pagination.nextMarker |
| MetricInfoList.namespace / metric_name / unit / dimensions | CesMetricInfo.namespace / metricName / unit / dimensions |
| MetricsDimension.name / value | CesMetricDimension.name / value |
| —（DTO 新增便利字段） | pagination.hasMore（计算字段：`nextMarker != null && count > 0`） |

契约测试：现有 `CesListMetricsContractTest` 已覆盖（保留）。

### 2. `ShowMetricData`（v1）

| 项 | 内容 |
|---|---|
| SDK 类 | `ces.v1.model.ShowMetricDataResponse` → `Datapoint` |
| DTO | `CesQueryMetricDataResponse` → `CesDatapoint` |
| 处置 | **OK**（已无损，PR-2 新增契约测试） |

| SDK 字段 | DTO 字段 |
|---|---|
| metric_name | metricName |
| datapoints | datapoints |
| Datapoint.timestamp / unit / max / min / average / sum / variance | 同名 |

契约测试（新增）：`CesShowMetricDataContractTest`，样本 `sdk-samples/ces/show-metric-data-response.json`。

### 3. `BatchListMetricData`（v1）

| 项 | 内容 |
|---|---|
| SDK 类 | `ces.v1.model.BatchListMetricDataResponse` → `BatchMetricData` → `DatapointForBatchMetric` |
| DTO | `CesBatchQueryMetricDataResponse` → `CesBatchMetricResult`（复用 `CesMetricDimension` 与 `CesDatapoint`） |
| 处置 | **OK**（已无损，PR-2 新增契约测试） |

| SDK 字段 | DTO 字段 |
|---|---|
| metrics | metrics |
| BatchMetricData.unit / namespace / metric_name / dimensions / datapoints | unit / namespace / metricName / dimensions / datapoints |
| DatapointForBatchMetric.timestamp / max / min / average / sum / variance | CesDatapoint.timestamp / max / min / average / sum / variance（unit 在父级，CesDatapoint.unit=null） |

契约测试（新增）：`CesBatchListMetricDataContractTest`，样本 `sdk-samples/ces/batch-list-metric-data-response.json`。

### 4. `ListNotificationMasks`（v2）

| 项 | 内容 |
|---|---|
| SDK 类 | `ces.v2.model.ListNotificationMasksResponse` → `ListNotificationMaskRespNotificationMasks`（20 字段，含 `ResourceCategory` / `ProductMetric` / `PoliciesInListResp` 嵌套，后者再嵌套 `MetricExtraInfo` / `Period` / `HierarchicalValue` / `SuppressDuration`） |
| DTO | `CesListNotificationMasksResponse` → `CesNotificationMask`（+ `NotificationMaskProductMetric` / 新增 `CesNotificationMaskResourceCategory` / `CesNotificationMaskPolicy` / `CesNotificationMaskMetricExtraInfo` / `CesNotificationMaskHierarchicalValue`） |
| 处置 | **PR-2 已补齐**：增加 5 个顶层字段 + 4 个新 sub-record |

补齐前缺失（5）→ PR-2 已补：

| 缺失字段 | SDK 类型 | DTO 字段 |
|---|---|---|
| resource_type | MaskResourceType | resourceType (String, `.getValue()`) |
| resources | List<ResourceCategory> | resources → `List<CesNotificationMaskResourceCategory>` |
| create_time | Long | createTime |
| update_time | Long | updateTime |
| policies | List<PoliciesInListResp>（含 MetricExtraInfo / HierarchicalValue 子结构） | policies → `List<CesNotificationMaskPolicy>` |

注意点（PR-2 中已处理）:
- 任务卡 §「样板」原本写 `Resources / ResourceDimension` —— **实际 SDK 是 `ResourceCategory`**（只含 `namespace + dimension_names: List<String>`），与请求侧的 `Resource`/`ResourceDimension`（含具名维度）不是同一个类。
- `PoliciesInListResp` 中 `period` / `suppress_duration` 是 Integer-backed enum-like 类，DTO 取 `.getValue()` 为 Integer。
- `HierarchicalValue` 包含 critical / major / minor / info 四个分级阈值，是诊断 Agent 关注的关键信号。

契约测试（新增）：`CesListNotificationMasksContractTest`（2 cases），样本 `sdk-samples/ces/list-notification-masks-response.json`。

---

## PR-3 — APM（`ShowSpanSearch` + `ShowTopology`）

### 1. `ShowSpanSearch`（v1）

| 项 | 内容 |
|---|---|
| SDK 类 | `apm.v1.model.ShowSpanSearchResponse` → `ClientSpanInfo`（22 字段） |
| DTO | `ApmQueryTracesResponse` → `ApmSpan` |
| 处置 | **PR-3 已补齐 9 字段**：globalPath / envId / instanceId / appId / bizId / domainId / isAsync / type / bizCode |

| SDK 字段 | DTO 字段 | 备注 |
|---|---|---|
| total | total | |
| span_info_list | spans | |
| ClientSpanInfo.global_trace_id / trace_id / span_id | globalTraceId / traceId / spanId | |
| ClientSpanInfo.source / real_source / class_name | source / realSource / className | |
| ClientSpanInfo.start_time / time_used | startTime / timeUsed | Long millis |
| ClientSpanInfo.code | code | Integer |
| ClientSpanInfo.has_error / error_reasons | hasError / errorReasons | |
| ClientSpanInfo.http_method | httpMethod | |
| ClientSpanInfo.tags | tags | Map<String,String> |
| ClientSpanInfo.global_path | globalPath | PR-3 新增 |
| ClientSpanInfo.env_id / instance_id / app_id / biz_id | envId / instanceId / appId / bizId | Long，PR-3 新增 |
| ClientSpanInfo.domain_id | domainId | Integer，PR-3 新增 |
| ClientSpanInfo.is_async | isAsync | PR-3 新增 |
| ClientSpanInfo.type | type | PR-3 新增 |
| ClientSpanInfo.biz_code | bizCode | PR-3 新增 |

契约测试（新增）：`ApmShowSpanSearchContractTest`（2 cases），样本 `sdk-samples/apm/show-span-search-response.json`。

### 2. `ShowTopology`（v1）

| 项 | 内容 |
|---|---|
| SDK 类 | `apm.v1.model.ShowTopologyResponse` → `TraceTopologyNode`(3) / `TraceTopologyLine`(7) / `TraceTopologyLineInfo`(4) |
| DTO | `ApmGetTopologyResponse` → `ApmTopologyNode` / `ApmTopologyLine` |
| 处置 | **PR-3 已补齐 5 字段** 到 ApmTopologyLine：id / clientArgument / clientEventId / serverArgument / serverEventId |

| SDK 字段 | DTO 字段 | 备注 |
|---|---|---|
| global_trace_id / node_list / line_list | globalTraceId / nodes / lines | |
| TraceTopologyNode.node_id / node_name / hint | nodeId / nodeName / hint | OK |
| TraceTopologyLine.start_node_id / end_node_id / span_id / hint | startNodeId / endNodeId / spanId / hint | OK |
| TraceTopologyLine.client_info.start_time / time_used | clientStartTime / clientTimeUsed | 沿用既有 `client*` flat 风格 |
| TraceTopologyLine.server_info.start_time / time_used | serverStartTime / serverTimeUsed | 沿用既有 `server*` flat 风格 |
| TraceTopologyLine.id | id | PR-3 新增 |
| TraceTopologyLine.client_info.argument / event_id | clientArgument / clientEventId | PR-3 新增 |
| TraceTopologyLine.server_info.argument / event_id | serverArgument / serverEventId | PR-3 新增 |

设计说明：现有 DTO 把 `client_info` / `server_info` 嵌套对象拍平为 `client*` / `server*` 前缀的 4 个标量。按 §4.1「不拍平丢信息」准则——本期保留已有命名风格以维持 Agent 兼容（"不改已有响应字段名"），把剩余 4 个嵌套字段（{client,server} × {argument, event_id}）沿同一前缀风格继续平铺补齐。**没有丢字段**，与"sub-record 拆分"语义等效。

契约测试（新增）：`ApmShowTopologyContractTest`，样本 `sdk-samples/apm/show-topology-response.json`。

---

## PR-4 / PR-5（待开工）

- **PR-4 — AOM**：`ListLogItemsResponse`、`ListMetricItemsResponse / MetricItemResultAPI`、`ListSampleResponse / QuerySample / SampleDataValue / MetricDataPoints / StatisticValue`
- **PR-5 — LTS**：`ListLogsResponse / LogContents`、`ListLogContextResponse`

每个 PR 完成后在本文档追加对应小节。
