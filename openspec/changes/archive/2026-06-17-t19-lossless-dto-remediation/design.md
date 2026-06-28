## Context

存量基础设施 / 横切回填。原始任务卡：`docs/tasks/T19-lossless-dto-remediation.md`（状态 Ready→Done，按 PR-0~PR-5 拆分逐步合入）；关联修订 `CLAUDE.md §4.1`。本变更属 infra / 横切层，无对外 capability spec，因此把全部重契约（SDK 类 / 方法 / 版本、字段映射表、错误码映射、限流 / 重试 / 超时 / 可观测、各服务不一致的时间参数格式、AI 易错点）沉淀到本设计文档。

**权威 schema 来源**：console API Explorer 需登录且是 SPA，抓不到；一律以 SDK 源码 model 类为准——sparse-checkout `huaweicloud/huaweicloud-sdk-java-v3` 对应 `services/<svc>/src/main/java/com/huaweicloud/sdk/<svc>/<ver>/model/` 下的真实类，按其 `@JsonProperty` + 字段类型逐字段映射。公开文档站 `support.huaweicloud.com/api-*` 仅作语义参考。**禁止凭记忆猜类名 / 字段 / 类型。**

根因与处置（任务卡「背景」摘要）：DTO 系统性偏薄源于 §4.1 被误读为「最小子集」+ alarm history 打错 API 版本（用了 CES v1 而目标字段只在 v2）。本期决策已拍板：alarm history 切 CES v2；所有 DTO 原汁原味无损覆盖 SDK 字段；不止改 alarm history，全 adapter 一起治理。

## Goals / Non-Goals

**Goals:**
- 把 `CLAUDE.md §4.1` 从「最小子集」纠正为「无损投影 + 钉死 API 版本 + SDK 源码为权威 + 契约测试兜底」。
- CES alarm history 链路切 CES v2，`CesAlarmHistory` 无损覆盖 `AlarmHistoryItemV2` 全字段含嵌套子 record。
- 对审计表中每个 response DTO 逐字段比对 SDK 源码 model 类，确认有损的全部补齐，本就无损的标注 OK 不动。
- 每个被改 DTO 配 `*ContractTest`，真实 SDK 样本经 adapter 映射后断言覆盖全字段，任删一字段即变红。
- 维持 SDK 类型不外泄（§4.1 第一铁律）：无损 ≠ 透传 SDK 类，仍转成我们的 record。

**Non-Goals:**
- 不改 request DTO 设计、不改任何 `@Tool(name=...)` 契约名、不加新 tool、不动分页 / 限流 / 错误码语义。
- 不改已有响应字段名（只增不改名，保证 Agent 向后兼容）。
- 本就无损的 DTO 只标注不重构。
- 除契约测试外的 UT / 冒烟脚本不在本卡（列遗留项）。
- 不做客户端聚合 / 排序 / 维度展开 / SQL 预校验。

## Decisions

### 架构决策（infra / 横切）

- **治理边界 = adapter 层**：DTO 无损化只发生在各 `agentic-adapter-*` 子模块内部；monitoring / mcp 层不感知 SDK 类型变更，只在响应里多收到字段。`grep -r "com.huaweicloud.sdk" agentic-monitoring agentic-mcp` 应无结果。
- **只增不改名**：响应 DTO 一律「新增字段 / 拆嵌套子 record」，绝不重命名既有字段——改名会破坏 Agent 已有契约。嵌套对象拆子 record（metric / condition / data_points 等），不拍平成几个标量后丢其余。
- **API 版本显式钉死**：每个工具对应的 SDK API 版本（v1 / v2）写进本设计，不让实现自选；字段缺失先怀疑「打错版本」。alarm history 即是 v1→v2 的纠错。
- **依赖方向不变**：`mcp → monitoring → adapter → common`；本变更不新增模块、不改依赖边。
- **选型：契约测试做兜底网**，而非编译期反射断言。落真实 SDK 响应样本到 `test/resources/sdk-samples/<svc>/`，反序列化为 SDK model → 经 adapter 映射为 DTO → 断言覆盖样本全字段；**故意删 DTO 一字段该测试必须变红**（验证有效）。

### SDK 映射（审计与处置表 → 必读真实 SDK 类）

| 自定义 DTO | adapter | 权威 SDK 类（读这个） | 处置 |
|---|---|---|---|
| `CesAlarmHistory` / `CesListAlarmsResponse` | CES | **切 v2** `ces.v2.model.ListAlarmHistoriesResponse` → `AlarmHistoryItemV2`（+ `...Metric` / `...MetricDimensions` / `...Condition` / `AdditionalInfo` / `DataPointInfo` / `...AlarmActions`） | 切版本 + 无损重画（PR-1） |
| `CesListMetricsResponse` | CES | `ces.v1.model.ListMetricsResponse` → `MetricInfoList`（+ `MetricsDimension`） | 审计补齐 |
| `CesQueryMetricDataResponse` | CES | `ces.v1.model.ShowMetricDataResponse` → `Datapoint` | 审计补齐（unit / 聚合是否全） |
| `CesBatchQueryMetricDataResponse` | CES | `ces.v1.model.BatchListMetricDataResponse` → `BatchMetricData` / `DatapointForBatchMetric` | 审计补齐 |
| `CesListNotificationMasksResponse` | CES | `ces.v2.model.ListNotificationMasksResponse` → `ListNotificationMaskRespNotificationMasks`（+ `Resource` / `ResourceDimension` / `ProductMetric` / `MaskType`） | 审计补齐 |
| `ApmSpan` / `ApmQueryTracesResponse` | APM | `apm.v1.model.ShowSpanSearchResponse` → `ClientSpanInfo` | 审计补齐（已知偏薄，重点） |
| `ApmTopologyNode` / `ApmTopologyLine` / `ApmGetTopologyResponse` | APM | `apm.v1.model.ShowTopologyResponse` → `TraceTopologyNode` / `TraceTopologyLine` / `TraceTopologyLineInfo` | 审计补齐 |
| `AomQueryLogsResponse` | AOM | `aom.v2.model.ListLogItemsResponse` | 审计补齐 |
| `AomListMetricsResponse` 等 | AOM | `aom.v2.model.ListMetricItemsResponse` → `MetricItemResultAPI` | 审计补齐 |
| `AomSampleSeries` / `AomMetricDatapoint` / `AomStatisticValue` | AOM | `aom.v2.model.ListSampleResponse` → `QuerySample` / `SampleDataValue` / `MetricDataPoints` / `StatisticValue` | 审计补齐 |
| `LtsListLogsResponse` / `LtsLogEntry` | LTS | `lts.v2.model.ListLogsResponse` → `LogContents` | 审计补齐（labels / line_num / 分析态） |
| `LtsListLogContextResponse` | LTS | `lts.v2.model.ListLogContextResponse` | 审计补齐 |

> findings 表每行：SDK 字段全集 / DTO 已覆盖 / **缺失项** / 处置（补齐 or OK + 理由）。

### 样板：`CesAlarmHistory` v2 字段映射（字段已查实，照此写）

切 `cesV2Client`（v2 `CesClient`）调 v2 `listAlarmHistories`。响应 `ListAlarmHistoriesResponse`：`getCount()`（Integer）+ `getAlarmHistories()` → `List<AlarmHistoryItemV2>`。

**`AlarmHistoryItemV2` 顶层字段（无损全收）**：

| SDK getter | 类型 | 契约字段 |
|---|---|---|
| getRecordId | String | record_id |
| getAlarmId | String | alarm_id |
| getName | String | name |
| getStatus | StatusEnum → `.getValue()` | status |
| getLevel | LevelEnum → `.getValue()`（Integer） | level |
| getType | TypeEnum → `.getValue()` | type |
| getActionEnabled | Boolean | action_enabled |
| getBeginTime / getEndTime / getFirstAlarmTime / getLastAlarmTime / getAlarmRecoveryTime | **OffsetDateTime** | 各时间字段 |
| getMetric | AlarmHistoryItemV2Metric | metric（子 record） |
| getCondition | AlarmHistoryItemV2Condition | condition（子 record） |
| getAdditionalInfo | AdditionalInfo | additional_info（子 record） |
| getAlarmActions / getOkActions | List<AlarmHistoryItemV2AlarmActions> | alarm_actions / ok_actions（子 record） |
| getDataPoints | List<DataPointInfo> | data_points（子 record） |
| getMaskStatus | MaskStatusEnum → `.getValue()` | mask_status |

**嵌套子类真实字段（各建一个子 record）**：

- `AlarmHistoryItemV2Metric` → namespace(String) / metricName(String) / dimensions(List<…MetricDimensions>)
- `AlarmHistoryItemV2MetricDimensions` → name(String) / value(String)
- `AlarmHistoryItemV2Condition` → period(Integer) / filter(String) / comparisonOperator(String) / value(Double) / unit(String) / count(Integer) / suppressDuration(Integer)
- `AdditionalInfo` → resourceId(String) / resourceName(String) / eventId(String)
- `DataPointInfo` → time(**String**) / value(Double)
- `AlarmHistoryItemV2AlarmActions` → type(枚举 → `.getValue()`) / notificationList(List<String>)

> 类型细节（猜会错）：顶层时间是 `OffsetDateTime`，但 `DataPointInfo.time` 是 `String`；condition.value 是 `Double`；level / status / type / maskStatus / actions.type 都是 SDK 枚举，取 `.getValue()`。

> v1 → v2 引用迁移：`CorrelateIncidentService` 原读 v1 `AlarmHistoryInfoResp` 拍平后的瘦字段，切 v2 后改读 `CesAlarmHistory` 的对应（更全）字段；保证编译通过、关联逻辑行为不回归。

### 错误码 → retryable

不改既有语义，沿用各 adapter 经 `HuaweiCloudInvocation` 统一通道（retry 名 `huaweicloud-retryable`）的映射：

| 上游 | SDK 异常 | ErrorCode | retryable | 重试 |
|---|---|---|---|---|
| Service 校验失败 | — | `INVALID_PARAM` | false | 不重试 |
| 429 | `ClientRequestException` | `UPSTREAM_THROTTLED` | true | 3 次指数退避 |
| 401 / 403 | `ClientRequestException` | `UPSTREAM_AUTH_FAILED` | false | 不重试 |
| 5xx | `ServerResponseException` | `UPSTREAM_ERROR` | true | 3 次指数退避 |
| Timeout | `RequestTimeoutException` | `TIMEOUT` | true | 3 次指数退避 |
| 未分类 | — | `INTERNAL` | false | 不重试 |

失败响应透传 `upstreamTraceId`（华为云 `X-Request-Id`，可空）。本变更**不动**该映射，仅记录以明确无损化未触碰错误语义。

### 限流 / 重试 / 超时 / 可观测（不变，仅记录）

- 限流域：各服务沿用既有 `ces-readonly` / `aom-readonly` / `apm-readonly` / `lts-readonly`（均 10 QPS：`limit-for-period: 10` / `limit-refresh-period: 1s` / `timeout-duration: 0`）。alarm history 切 v2 后仍用 `ces-readonly`。
- 重试域：`huaweicloud-retryable`，仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做 3 次指数退避（约 200ms / 800ms / 3.2s）。
- 超时：复用 `HuaweiCloudClientFactory.defaultHttpConfig()` 传输层超时（10s）。
- 可观测：`mcp_tool_invocation{tool="..."}` + adapter INFO 日志（入参摘要 + 耗时 + upstream trace id），API 标识如 `ces.listAlarmHistories`。

### 时间参数格式（跨服务不一致，AI 高频出错点，重点）

各华为云服务的时间参数格式**互不相同**，迁移 / 复制代码时极易引入隐性 bug，集中说明：

- **CES alarm history（v2）顶层时间**：`AlarmHistoryItemV2` 的 begin/end/firstAlarm/lastAlarm/alarmRecovery 是 **`OffsetDateTime`（ISO8601 带时区偏移，如 `2024-02-11T05:48:08+08:00`）**；但同一响应内 `DataPointInfo.time` 是 **`String`**（不要统一臆测成 Long）。
- **CES alarm history（v1，请求侧旧链路）**：`ListAlarmHistoriesRequest` 的 `from` / `to` / `start` / `limit` 在 SDK 里全是 **`String`**，需 `String.valueOf(...)`；`from` / `to` 语义为毫秒时间戳字符串。
- **APM `ListAlarmData`**：`AlarmDataVO` 的 `alarm_first_time` / `alarm_last_time` / `gmt_create` / `gmt_modify` 是 **`String`（上游未固定格式）**，原样透传，不本地解析。
- **LTS（`ListLogs` / `ListLogContext`）**：SDK 侧 `start_time` / `end_time` 是 **`String`，语义为 UTC 毫秒**（毫秒数的字符串形式，非 ISO8601）；adapter 对外 DTO 暴露为 **`Long`（UTC 毫秒）**，转 SDK 用 `String.valueOf(longValue)`。游标 `__time__` 在 SDK Java 字段名只叫 `time`（`getTime()` / `setTime()`），DTO 命名 `cursorTime`（String），与 `lineNum` 配对做行号游标分页。
- **AOM（`ListSample` / metric-data）**：时间窗常用 `startMillis` / `endMillis` / `durationMinutes` 三件套（毫秒区间 + 分钟粒度），与上面三种又不同。

> 一句话：**CES v2 顶层 = OffsetDateTime**（但其 data_points.time = String）；**APM = String 不定格式**；**LTS = "UTC 毫秒字符串"（对外 Long）**；**AOM = startMillis / endMillis / durationMinutes**。四套格式互不相同，照搬必错。

### AI 易错点（沉淀自任务卡）

1. **禁止猜 SDK 类名 / 字段 / 类型**——一律 sparse-checkout 真实 model 类读。`AlarmHistoryInfoResp` 是 v1；v2 是 `AlarmHistoryItemV2`。
2. **时间类型**：CES v2 顶层 `OffsetDateTime`，`DataPointInfo.time` 是 String；LTS 是 "UTC 毫秒字符串"；APM 是不定格式 String——别统一臆测成 Long。
3. **枚举取 `.getValue()`**：level / status / type / mask_status / actions.type 是 SDK 枚举，不是裸 String / Integer。
4. **无损 ≠ 透传 SDK 类**：仍要转成我们的 record（§4.1 第一铁律），只是字段不丢。
5. **响应加字段向后兼容**：放心补；但**不改已有字段名**（改名破 Agent 契约）。
6. **审计克制**：本就无损的只标注不重构；不碰 request / 分页 / 限流 / 工具名。
7. **任务卡与真实 SDK 冲突时停下来问，不要猜**（CLAUDE.md §5.1）。

## Risks / Trade-offs

- **时间格式四服务不一致**：CES（OffsetDateTime + data_points.time String）/ APM（String 不定）/ LTS（UTC 毫秒字符串，对外 Long）/ AOM（startMillis / endMillis / durationMinutes）各异，跨服务复制代码极易引入隐性 bug。缓解：转换集中在各 adapter 的 `toDto*` / `toSdk*` 一处，DTO 层统一对外语义。
- **v1 → v2 切换的行为漂移**：alarm history 数据源从 v1 换到 v2，字段更全且类型不同（v1 瘦字段 vs v2 OffsetDateTime + 嵌套）。缓解：`CorrelateIncidentService` 引用同步调整 + 契约测试 + 「只增不改名」约束，保证 Agent 向后兼容、关联逻辑不回归。
- **审计「补齐 vs 标注 OK」的判断主观性**：以 SDK 源码 model 类逐字段比对为唯一裁决依据，findings 入 `docs/audit/dto-losslessness-audit.md` 留痕；本就无损的不重构，控制改动面。
- **SDK 跨小版本字段稳定性**：CES / AOM / APM / LTS 共用根 pom `huaweicloud-sdk.version`；sparse-checkout 复核用的 master 与编译用版本字段一致为人工核对结论，后续升级需重核关键 model 类。
- **响应弱类型字段**：如 LTS `analysisLogs`（`List<Object>`）等 SDK 故意保留的弱类型字段，adapter 保持透传不强类型化，trade-off 选通用性。
- **遗留项**（本期未交付，列入 tasks.md）：除契约测试外的 Tool / Service / Adapter UT、冒烟脚本、Micrometer 看板、README 示例、结构化日志查询等。
