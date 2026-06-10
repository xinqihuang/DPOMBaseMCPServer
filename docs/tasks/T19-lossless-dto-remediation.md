# T19 — DTO 无损化治理（全 adapter）+ alarm history 切 v2

> 状态: **Ready** · 估时: 2.5d（拆多 PR）· 依赖: T04–T18 全部已交付 adapter · 关联: 修订 `CLAUDE.md §4.1`

## 背景（为什么有这张卡）

仓库里**所有 response DTO 系统性偏薄、丢字段**，不止 alarm history。两个叠加根因：

1. **约定被误读**：`CLAUDE.md §4.1`「自定义 DTO 包裹 SDK」本意是「稳定契约 + 不泄漏 SDK 类型」，但理由写成「字段命名长、嵌套深」，被 AI 读成「做最小子集」。多个 DTO 的 Javadoc 明文写了「最小信息集」。对诊断 Agent 最关键的信号（触发值、阈值规则、维度、record_id、各时间戳、mask_status 等）被系统性砍掉。
2. **打错 API 版本**：alarm history 链路用 CES **v1**（`AlarmHistoryInfoResp`），但目标字段只存在于 **v2**（`AlarmHistoryItemV2`）。

**本期决策（已拍板）**：
- ✅ alarm history 切 CES **v2**
- ✅ 所有 DTO **原汁原味、无损覆盖 SDK 字段**，不做最小子集（动作配置类字段也保留）
- ✅ **不止改 alarm history，全 adapter 的 response DTO 一起治理**

**权威 schema 来源（重要，替代 console API Explorer）**：console 的 API Explorer 需登录且是 SPA，抓不到。**一律以 SDK 源码 model 类为准**——sparse-checkout `huaweicloud/huaweicloud-sdk-java-v3` 对应 `services/<svc>/src/main/java/com/huaweicloud/sdk/<svc>/<ver>/model/` 下的真实类，按其 `@JsonProperty` + 字段类型逐字段映射。**禁止凭记忆猜类名/字段/类型**。公开文档站 `support.huaweicloud.com/api-*` 仅作语义参考。

## 范围

**做**:

1. **改 `CLAUDE.md §4.1`**：把「最小子集」语义改成「无损投影 + 钉死 API 版本 + SDK 源码为权威 + 契约测试兜底」（全文见下「§4.1 替换文本」，照贴）。
2. **alarm history 切 v2 + 无损化（样板，字段已查实，见下「样板：CesAlarmHistory v2 映射」）**。
3. **全 response DTO 审计 + 无损化**：对下「审计与处置表」中**每一个** response DTO，sparse-checkout 其对应 SDK 类，逐字段比对，**确认有损的全部补齐**（拆嵌套子 record、补字段、改类型），本就无损的标注 OK 不动。
4. **每个被改的 DTO 配契约测试**：真实 SDK 响应样本落 `test/resources/sdk-samples/<svc>/`，反序列化经 adapter 映射，断言 DTO 覆盖样本全字段，漂移即 fail。

**不做**（防蔓延）:
- ❌ 不改 request DTO 设计、不改任何 `@Tool(name=...)` 契约名、不加新 tool、不动分页/限流/错误码语义
- ❌ 不改已有响应字段名（只增不改名，保证 Agent 向后兼容）
- ❌ 本就无损的 DTO 只标注不重构
- ❌ UT/冒烟脚本不在本卡（除契约测试外），列遗留项

## 执行方式（拆 PR，避免一锅炖 review 不动）

- **PR-0**：仅改 `CLAUDE.md §4.1`（根因，先合）
- **PR-1**：CES alarm history 切 v2 + 无损 `CesAlarmHistory` + 契约测试 + 修 `CorrelateIncidentService` 引用（样板，小而完整）
- **PR-2**：CES 其余（metrics / metric-data / batch / notification-mask）
- **PR-3**：APM（span / topology / traces）
- **PR-4**：AOM（logs / metrics / sample）
- **PR-5**：LTS（logs / log-context）
- 每个 PR 自带对应契约测试；PR-2~5 各附 findings 表片段到 `docs/audit/dto-losslessness-audit.md`

## 审计与处置表（DTO → 必须读的真实 SDK 类）

> 下表 SDK 类名已用 sparse-checkout 在 `huaweicloud-sdk-java-v3` master 上**核实存在**。Claude Code 执行时拉对应 model 目录、按真实 `@JsonProperty` 无损映射。

| 自定义 DTO | adapter | 权威 SDK 类（读这个） | 处置 |
|---|---|---|---|
| `CesAlarmHistory` / `CesListAlarmsResponse` | CES | **切 v2** `ces.v2.model.ListAlarmHistoriesResponse` → `AlarmHistoryItemV2`(+ `...Metric`/`...MetricDimensions`/`...Condition`/`AdditionalInfo`/`DataPointInfo`/`...AlarmActions`) | 切版本 + 无损重画（PR-1） |
| `CesListMetricsResponse` | CES | `ces.v1.model.ListMetricsResponse` → `MetricInfoList`(+ `MetricsDimension`) | 审计补齐 |
| `CesQueryMetricDataResponse` | CES | `ces.v1.model.ShowMetricDataResponse` → `Datapoint` | 审计补齐（unit/聚合是否全） |
| `CesBatchQueryMetricDataResponse` | CES | `ces.v1.model.BatchListMetricDataResponse` → `BatchMetricData` / `DatapointForBatchMetric` | 审计补齐 |
| `CesListNotificationMasksResponse` | CES | `ces.v2.model.ListNotificationMasksResponse` → `ListNotificationMaskRespNotificationMasks`(+ `Resource`/`ResourceDimension`/`ProductMetric`/`MaskType`) | 审计补齐 |
| `ApmSpan` / `ApmQueryTracesResponse` | APM | `apm.v1.model.ShowSpanSearchResponse` → `ClientSpanInfo` | 审计补齐（memory 已知偏薄，重点） |
| `ApmTopologyNode`/`ApmTopologyLine`/`ApmGetTopologyResponse` | APM | `apm.v1.model.ShowTopologyResponse` → `TraceTopologyNode` / `TraceTopologyLine` / `TraceTopologyLineInfo` | 审计补齐 |
| `AomQueryLogsResponse` | AOM | `aom.v2.model.ListLogItemsResponse` | 审计补齐 |
| `AomListMetricsResponse` 等 | AOM | `aom.v2.model.ListMetricItemsResponse` → `MetricItemResultAPI` | 审计补齐 |
| `AomSampleSeries`/`AomMetricDatapoint`/`AomStatisticValue` | AOM | `aom.v2.model.ListSampleResponse` → `QuerySample` / `SampleDataValue` / `MetricDataPoints` / `StatisticValue` | 审计补齐 |
| `LtsListLogsResponse` / `LtsLogEntry` | LTS | `lts.v2.model.ListLogsResponse` → `LogContents` | 审计补齐（labels/line_num/分析态） |
| `LtsListLogContextResponse` | LTS | `lts.v2.model.ListLogContextResponse` | 审计补齐 |

> findings 表每行：SDK 字段全集 / DTO 已覆盖 / **缺失项** / 处置（补齐 or OK+理由）。

## §4.1 替换文本（把 CLAUDE.md 现有 §4.1 整段替换为以下内容）

```markdown
### 4.1 自定义 DTO 无损包裹 SDK 类型（重要）

**绝不允许** 把华为云 SDK 的 Request/Response 类泄漏到 `adapter` 之外的层。每个 SDK 调用：
adapter 把入参 DTO（我们的 record）转 SDK Request → 调 SDK → 把 SDK Response 转我们的输出 DTO → 返回上层。

**DTO 是稳定契约，但必须对 SDK 响应做无损投影**：

- **无损**：SDK 响应里的字段，DTO 必须全部覆盖。允许重命名贴齐 snake_case、允许把嵌套拆成子 record，但不允许丢字段。
- **要砍字段必须在 spec 里显式列出并写理由**，否则一律保留。「Agent 当前用不到」不是理由——只读诊断工具默认全留。
- **嵌套不拍平丢信息**：嵌套对象拆子 record（如 metric/condition/data_points），不要拍平成几个标量后丢其余。
- **API 版本显式钉死**：每个工具对应的 SDK API 版本（v1/v2）写进 spec，不让实现自选。字段缺失先怀疑「打错版本」。
- **权威 schema = SDK 源码 model 类**：写 DTO 前必须 sparse-checkout `huaweicloud-sdk-java-v3` 对应 model 类，按真实 @JsonProperty + 字段类型映射。console API Explorer 抓不到（登录+SPA），仅作语义参考。禁止凭记忆猜字段/类名/类型。
- **类型贴齐 SDK**：时间用 SDK 的 OffsetDateTime（非 Long/String 臆测），SDK 枚举映射取 .getValue()。
- **契约测试兜底**：每个有响应 DTO 的工具必须有 *ContractTest——真实 SDK 样本（test/resources/sdk-samples/<svc>/）反序列化经 adapter 映射，断言覆盖全字段，漂移即 fail。

理由：DTO 提供稳定命名与防 SDK 泄漏的价值，但诊断 Agent 依赖完整信号；历史「最小子集」做法已造成系统性丢字段，本条予以纠正。
```

## 样板：`CesAlarmHistory` v2 映射（字段已查实，照此写）

切 `cesV2Client`（v2 `CesClient`）调 v2 `listAlarmHistories`。响应 `ListAlarmHistoriesResponse`：`getCount()`(Integer) + `getAlarmHistories()` → `List<AlarmHistoryItemV2>`。

**`AlarmHistoryItemV2` 真实字段（顶层，无损全收）**：

| SDK getter | 类型 | 契约字段 |
|---|---|---|
| getRecordId | String | record_id |
| getAlarmId | String | alarm_id |
| getName | String | name |
| getStatus | StatusEnum → `.getValue()` | status |
| getLevel | LevelEnum → `.getValue()` (Integer) | level |
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

- `AlarmHistoryItemV2Metric` → namespace(String), metricName(String), dimensions(List<…MetricDimensions>)
- `AlarmHistoryItemV2MetricDimensions` → name(String), value(String)
- `AlarmHistoryItemV2Condition` → period(Integer), filter(String), comparisonOperator(String), value(Double), unit(String), count(Integer), suppressDuration(Integer)
- `AdditionalInfo` → resourceId(String), resourceName(String), eventId(String)
- `DataPointInfo` → time(**String**), value(Double)
- `AlarmHistoryItemV2AlarmActions` → type(枚举→`.getValue()`), notificationList(List<String>)

> 注意类型细节（猜会错）：顶层时间是 `OffsetDateTime`，但 `DataPointInfo.time` 是 `String`；condition.value 是 `Double`；level/status/type/maskStatus/actions.type 都是 SDK 枚举，取 `.getValue()`。

## 契约测试（防回归网，关键交付）

样板 `CesListAlarmHistoriesContractTest`：把本卡末 v2 JSON 落 `sdk-samples/ces/list-alarm-histories-v2-response.json` → 用 v2 SDK model 反序列化 → 经 adapter 映射 → 断言 DTO 覆盖样本全字段。**故意删 DTO 一个字段，该测试必须变红**（验证有效）。其余被改 DTO 同法各配一个。

## 验收标准

- [ ] `CLAUDE.md §4.1` 已替换为无损投影版本（PR-0）
- [ ] alarm history 走 v2，`CesAlarmHistory` 无损覆盖 `AlarmHistoryItemV2` 全字段含嵌套；`CorrelateIncidentService` 编译通过、行为不回归（PR-1）
- [ ] 审计与处置表中**每个 DTO** 都在 `docs/audit/dto-losslessness-audit.md` 有结论；确认有损的全部补齐（PR-2~5）
- [ ] 每个被改 DTO 有 `*ContractTest`；任删一字段能让对应测试变红
- [ ] 全模块 `mvn -q test` 通过；Checkstyle 0；依赖方向不破（mcp 不 import SDK；monitoring 只经 adapter 接口）
- [ ] 无任何已有响应字段被改名（只增）

## AI 易错点提醒

1. **禁止猜 SDK 类名/字段/类型**——一律 sparse-checkout 真实 model 类读。`AlarmHistoryInfoResp` 是 v1；v2 是 `AlarmHistoryItemV2`。
2. **时间类型**：顶层 `OffsetDateTime`，`DataPointInfo.time` 是 String，别统一臆测成 Long。
3. **枚举取 `.getValue()`**：level/status/type/mask_status/actions.type 是 SDK 枚举，不是裸 String/Integer。
4. **无损 ≠ 透传 SDK 类**：仍要转成我们的 record（§4.1 第一铁律），只是字段不丢。
5. **响应加字段向后兼容**：放心补；但**不改已有字段名**（改名破 Agent 契约）。
6. **审计克制**：本就无损的只标注不重构；不碰 request/分页/限流/工具名。
7. 任务卡与真实 SDK 冲突时**停下来问，不要猜**（CLAUDE.md §5.1）。

## 完成后

按 PR 切分逐个提：`refactor(T19): lossless DTO remediation — <module>`。PR-1 标题：`refactor(T19): ListAlarmHistories v2 + lossless CesAlarmHistory + contract guard`。

---

## 附：CES v2 `ListAlarmHistories` 权威响应样本（落 sdk-samples + 契约测试基准）

```json
{
  "alarm_histories": [
    {
      "alarm_id": "al1604473987569z6n6nkpm1",
      "record_id": "ah251222T092004SAD2yARym",
      "name": "TC_CES_FunctionBaseline_Alarm_008",
      "metric": {
        "namespace": "SYS.VPC",
        "dimensions": [ { "name": "bandwidth_id", "value": "79a9cc0c-f626-4f15-bf99-a1f184107f88" } ],
        "metric_name": "downstream_bandwidth"
      },
      "condition": {
        "period": 1, "filter": "average", "comparison_operator": ">=",
        "value": 0, "unit": "", "count": 3, "suppress_duration": 3600
      },
      "level": 2,
      "type": "ALL_INSTANCE",
      "begin_time": "2024-02-11T05:48:08+08:00",
      "end_time": "2024-02-11T08:48:08+08:00",
      "first_alarm_time": "2024-02-11T06:48:08+08:00",
      "last_alarm_time": "2024-02-11T08:48:08+08:00",
      "alarm_recovery_time": "2024-02-11T08:48:08+08:00",
      "action_enabled": false,
      "alarm_actions": [],
      "ok_actions": [],
      "status": "alarm",
      "data_points": [
        { "time": "2022-06-22T16:38:02+08:00", "value": 873.1507798960139 },
        { "time": "2022-06-22T16:28:02+08:00", "value": 883.1507798960139 },
        { "time": "2022-06-22T16:18:02+08:00", "value": 873.4 }
      ],
      "additional_info": { "resource_id": "", "resource_name": "", "event_id": "" },
      "mask_status": "UN_MASKED"
    }
  ],
  "count": 103
}
```
