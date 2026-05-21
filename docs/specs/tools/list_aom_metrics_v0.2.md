# Spec: list_aom_metrics

> 状态: **Approved（v0.2，含业务方评审决议）**  ·  版本: v0.2  ·  所属服务: DPOMBaseMCPServer  ·  对称 spec: [list_ces_metrics](./list_ces_metrics.md)

## 1. 意图与场景

让运维 Agent 能够发现一个 AOM（Application Operations Management，应用运维管理）资源/组件下"哪些指标可以查"，作为后续拉取指标数据、定位异常、关联告警的前置步骤。

**典型场景**:

- **场景 A（容器/应用级排障）**: Agent 收到"appName=order-svc 在 PAAS.CONTAINER 命名空间下最近异常"的线索，需要先知道 order-svc 这个应用暴露了哪些指标，再选取相关指标拉取时序数据。
- **场景 B（节点级容量分析）**: Agent 要分析某集群节点资源水位，先用 `namespace=PAAS.NODE` + `clusterName` 维度查所有可用指标，再决定看 CPU、内存、磁盘还是网络。
- **场景 C（按资源 ID 反查）**: Agent 已知一个 `inventoryId`（资源清单 ID，格式 `resType_resId`），需要列出这个资源全部相关的指标。

**定位**:
- 与 `list_ces_metrics` **对称但不互替**——CES 是基础设施层指标（ECS、RDS、EVS 等云资源），AOM 是应用运维层指标（应用、组件、容器、进程等）。运维 Agent 通常需要两者结合。
- 是 `query_aom_metric_data`（T08，未来任务）的前置步骤。
- 与 CES `list_ces_metrics` 的关键差异见 §4，**实现时不能简单复制 CES adapter**。

## 2. 范围边界

**做**:

- 调用 AOM v2 `ListMetricItems` API，按命名空间 / 指标名 / 维度 / 资源 ID 任意组合过滤
- 支持分页（基于 `start` offset + `limit` 页大小）
- 返回结构化 metric 列表 + 分页元信息
- 输入校验（在调用 SDK 之前过滤明显违规请求）
- 限流、重试、异常映射，沿用 `agentic-common` 现有基础设施

**不做**（划清边界）:

- ❌ **不查指标值**：只列出"哪些指标可查"，不拉时序数据点。值查询是另一个 tool（`query_aom_metric_data`）。
- ❌ **不查日志、不查告警**：本 tool 只覆盖指标元数据查询。
- ❌ **不做跨命名空间合并、不做指标"含义解释"**：Agent 自己根据 metricName 判断业务含义。
- ❌ **不做缓存**：每次调用都打 AOM API（MVP 阶段；后续视真实 QPS 决定是否引入）。
- ❌ **不暴露 SDK 类型**：所有公开 DTO 都自定义 record，不让 `MetricItemResultAPI` 等 SDK 类穿过 adapter 层。

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- **name**: `list_aom_metrics`
- **description**（Agent 看到的，须精准）:

  > List available AOM (Application Operations Management) metric definitions for an application, component, container, process, or other monitored object in Huawei Cloud. Use this BEFORE query_aom_metric_data to discover which metric names exist for a given namespace (PAAS.CONTAINER / PAAS.NODE / PAAS.SLA / PAAS.AGGR / CUSTOMMETRICS) or a specific inventoryId. AOM covers application-layer metrics; for cloud infrastructure metrics (ECS / RDS / EVS etc.) use list_ces_metrics instead. Returns metric metadata (namespace, metric_name, dimensions, unit) — NOT actual data points.

- **annotations**:
  - `readOnlyHint=true`
  - `destructiveHint=false`
  - `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `namespace` | string | 条件必填(见下) | — | AOM 命名空间。**枚举值**: `PAAS.CONTAINER` / `PAAS.NODE` / `PAAS.SLA` / `PAAS.AGGR` / `CUSTOMMETRICS`，或自定义命名空间。 |
| `metric_name` | string | 否 | — | 指标名精确匹配，如 `aom_process_cpu_usage`。长度 1-255。 |
| `dimensions` | array of `{name, value}` | 否 | — | 维度过滤列表，**所有维度 AND 关系**。如 `[{name:"appName",value:"aomApp"}]`。 |
| `inventory_id` | string | 条件必填(见下) | — | 资源清单 ID，格式 `resType_resId`，`resType` 枚举: `host / application / instance / container / process / network / storage / volume`。 |
| `limit` | integer | 否 | 100 | 页大小，范围 [1, 1000]。 |
| `start` | integer | 否 | 0 | 分页起始 offset，非负整数。 |

**输入校验规则**（在 service 层做，违反抛 `InvalidParamException`）:

1. **`namespace` 或 `inventory_id` 至少提供一个**（不能两者都空——AOM API 会拒绝空 body）
2. 如果同时提供 `namespace` 和 `inventory_id`，**`inventory_id` 优先**（AOM API 行为如此），但工具层不抛错，记 WARN 日志
3. `limit` ∈ [1, 1000]
4. `start` ≥ 0
5. 如果提供 `dimensions`，每个元素的 `name` 和 `value` 都必填且非空
6. `namespace` 如果是预定义值，正则: `^(PAAS\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_.]{2,63})$`
7. `inventory_id` 如果提供，正则: `^(host|application|instance|container|process|network|storage|volume)_[A-Za-z0-9-]+$`

### 3.3 输出契约（成功）

```json
{
  "metrics": [
    {
      "namespace": "PAAS.CONTAINER",
      "metric_name": "aom_process_cpu_usage",
      "unit": "Percent",
      "dimensions": [
        { "name": "appName", "value": "aomApp" }
      ],
      "dimension_value_hash": "a1b2c3d4..."
    }
  ],
  "pagination": {
    "count": 1,
    "total": 523,
    "offset": 100,
    "next_token": 100,
    "has_more": true
  }
}
```

字段说明:

- `metrics[].dimension_value_hash`: AOM 特有，由 SDK `dimensionvaluehash` 字段映射而来——同一组维度值的稳定哈希，**Agent 可用于跨调用关联同一指标维度组合**。
- `pagination.next_token`: AOM SDK 返回的是 Integer 类型的 offset。**这是与 CES 关键差异**（CES 用 String 类型 marker）。
- `pagination.has_more`: `next_token != null && count > 0` 时为 true。

### 3.4 输出契约（失败）

```json
{
  "error_code": "INVALID_PARAM | UPSTREAM_THROTTLED | UPSTREAM_AUTH_FAILED | UPSTREAM_ERROR | TIMEOUT | INTERNAL",
  "error_message": "...",
  "upstream_trace_id": "...",
  "retryable": true | false
}
```

错误码映射（沿用 `agentic-common` 的 `SdkExceptionMapper`，无需 tool 层自定义）:

| 上游情况 | error_code | retryable |
|---|---|---|
| 本地校验失败 | INVALID_PARAM | false |
| HTTP 429 | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| SDK timeout / connection | TIMEOUT | true |
| 其他 | INTERNAL | false |

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.aom.v2.AomClient`
- **SDK 方法**: `listMetricItems(ListMetricItemsRequest request)` → `ListMetricItemsResponse`
- **SDK 版本**: v3.1.196
- **HTTP 方法**: POST（**注意：与 CES list 不同，CES 是 GET**）
- **API 路径**: `POST /v2/{project_id}/ams/metrics`（实际由 SDK 拼装）

**字段映射**:

| MCP 输入 | SDK Request 位置 | SDK 类型 | 备注 |
|---|---|---|---|
| `limit` | `request.setLimit(String)` | **String** (!) | SDK 字段是 String 类型，需 `String.valueOf(limit)` |
| `start` | `request.setStart(String)` | **String** (!) | 同上 |
| `inventory_id` | `request.body.setInventoryId(String)` | String | 注意是 body 字段，不是 query |
| `namespace` | `request.body.metricItems[i].setNamespace(NamespaceEnum)` | **Enum** (!) | 用 `QueryMetricItemOptionParam.NamespaceEnum.fromValue(String)` 转换 |
| `metric_name` | `request.body.metricItems[i].setMetricName(String)` | String | |
| `dimensions` | `request.body.metricItems[i].setDimensions(List<Dimension>)` | List | SDK 的 `com.huaweicloud.sdk.aom.v2.model.Dimension`（与 CES 的 `MetricsDimension` 不同包） |

**调用模式**（根据有无 `inventory_id` 分两种路径）:

```
case 1: 仅 inventory_id (URI 参数 type=inventory)
  ListMetricItemsRequest req = new ListMetricItemsRequest()
      .withType("inventory")
      .withLimit(String.valueOf(limit))
      .withStart(String.valueOf(start))
      .withBody(new MetricAPIQueryItemParam()
          .withInventoryId(inventoryId));

case 2: 按 namespace + dimensions 查询
  List<Dimension> dims = ...;
  QueryMetricItemOptionParam item = new QueryMetricItemOptionParam()
      .withNamespace(NamespaceEnum.fromValue(namespace))
      .withMetricName(metricName)        // optional
      .withDimensions(dims);              // optional
  ListMetricItemsRequest req = new ListMetricItemsRequest()
      .withLimit(String.valueOf(limit))
      .withStart(String.valueOf(start))
      .withBody(new MetricAPIQueryItemParam()
          .withMetricItems(List.of(item)));
```

**Response 字段映射**:

| MCP 输出 | SDK Response 字段 | SDK 类型 |
|---|---|---|
| `metrics[]` | `ListMetricItemsResponse.getMetrics()` | `List<MetricItemResultAPI>` |
| `metrics[].namespace` | `MetricItemResultAPI.getNamespace()` | String |
| `metrics[].metric_name` | `MetricItemResultAPI.getMetricName()` | String |
| `metrics[].unit` | `MetricItemResultAPI.getUnit()` | String |
| `metrics[].dimensions[]` | `MetricItemResultAPI.getDimensions()` | `List<Dimension>` (注意是 aom.v2.model.Dimension) |
| `metrics[].dimension_value_hash` | `MetricItemResultAPI.getDimensionvaluehash()` | String |
| `pagination.count` | `MetaDataSeries.getCount()` | Integer |
| `pagination.total` | `MetaDataSeries.getTotal()` | Integer |
| `pagination.offset` | `MetaDataSeries.getOffset()` | Integer |
| `pagination.next_token` | `MetaDataSeries.getNextToken()` | Integer (!) |
| `pagination.has_more` | 计算: `nextToken != null && count > 0` | — |

### AI 容易写错的点（实现时务必注意）

1. **不要复用 CES adapter 的代码**。AOM 和 CES 是两套独立的 SDK 包，类名相同含义不同（`Dimension` vs `MetricsDimension`，`MetaDataSeries` vs `MetaData`），需要独立的 adapter 实现。
2. **`limit` / `start` 是 String 不是 Integer**。在 SDK request 上用 `withLimit(String.valueOf(intValue))`，DTO 层再做类型转换。
3. **POST 请求，body 包含查询条件**。CES 是 GET + query string；AOM 是 POST + body，SDK 内部已经处理，但写测试 mock 时要清楚这点。
4. **namespace 是 Enum，不是 String**。用 `QueryMetricItemOptionParam.NamespaceEnum.fromValue(name)`。自定义命名空间也能通过 `fromValue` 创建（看 SDK 源码 `NamespaceEnum` 的 `fromValue` 实现，未知值会 new 一个 NamespaceEnum 实例）。
5. **`inventory_id` 模式下，`metricItems` 必须为空或不传**。AOM 文档明确："When type is inventory, this parameter instead of metricItems is used for associated metric queries"。两者互斥。
6. **AOM API 需要 `projectId`**。配置层要新增 `huaweicloud.project-id`（CES 不需要这个）；`BasicCredentials().withProjectId(projectId).withAk(ak).withSk(sk)`。
7. **错误码不同**。AOM 错误码以 `AOM.xxxxxx` 或 `SVCSTG_AMS_xxx` 开头，与 CES 的 `CES.xxxx` 不同。`SdkExceptionMapper` 按 HTTP status code 分类，已经兼容，不需为 AOM 单独写映射。
8. **响应可能含 `errorCode: SVCSTG_AMS_2000000`（成功码）**。这是 AOM 历史遗留——HTTP 200 + body 里有 errorCode 字段表示成功。adapter 层**不要**把这个当作业务错误。
9. **空 dimensions 数组 vs null**。SDK 在 namespace-only 查询下 `dimensions` 字段可以是 null（不传），但绝对不要传 `dimensions=[]`——AOM API 可能返回 4xx。

## 5. 非功能要求

- **限流**: RateLimiter key = `aom-readonly`，默认 QPS = 10（与 CES 对称；后续可在 `application.yml` 微调）
- **重试**: 沿用 `huaweicloud-retryable`，3 次指数退避 200/800/3200ms
- **超时**: HTTP timeout 10 秒（与 CES 一致）
- **可观测**:
  - INFO 日志输出 API 标识 `aom.listMetricItems`、入参摘要（namespace、inventory_id、limit）、耗时、upstream_trace_id
  - 失败时 WARN，含 errorCode 与 upstream_trace_id
  - Prometheus 指标自动通过 Resilience4j-micrometer 暴露
- **AK/SK + projectId**: 从环境变量 `HUAWEICLOUD_AK` / `HUAWEICLOUD_SK` / `HUAWEICLOUD_PROJECT_ID` 注入，Vault 兜底；启动时 `HuaweiCloudCredentialsHealthIndicator` 加上 `projectId` 检查
- **公网/内网**: AOM 端点 `aom.cn-southwest-2.myhuaweicloud.com`，需在 CCE 的 VPC 出口放行（与 CES 端点不同，需 SRE 确认 VPC 规则覆盖到 aom subdomain）

## 6. 测试策略（Definition of Done）

### 单元测试（mock SDK）

| ID | 用例 | 期望 |
|---|---|---|
| UT-01 | namespace-only：仅 `namespace="PAAS.CONTAINER"` | SDK Request 的 body 包含 1 个 `QueryMetricItemOptionParam`，namespace 为 PAAS_CONTAINER enum |
| UT-02 | namespace + metric_name + dimensions 全部提供 | metricItems[0] 含正确 namespace/metricName/dimensions |
| UT-03 | 仅 inventory_id（不带 namespace）| Request type=inventory，body.inventoryId 正确，body.metricItems 为 null |
| UT-04 | 同时提供 namespace + inventory_id | inventory_id 优先；WARN 日志被写入；body.metricItems 为 null |
| UT-05 | 既无 namespace 也无 inventory_id → INVALID_PARAM | 抛 InvalidParamException，不调 SDK |
| UT-06 | `dimensions=[{name:"appName",value:""}]`（空 value）→ INVALID_PARAM | 抛 InvalidParamException |
| UT-07 | `limit=1001` → INVALID_PARAM | 不调 SDK |
| UT-08 | `limit=0` → INVALID_PARAM | 不调 SDK |
| UT-09 | `start=-1` → INVALID_PARAM | 不调 SDK |
| UT-10 | `namespace="syc.ecs"`（CES 风格） → INVALID_PARAM | 不调 SDK |
| UT-11 | `inventory_id="bogus_xxx"`（resType 不在枚举） → INVALID_PARAM | 不调 SDK |
| UT-12 | SDK 返回空 metrics → pagination.count=0, has_more=false | metrics=[], pagination 正确 |
| UT-13 | SDK 返回 next_token != null + count>0 → has_more=true | next_token 透传 |
| UT-14 | SDK 返回 next_token=null → has_more=false | |
| UT-15 | HTTP 429 → 重试 3 次后 UPSTREAM_THROTTLED + trace_id | 沿用 SdkExceptionMapper，验证 errorCode 与 retryable |
| UT-16 | HTTP 401 → 不重试，UPSTREAM_AUTH_FAILED | |
| UT-17 | HTTP 503 → 重试 3 次后 UPSTREAM_ERROR | |
| UT-18 | RequestTimeoutException → TIMEOUT | |
| UT-19 | SDK 调用 `withLimit("100")`，验证 String 转换 | 用 ArgumentCaptor 验证 SDK request.limit 是 String "100" |
| UT-20 | `dimension_value_hash` 字段透传 | 输出 DTO 含 dimension_value_hash |

### 类型契约测试（防 SDK 升级时字段静默变更）

| ID | 用例 | 期望 |
|---|---|---|
| TC-01 | `MetricItemResultAPI` 含 `namespace / metricName / unit / dimensions / dimensionvaluehash` 字段 | 反射检查 |
| TC-02 | `Dimension`（aom.v2.model）含 `name / value` 字段 | 反射检查 |
| TC-03 | `MetaDataSeries` 含 `count / offset / total / nextToken` 字段 | 反射检查 |
| TC-04 | `QueryMetricItemOptionParam` 含 `NamespaceEnum` 嵌套类，并含 `PAAS_CONTAINER / PAAS_NODE / PAAS_SLA / PAAS_AGGR / CUSTOMMETRICS` 静态常量 | 反射检查（**这是 CES spec 没有的，因为 CES 不用 Enum**） |
| TC-05 | 样例 JSON（华为云文档 `ListMetricItems` 示例）能被 SDK 反序列化为 `ListMetricItemsResponse`，且关键字段无 null | 用 `/sdk-samples/aom/list-metric-items-response.json`，参考 CES 的 TC-04 |

### 部署后冒烟（生产环境真实调用）

`scripts/smoke/smoke-list_aom_metrics.sh <host:port>` 三个用例:

1. `namespace=PAAS.CONTAINER, limit=5` → 预期非空 metrics（前提是测试集群有应用接入 AOM）
2. `inventory_id=host_<已知节点 ID>, limit=5` → 预期返回该节点相关指标
3. `limit=1001` → 预期返回 INVALID_PARAM

## 7. 评审决议（v0.2 锁定）

> 业务方评审会的决议。每条对应 v0.1 §7 的开放问题。

| # | 问题 | 决议 | 影响章节 |
|---|---|---|---|
| 1 | MVP 支持的 namespace 范围 | **5 个预定义全开 + 自定义命名空间**。Agent 场景多样，限制范围反而增加后续解锁成本。校验仅做格式检查，不做白名单。 | §3.2 校验规则 6 |
| 2 | `inventory_id` 模式实用性 | **保留**。业务方反馈"按节点/集群反查"是真实高频场景，删了会让运维 Agent 退化。 | §3.2 输入参数表 |
| 3 | `dimension_value_hash` 透传 | **保留**。Agent 跨调用关联同维度组合时有用；体积可忽略（一个 32 字节字符串/条）。 | §3.3 输出契约 |
| 4 | 跨 projectId 查询 | **MVP 不支持**。单服务实例绑定单 projectId，通过环境变量注入。多 projectId 是未来 Agent 编排层职责。 | §5 配置 |
| 5 | 透传 AOM 业务错误码（`SVCSTG_AMS_xxx`） | **不透传**。MVP 只透 HTTP status + Huawei X-Request-Id。Agent 真有需求再扩 ErrorResponse 加 `upstream_error_code`。 | §3.4 错误码映射（保持不变） |
| 6 | QPS=10 是否合理 | **保持 10**。AOM 指标元数据变化慢（小时级），10 QPS 已经远超运维 Agent 实际查询频率。投产观察后视真实数据再调。 | §5 限流 |

### 配套基础设施变更（与本 spec 强耦合，需在 T06 实现）

- 在 `HuaweiCloudProperties` 增加 `projectId` 字段，环境变量 `HUAWEICLOUD_PROJECT_ID`
- `HuaweiCloudCredentialsHealthIndicator` 加上 projectId 缺失检查
- `application.yml` 加上 `resilience4j.ratelimiter.instances.aom-readonly` 配置（QPS=10，与 ces-readonly 对称）
- SRE 已确认 CCE VPC 出口已放行 `*.cn-southwest-2.myhuaweicloud.com`，AOM endpoint 不需要额外开 ticket

## 8. 变更记录

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| v0.1 | 2026-05-21 | PL | 首版 draft |
| v0.2 | 2026-05-21 | PL | 合入业务方评审决议；状态 Approved；新增"配套基础设施变更"小节 |
