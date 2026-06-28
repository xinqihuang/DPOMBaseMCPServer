## Context

存量工具回填。原始任务卡：`docs/tasks/T26-aom-enum-and-cache.md`（状态 Done）；相关工具 spec：`docs/specs/tools/list_aom_metrics_v0.2.md`、`docs/specs/tools/query_aom_metric_data.md`、`docs/specs/tools/query_logs.md`（均 Approved）；依赖 T24（模式与基建：受控枚举进 Schema、`DiscoveryCacheConfig`）；关联 ADR-004（§4.3 受控枚举 vs 运行时发现分轴解法、严格档 vs 宽容目录档）。

本文承载 OpenSpec 主 spec 放不下的重契约：四个枚举设计、请求 DTO 枚举化与 SDK 还原映射、SDK 类/方法/版本、字段映射表、**三类不一致的时间参数格式**（AOM `time_range` 字符串 `startMs.endMs.durationMin` / `query_logs` 的 UTC 毫秒 / 与 CES 的 period 类型差异）、错误码→retryable、缓存设计、限流/重试/超时/可观测、以及 AI 实现时的易错点。

> 关键说明：本变更属 infra 层，**不引入也不修改任何 capability spec**——故 `changes/t26-aom-enum-and-cache/specs/` 目录为空。枚举只动请求侧，响应契约不变。

## Goals / Non-Goals

**Goals:**
- 把四个封闭集参数（`statistics` / `period` / `fill_value` / `category`，§4.3(a) 严格档）收敛为请求侧受控枚举，使合法取值经 Spring AI 反射进工具 JSON Schema，非法值由框架更早拒绝。
- 枚举 → SDK 字面量的还原（`.getValue()` / `.getSeconds()`）集中在 adapter 的 SDK 映射处，不在 service 层散落 `ALLOWED_*` Set 校验。
- 收紧工具描述，固化「选 namespace → `list_aom_metrics` 发现真实 metric_name/维度名 → 查数」的 Agent 自编排发现链。
- 为 `list_aom_metrics` 加发现缓存（TTL 1h），降低重复发现成本。

**Non-Goals:**
- ❌ 不枚举 `namespace`（自定义命名空间合法，维持 String + `AomPatterns` 模式校验——与 T24 的关键差异，不得照搬）。
- ❌ 不做 RDS 式双 namespace 处理（AOM 无 namespace 分裂问题，不写 T24 那样的探测指引）。
- ❌ 不枚举 `inventory_id`（`resType_resId` 复合字符串，枚举化收益低）。
- ❌ 不改响应 DTO（§4.1 无损）；不动 `time_range` 格式与 `query_logs` 的 keyword 语法（格式约束非词表，现有 Pattern 已覆盖）。
- ❌ 不重建 `list_aom_metrics`（只加缓存 + 描述）。

## Decisions

### 四个枚举设计（aom dto 包）

参照 `CesMetricFilter` / `CesNamespace` 写法，每值携带 API 字面量 + 中文 Javadoc：

```
AomMetricStatistic  MAXIMUM("maximum") / MINIMUM("minimum") / SUM("sum")
                    / AVERAGE("average") / SAMPLE_COUNT("sampleCount")
AomMetricPeriod     SEC_60(60) / SEC_300(300) / SEC_900(900) / SEC_3600(3600)   ← @JsonValue int
AomFillValue        MINUS_ONE("-1") / ZERO("0") / NULL_FILL("null") / AVERAGE("average")
AomLogCategory      APP_LOG("app_log") / NODE_LOG("node_log") / CUSTOM_LOG("custom_log")
```

- `fromValue` 沿用 T24 约定：**严格拒绝未知值**（抛 `IllegalArgumentException`），同时接受 API 字面量（如 `maximum` / `app_log`）与枚举常量名（如 `MAXIMUM` / `APP_LOG`）两种写法（防 Schema 渲染形式与反序列化规则不一致）。
- `AomMetricPeriod` 以 `@JsonValue int` + `@JsonCreator fromSeconds(int)` 接受数字 JSON，Schema 渲染为整数枚举；提供 `.getSeconds()` 还原。其余三枚举提供 `.getValue()` 还原字面量。

### 请求 DTO 枚举化与 service 层删 Set

- `AomQueryMetricDataRequest`：`statistics` → `List<AomMetricStatistic>`、`period` → `AomMetricPeriod`、`fillValue` → `AomFillValue`。
- `AomQueryLogsRequest`：`category` → `AomLogCategory`。
- 这四个走 ADR-004 严格档，DTO 直接持有 enum（与 namespace 宽容档不同）。adapter 在 SDK 映射处经 `.getValue()` / `.getSeconds()` 还原字面量。
- service 层删除 `ALLOWED_STATISTICS` / `ALLOWED_PERIODS` / `ALLOWED_FILL_VALUES`（`AomMetricDataService`）与 `ALLOWED_CATEGORIES`（`AomLogService`）——被类型系统取代；`null` 必填校验、`MAX_DIMENSIONS`、分页范围等其余规则保留。
- `statistics` 为空列表 / `null` 时是可选参数，保持现状语义（SDK 侧默认），不强制必填。

### SDK 映射

- **共用 SDK 类**：`com.huaweicloud.sdk.aom.v2.AomClient`。
- `query_aom_metric_data` → `listSample(ListSampleRequest)`（v3.1.177，POST，body=`QuerySampleParam`）。
- `query_logs` → `listLogItems(ListLogItemsRequest)`（v3.1.177，固定 `withType("querylogs")`）。
- `list_aom_metrics` → `listMetricItems(ListMetricItemsRequest)`（v3.1.196，POST，注意 CES list 是 GET）。
- 字段缺失先怀疑 SDK 版本，以 `CLAUDE.md §1` 实际部署版本为准。

**`query_aom_metric_data` 封闭集字段映射（枚举 → SDK）**：

| MCP 输入 | SDK 位置 | SDK 类型 | 还原 |
|---|---|---|---|
| `statistics` | `body.setStatistics(List<String>)`（`QuerySampleParam` 顶层，非 sample 内） | `List<String>` | `.stream().map(AomMetricStatistic::getValue)` |
| `period` | `body.withPeriod(Integer)`（**直接传秒数 Integer**，与 CES 不同） | Integer | `period.getSeconds()` |
| `fillValue` | `request.setFillValue(String)`（位于 `ListSampleRequest` **顶层，非 body**） | String | `fillValue.getValue()` |
| `namespace` | `body.samples[0].withNamespace(String)`（不枚举，保持 String） | String | 原样 |

**`query_logs` 封闭集字段映射**：

| MCP 输入 | SDK 位置 | 还原 |
|---|---|---|
| `category` | `body.withCategory(String)` | `category.getValue()` |

### 时间参数格式（三套不一致，最易错）

- `query_aom_metric_data` 的 `time_range`：**AOM 特有字符串** `startMillis.endMillis.durationMinutes`，正则 `^(-1|\d{1,16})\.(-1|\d{1,16})\.\d{1,7}$`，`-1` 是占位符由上游计算（如 `-1.-1.60` = 最近 60 分钟）。**不是数字毫秒区间**，原样透传不做本地解析。本卡不动其格式。
- `query_logs` 的 `startTime` / `endTime`：**UTC 毫秒 Long**，要求 `endTime` 严格大于 `startTime`。
- `period`：AOM `QuerySampleParam` 上是 **Integer 秒数直传**（`AomMetricPeriod.getSeconds()`）；对比 CES `ShowMetricData` 是 `PeriodEnum.fromValue(int)`、CES `BatchListMetricData` 又是 `PeriodEnum.fromValue(String)`——三种 API 三种类型，写映射时不要凭印象，也**不能复用 CES 枚举**（CES period 取值 `1/60/300/...` ≠ AOM 的 `60/300/900/3600`，且不得混进 ces 包）。

### 错误码 → retryable 映射（三工具一致）

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败（含框架枚举反序列化失败、null 必填） | INVALID_PARAM | false |
| HTTP 429 / SDK throttling | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

失败响应统一带 `error_code` / `error_message` / `upstream_trace_id`（华为云 `X-Request-Id`，可空）/ `retryable`。

### 缓存设计（`list_aom_metrics`）

- 复用 T24 的 `DiscoveryCacheConfig`，新增 cache 名 `aom-list-metrics`，与 CES 缓存相互独立。
- Caffeine TTL 缓存，**TTL 默认 1h**（不照抄 CES 的 1d：AOM 指标清单含容器/进程级对象，churn 明显快于 CES 云服务目录，1d 会服务陈旧清单），最大条数可配。
- 缓存 key = 整个 `AomListMetricsRequest` 请求 record（紧凑构造器已归一化 limit/start 默认值，值语义 equals 覆盖全部参数，含 `dimensions` List；不自己拼字符串 key）。
- **失败 / 空结果不写缓存**（`unless` 同时判 `null` 与 `isEmpty`）。
- 留 `@CacheEvict` 整体失效口。
- 配置项落 `application.yml`（`aom.discovery-cache.ttl` / `aom.discovery-cache.maximum-size`）。

### 非功能要求（三工具共用，本变更不改语义）

- 限流：复用既有 AOM 只读限流域，不新增限流域。
- 重试：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，最多 3 次指数退避。
- 超时：单次 SDK 调用 10s。
- 可观测：Micrometer `mcp_tool_invocation{tool="...", result="success|error", error_code="..."}`；INFO 日志含入参摘要 + 耗时 + upstream trace id。
- AOM 调用需 `projectId`（凭证里，环境变量 `HUAWEICLOUD_PROJECT_ID`），缺失会启动失败——CES 无此要求。

## Risks / Trade-offs

- **namespace 不枚举是本卡与 T24 的核心差异**：自定义命名空间是合法输入，若误枚举会废掉自定义指标查询。namespace 维持 String + `AomPatterns` 模式校验（宽容目录档），既有 UT 保持绿。
- **字面量照抄不"修正"**：`sampleCount` 是驼峰；`fill_value` 的 `"null"` 是**字面量字符串**不是 Java `null`；枚举 value 照抄。
- **AomMetricPeriod 不能复用 CES 枚举**：取值集不同（60/300/900/3600 vs CES 的 1/60/300/...），也不得混进 ces 包。period 走 `@JsonCreator fromSeconds(int)` 接受数字 JSON、Schema 渲染整数枚举。
- **枚举只动请求侧**：响应 DTO 不动（§4.1 无损），否则破坏既有契约测试。
- **缓存 TTL 1h 而非 1d**：用时效性换 churn 适配；失败/空不缓存保证探测的存在性语义正确、错误不被放大。
- **删 `ALLOWED_*` Set 时保留其余规则**：null 必填校验、`MAX_DIMENSIONS`、分页范围等不能一并删。
- **AOM 响应 `errorCode = SVCSTG_AMS_2000000` 是历史"成功"码**：HTTP 200 时 adapter 不要当业务错误。
- **与真实 SDK 冲突时停下来问**（`CLAUDE.md §5.1`），不臆造字段/方法。
