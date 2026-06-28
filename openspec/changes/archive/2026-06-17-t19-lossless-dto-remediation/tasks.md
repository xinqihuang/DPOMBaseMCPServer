> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T19-lossless-dto-remediation.md`，状态 Ready→Done，按 PR-0~PR-5 拆分逐步合入）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. 根因修订（PR-0：CLAUDE.md §4.1）

- [x] 1.1 把 `CLAUDE.md §4.1` 整段替换为「无损投影 + 钉死 API 版本 + SDK 源码为权威 + 类型贴齐 SDK + 契约测试兜底」版本（照贴任务卡替换文本）

## 2. CES alarm history 切 v2 + 无损样板（PR-1）

- [x] 2.1 链路切 `cesV2Client`（v2 `CesClient`）调 v2 `listAlarmHistories`，响应 `ListAlarmHistoriesResponse`（`count` + `alarmHistories`）
- [x] 2.2 `CesAlarmHistory` 无损覆盖 `AlarmHistoryItemV2` 顶层全字段（record_id / alarm_id / name / status / level / type / action_enabled / 5 个时间字段 / mask_status）
- [x] 2.3 嵌套各建子 record：`metric`(+dimensions) / `condition` / `additional_info` / `alarm_actions` / `ok_actions` / `data_points`
- [x] 2.4 类型贴齐：顶层时间 `OffsetDateTime`、`DataPointInfo.time` String、condition.value Double、枚举取 `.getValue()`
- [x] 2.5 修 `CorrelateIncidentService` 对 `CesAlarmHistory` 的引用，编译通过、关联行为不回归
- [x] 2.6 `CesListAlarmHistoriesContractTest` + 样本 `sdk-samples/ces/list-alarm-histories-v2-response.json`，断言覆盖全字段；故意删一字段测试变红

## 3. CES 其余 DTO 审计 + 无损化（PR-2）

- [x] 3.1 `CesListMetricsResponse` ← `ces.v1.model.ListMetricsResponse`/`MetricInfoList`(+`MetricsDimension`) 逐字段比对，确认有损补齐 / OK 标注
- [x] 3.2 `CesQueryMetricDataResponse` ← `ces.v1.model.ShowMetricDataResponse`/`Datapoint`（unit / 聚合）
- [x] 3.3 `CesBatchQueryMetricDataResponse` ← `ces.v1.model.BatchListMetricDataResponse`/`BatchMetricData`/`DatapointForBatchMetric`
- [x] 3.4 `CesListNotificationMasksResponse` ← `ces.v2.model.ListNotificationMasksResponse`/`ListNotificationMaskRespNotificationMasks`(+`Resource`/`ResourceDimension`/`ProductMetric`/`MaskType`)
- [x] 3.5 各被改 DTO 配 `*ContractTest` + sdk-samples 样本；findings 片段写入 `docs/audit/dto-losslessness-audit.md`

## 4. APM DTO 审计 + 无损化（PR-3）

- [x] 4.1 `ApmSpan` / `ApmQueryTracesResponse` ← `apm.v1.model.ShowSpanSearchResponse`/`ClientSpanInfo`（重点：已知偏薄）
- [x] 4.2 拓扑 DTO ← `apm.v1.model.ShowTopologyResponse`/`TraceTopologyNode`/`TraceTopologyLine`/`TraceTopologyLineInfo`
- [x] 4.3 各被改 DTO 配 `*ContractTest` + sdk-samples 样本；findings 写入审计文档

## 5. AOM DTO 审计 + 无损化（PR-4）

- [x] 5.1 `AomQueryLogsResponse` ← `aom.v2.model.ListLogItemsResponse`
- [x] 5.2 `AomListMetricsResponse` 等 ← `aom.v2.model.ListMetricItemsResponse`/`MetricItemResultAPI`
- [x] 5.3 `AomSampleSeries`/`AomMetricDatapoint`/`AomStatisticValue` ← `aom.v2.model.ListSampleResponse`/`QuerySample`/`SampleDataValue`/`MetricDataPoints`/`StatisticValue`
- [x] 5.4 各被改 DTO 配 `*ContractTest` + sdk-samples 样本；findings 写入审计文档

## 6. LTS DTO 审计 + 无损化（PR-5）

- [x] 6.1 `LtsListLogsResponse` / `LtsLogEntry` ← `lts.v2.model.ListLogsResponse`/`LogContents`（labels / line_num / 分析态）
- [x] 6.2 `LtsListLogContextResponse` ← `lts.v2.model.ListLogContextResponse`
- [x] 6.3 各被改 DTO 配 `*ContractTest` + sdk-samples 样本；findings 写入审计文档

## 7. 收口校验（全期）

- [x] 7.1 审计与处置表中每个 DTO 都在 `docs/audit/dto-losslessness-audit.md` 有结论（缺失项 / 补齐 or OK+理由）
- [x] 7.2 全模块 `mvn -q test` 通过；Checkstyle 0；依赖方向不破（mcp / monitoring 不 import SDK）
- [x] 7.3 无任何已有响应字段被改名（只增不改名）

## 8. 遗留项（本期未交付）

- [ ] 8.1 除契约测试外的 Tool / Service / Adapter 单元测试补全
- [ ] 8.2 各 DTO 对应冒烟脚本 `scripts/smoke/`
- [ ] 8.3 Micrometer 指标看板配置
- [ ] 8.4 README 各工具使用示例更新（反映无损后的响应字段）
- [ ] 8.5 LTS 结构化日志查询（`/struct-content/query`）无损化（与本期 `/content/query` 不同链路）
