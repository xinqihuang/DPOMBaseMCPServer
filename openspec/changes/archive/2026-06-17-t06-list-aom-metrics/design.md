## Context

存量工具回填。源 spec：`docs/specs/tools/list_aom_metrics_v0.2.md`（v0.2, Approved，含业务方评审决议）；源任务卡：`docs/tasks/T06-list-aom-metrics.md`。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类/方法/版本、字段映射表、两条调用路径、错误码映射、非功能要求、与 CES 的关键差异及 AI 易错点。

`list_aom_metrics` 与 `list_ces_metrics` 对称但**不能简单复制 CES adapter**——AOM 与 CES 是两套独立 SDK 包，类名相同含义不同。

## Goals / Non-Goals

**Goals:**
- 无损暴露 AOM `ListMetricItems` 的指标发现能力（namespace / metric_name / dimensions / inventoryId 过滤 + 分页）。
- 返回结构化指标元数据（含 AOM 特有 `dimension_value_hash`），SDK 类型不穿透 adapter。
- 作为 `query_aom_metric_data` 的前置发现步骤，与 CES `list_ces_metrics` 形成应用层/基础设施层互补。

**Non-Goals:**
- 不查指标值（时序数据点）——由 `query_aom_metric_data` 负责。
- 不查日志 / 告警；不做跨命名空间合并、不做指标含义解释。
- 不做缓存、不做跨 projectId / 跨 region 查询、不做客户端聚合 / 排序。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.aom.v2.AomClient`
- **SDK 方法**：`listMetricItems(ListMetricItemsRequest)` → `ListMetricItemsResponse`
- **SDK 版本**：v3.1.196（钉死；字段缺失先怀疑版本）
- **HTTP**：POST `/v2/{project_id}/ams/metrics`（与 CES list 的 GET 不同；SDK 内部拼装 body，写 mock 测试时需明确）
- 相关 SDK 类：`ListMetricItemsRequest` / `ListMetricItemsResponse` / `MetricAPIQueryItemParam` / `QueryMetricItemOptionParam`（含 `NamespaceEnum` 嵌套类）/ `Dimension`（`com.huaweicloud.sdk.aom.v2.model.Dimension`，**非** CES 的 `MetricsDimension`）/ `MetricItemResultAPI` / `MetaDataSeries`。

**入参字段映射（MCP → SDK Request）**：

| MCP 输入 | SDK Request 位置 | SDK 类型 | 备注 |
|---|---|---|---|
| `limit` | `request.setLimit(String)` | **String** | 需 `String.valueOf(limit)` 转换 |
| `start` | `request.setStart(String)` | **String** | 同上 |
| `inventory_id` | `request.body.setInventoryId(String)` | String | body 字段，非 query |
| `namespace` | `metricItems[i].setNamespace(NamespaceEnum)` | **Enum** | `QueryMetricItemOptionParam.NamespaceEnum.fromValue(String)`；自定义命名空间 `fromValue` 也能 new 实例 |
| `metric_name` | `metricItems[i].setMetricName(String)` | String | 可选 |
| `dimensions` | `metricItems[i].setDimensions(List<Dimension>)` | List | aom.v2.model.Dimension |

**两条调用路径**（按有无 `inventory_id`）：
- 路径 A（仅 inventory_id）：`request.withType("inventory")`，body 仅设 `inventoryId`，**不设 metricItems**（AOM 文档：type=inventory 时用 inventoryId 替代 metricItems，互斥）。
- 路径 B（namespace + 可选 metric_name + 可选 dimensions）：body 仅设 `metricItems`，**不设 inventoryId、不设 type**。
- 决策：若 `inventory_id != null` 走路径 A（即使 namespace 也提供，inventory_id 优先并记 WARN 日志）。

**响应字段映射（SDK Response → DTO）**：

| MCP 输出 | SDK Response 字段 | SDK 类型 |
|---|---|---|
| `metrics[]` | `ListMetricItemsResponse.getMetrics()` | `List<MetricItemResultAPI>` |
| `metrics[].namespace` | `MetricItemResultAPI.getNamespace()` | String |
| `metrics[].metric_name` | `MetricItemResultAPI.getMetricName()` | String |
| `metrics[].unit` | `MetricItemResultAPI.getUnit()` | String |
| `metrics[].dimensions[]` | `MetricItemResultAPI.getDimensions()` | `List<Dimension>` |
| `metrics[].dimension_value_hash` | `MetricItemResultAPI.getDimensionvaluehash()` | String |
| `pagination.count` | `MetaDataSeries.getCount()` | Integer |
| `pagination.total` | `MetaDataSeries.getTotal()` | Integer |
| `pagination.offset` | `MetaDataSeries.getOffset()` | Integer |
| `pagination.next_token` | `MetaDataSeries.getNextToken()` | **Integer**（CES 是 String marker，关键差异） |
| `pagination.has_more` | 计算 `nextToken != null && count > 0` | — |

> DTO `AomPagination.nextToken` 是 `Integer`（非 CES 的 String）；`AomMetricInfo` 含 AOM 特有 `dimension_value_hash`，用于 Agent 跨调用关联同一维度组合。

### 时间参数

本工具**无时间窗参数**（仅命名空间 / 指标名 / 维度 / 资源 ID + 分页）。指标元数据变化慢（小时级），不涉及上游字符串 / ISO8601 / UTC毫秒 / startMillis 等时间格式约定。分页 `limit` / `start` 虽为业务侧 Integer，但 SDK 字段是 String，必须 `String.valueOf(...)` 转换（见上表）。

### 错误码映射

沿用 `agentic-common` 的 `SdkExceptionMapper`，按 HTTP status 分类，**无需为 AOM 单独写映射**：

| 上游情况 | error_code | retryable |
|---|---|---|
| 本地校验失败 | INVALID_PARAM | false |
| HTTP 429 | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| SDK timeout / connection | TIMEOUT | true |
| 其他 | INTERNAL | false |

失败响应携带 `upstream_trace_id`（华为云 `X-Request-Id`，可空）与 `retryable`。AOM 错误码以 `AOM.xxx` / `SVCSTG_AMS_xxx` 开头，与 CES 的 `CES.xxx` 不同，但映射按 HTTP status，故无需改 mapper。

### 配置与凭据

- 调用包装：`HuaweiCloudInvocation.execute("aom-readonly", "huaweicloud-retryable", "aom.listMetricItems", ...)`。
- 限流：RateLimiter key `aom-readonly`，QPS=10（与 ces-readonly 对称，评审决议保持 10）。
- 重试：`huaweicloud-retryable`，3 次指数退避 200/800/3200ms。
- 超时：HTTP timeout 10s（与 CES 一致）。
- 凭据：`new BasicCredentials().withProjectId(projectId).withAk(ak).withSk(sk)`——**projectId 是 CES 没有的第三件套**，从 `HUAWEICLOUD_PROJECT_ID` 注入，Vault 兜底。region 用 `AomRegion.valueOf(properties.getRegion())`，endpoint `aom.cn-southwest-2.myhuaweicloud.com`（SRE 已确认 VPC 出口已放行 `*.cn-southwest-2.myhuaweicloud.com`）。
- 启动日志 `LOG.info("AomClient initialized, region={}, projectId={}", ...)`，**不打 ak/sk**。
- `application.yml` 的 `huaweicloud.project-id: ${HUAWEICLOUD_PROJECT_ID:}` 保留默认空串，避免启动期解析失败。

### 可观测

INFO 日志含 API 标识 `aom.listMetricItems`、入参摘要（namespace / inventory_id / limit）、耗时、upstream_trace_id；失败 WARN 含 errorCode 与 upstream_trace_id；Prometheus 指标经 Resilience4j-micrometer 自动暴露。

### Health Indicator 兼容 CES 的策略

`HuaweiCloudCredentialsHealthIndicator` 加 projectId 缺失检查时，CES 不需要 projectId。当前策略：projectId 缺失只报 DOWN+detail，**不阻断**；CES 工作流不查 projectId（reading 操作）仍可工作。该兼容策略需在 health indicator 的 javadoc 里说明。

## Risks / Trade-offs

- **AI 抄 CES 实现易踩坑**：AOM/CES 两套独立 SDK，类名相同含义不同——`Dimension`(aom) vs `MetricsDimension`(ces)、`MetaDataSeries`(aom) vs `MetaData`(ces)；`limit`/`start` 是 String 非 Integer；namespace 是 `NamespaceEnum` 非 String；POST+body 非 GET+query；需 projectId 三件套；`next_token` 是 Integer 非 String marker。须独立实现 adapter。
- **inventory_id 与 metricItems 互斥**：inventory_id 模式下 `metricItems` 必须为 null（不传），**不能传空 list**——AOM API 会拒绝空 list / 空 dimensions=[]，namespace-only 下 dimensions 应为 null 而非 []。
- **AOM 成功码遗留**：HTTP 200 时 body 也可能含 `errorCode: SVCSTG_AMS_2000000`（历史遗留成功码），adapter 层**不要**当作业务错误。
- **自定义命名空间**：评审决议 5 个预定义全开 + 自定义命名空间，校验仅做格式正则不做白名单（`^(PAAS\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_.]{2,63})$`），`NamespaceEnum.fromValue` 对未知值会 new 实例。
- **遗留**：MCP `annotations`（readOnlyHint 等）在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅语义意图。冒烟脚本在无应用接入 AOM 的测试集群第一个用例返回空 metrics 不算失败（断言 `count >= 0`）。
