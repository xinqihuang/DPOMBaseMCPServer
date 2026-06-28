## Context

存量工具回填（v2 修订版生效）。原始任务卡：`docs/tasks/T24-ces-namespace-enum.md`（状态 Done，v2）；相关工具 spec：`docs/specs/tools/list_ces_metrics.md` / `docs/specs/tools/query_ces_metric_data.md` / `docs/specs/tools/batch_query_ces_metric_data.md`（均 v1.0, Approved）；关联 ADR-004（§4.3 受控枚举 vs 运行时发现分轴解法）修订段，及 T23 的「发现真值转发、Agent 自编排」哲学。

本文承载 OpenSpec 主 spec 放不下的重契约：`CesNamespace` 枚举设计、三处工具入参枚举化与 `.getValue()` 映射归集、SDK 类/方法/版本、**两套 CES API 上不一致的 period 时间参数格式**、from/to 时间戳约定、缓存设计、错误码→retryable 映射、限流/重试/超时/可观测、以及 AI 实现时的易错点。

> 关键说明：本变更属 infra 层，**不引入也不修改任何 capability spec**——故 `changes/t24-ces-namespace-enum/specs/` 目录为空。枚举只动请求侧，响应契约（String namespace、无附加字段）不变。

## Goals / Non-Goals

**Goals:**
- 把 namespace（全局固定目录，§4.3(a)）收敛为请求侧受控枚举 `CesNamespace`（15 值），使合法取值经 Spring AI 反射进工具 JSON Schema，非法值由框架更早拒绝。
- 枚举 → SDK String 的映射集中在 Tool 层一处（`ToolValidations.cesNamespaceValue`），不在 service/adapter 间散落。
- 收紧工具描述，固化「选 namespace → `list_ces_metrics` 发现真实 metric_name/维度名 → 查数」的 Agent 自编排发现链，并写明 RDS 双 namespace 的探测指引。
- 为 `list_ces_metrics` 加发现缓存，既降低重复发现成本，也作为 RDS 形态探测的成本保障。

**Non-Goals:**
- ❌ 不做服务端 SYS_RDS 形态 fallback / `resolved_namespace` 回显（v1 已回滚，不得加回）；服务端不做任何隐式 namespace 替换/路由。
- ❌ 不枚举 `metric_name` / 维度名（按 namespace 变化、海量 → 走运行时发现 §4.3(b)）；不做静态目录。
- ❌ 不枚举全部华为云 namespace（只 15 个支持项）。
- ❌ 不改响应 DTO（§4.1 无损，namespace 保持 String，不加附加字段）；adapter DTO 不感知枚举。

## Decisions

### `CesNamespace` 枚举（15 值）

- 含且仅含 15 个受支持取值，每值携带 `SYS.*` 字面量 + 中文说明：

  ```
  SYS_ECS / SYS_OBS / SYS_EVS / SYS_VPC / SYS_GEIP / SYS_DMS / SYS_DCS / SYS_WAF
  / SYS_CFW / SYS_APIG / SYS_RDS / SYS_RDS_MYSQL_CLUSTER / SYS_ELB / SYS_DNS / SYS_NAT
  ```

- `fromValue` 严格拒绝未知值（抛 `IllegalArgumentException` → Tool 层转 `INVALID_PARAM`，或由 Spring AI 枚举反序列化失败直接拦下），同时接受两种写法：字面量 `SYS.ECS` 与常量名 `SYS_ECS`。
- `.getValue()` 返回 SDK 侧字面量（如 `SYS.ECS` / `SYS.RDS_MYSQL_CLUSTER`），映射调用集中在 `ToolValidations.cesNamespaceValue` 一处。

### RDS 双命名空间（v2 生效版）

- 主备 / 单机实例 = `SYS.RDS`；MySQL 集群版 = `SYS.RDS_MYSQL_CLUSTER`。两个值都显式进枚举。
- Agent 不确定实例形态时，先调 `list_ces_metrics(namespace, dim)` 探测哪个 namespace 下存在该实例的指标定义（结果走 1 天缓存，几乎零成本），用有指标定义的那个 namespace 查数。工具描述写明该流程、禁止瞎猜。
- 服务端不做任何隐式 namespace 替换，形态判定是 Agent 的职责（与 T23 哲学一致）。

### 三处工具入参枚举化

- `list_ces_metrics`：namespace 入参为枚举，可选（null 允许）。
- `query_ces_metric_data`：namespace 入参为枚举，必填。
- `batch_query_ces_metric_data`：工具入参项 `CesBatchMetricQueryInput` 的 namespace 为枚举，必填（每条 metric 各自带）。
- adapter 请求/响应 DTO 一律保持 `String`（§4.1 无损），adapter 不感知枚举；枚举 → String 在 Tool 层经 `ToolValidations.cesNamespaceValue` 完成。

### SDK 映射（三工具，CesClient 共用）

- **SDK 类**：`com.huaweicloud.sdk.ces.v1.CesClient`（三工具共用）。
- **SDK 方法**：
  - `list_ces_metrics` → `listMetrics(ListMetricsRequest)`（SDK 版本 v3.1.196；任务卡其余两工具标 v3.1.177，以 `CLAUDE.md §1` 实际部署版本为准，字段缺失先怀疑版本）。
  - `query_ces_metric_data` → `showMetricData(ShowMetricDataRequest)`（v3.1.177）。
  - `batch_query_ces_metric_data` → `batchListMetricData(BatchListMetricDataRequest)`（v3.1.177）。
- namespace 字段映射：枚举 `.getValue()` → `withNamespace(String)`（list/show）或 `body.metrics[i].withNamespace(String)`（batch）。
- 其余字段映射（filter/period/from/to/dimensions）维持各工具 v1.0 spec §4，本变更不改。

### 时间 / period 参数格式（**两套 CES API 上不一致，最易错**）

- `from` / `to`：均为**毫秒级 UNIX 时间戳（Long）**，要求 `to` 严格大于 `from`（`from >= to` → `INVALID_PARAM`）。CES 会按 period 向前取整 `from`，返回点数可能略多于预期（上游行为）。
- `period`（聚合粒度，秒）枚举集：`1 / 60 / 300 / 1200 / 3600 / 14400 / 86400`（`CesMetricPeriod`，7 值，严格）。**同一个 SDK 在两套 API 上 PeriodEnum.fromValue 入参类型不一致**：
  - `query_ces_metric_data`：`ShowMetricDataRequest.PeriodEnum.fromValue(int)` —— 接收**整数**。
  - `batch_query_ces_metric_data`：`BatchListMetricDataRequestBody.PeriodEnum.fromValue(String)` —— 接收**字符串**，要 `String.valueOf(period.getSeconds())`。
  - 不要混淆；混用会编译失败或装配错值。
- `filter`（聚合方式）：`average / max / min / sum / variance`（`CesMetricFilter`，5 值，严格）。
- 维度映射两套 API 也不同：`query_ces_metric_data` 用 4 个独立字符串字段 `dim0`…`dim3`（`"name,value"` 拼接，非对象，按数组顺序）；`batch_query_ces_metric_data` 用结构化对象 `MetricsDimension().withName().withValue()`；`list_ces_metrics` 只支持单维度过滤入参 `dim.0`（SDK `Dim0`，`"key,value"` 字符串）。
- 响应数据点类也不同：`query` 是 `Datapoint`（自带 `unit`），`batch` 是 `DatapointForBatchMetric`（无 unit，unit 在父级 `metrics[].unit`），不要混淆。

### 错误码 → retryable 映射（三工具一致）

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败（Tool/Service 层，含非法 namespace） | INVALID_PARAM | false |
| HTTP 429 / SDK throttling | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

失败响应统一结构带 `error_code` / `error_message` / `upstream_trace_id`（华为云 `X-Request-Id`，可空）/ `retryable`。

### 缓存设计（`list_ces_metrics`）

- Caffeine TTL 缓存，cache 名 `ces.discovery-cache`，默认 TTL 1 天、最大 2000 条。
- 缓存 key = 整个 `list_ces_metrics` 请求 record（值语义，含全部参数）。
- **失败 / 空结果不写缓存**（保证错误不被缓存、探测能感知真实存在性）。
- 留 `@CacheEvict` 整体失效口。
- 配置项落 `application.yml`（`ces.discovery-cache`）。

### 非功能要求（三工具共用，本变更不改语义）

- 限流：复用 `ces-readonly` RateLimiter（三工具共享配额，默认 10 QPS，可配），不新增限流域。
- 重试：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，最多 3 次指数退避 200ms / 800ms / 3.2s。
- 超时：单次 SDK 调用 10s。
- 可观测：Micrometer `mcp_tool_invocation{tool="...", result="success|error", error_code="..."}`；INFO 日志含入参摘要 + 耗时 + upstream trace id。

## Risks / Trade-offs

- **枚举只动请求侧**：响应 DTO（`CesMetricInfo` 等）namespace 是无损 String，绝不改；否则破坏 §4.1 无损契约与既有契约测试。
- **服务端无隐式替换**：RDS 形态判定靠 Agent 探测，体验上 Agent 需多一次（带缓存的）`list_ces_metrics` 调用；权衡：避免 v1 那种 namespace 穿层改写 + 响应加非上游字段 + 批量逐项 zip 回填的实现复杂度与可读性代价，且与 T23 哲学一致。缓存把探测成本压到接近零。
- **两套 PeriodEnum.fromValue 类型不一致**（int vs String）是本链路最隐蔽的坑，已在 Decisions 显式记录；类型契约测试（TC）兜底 SDK 升版本字段漂移。
- **缓存键含全部参数 + 失败/空不缓存**：保证发现结果的存在性语义正确（探测可信）、错误不被放大。
- **15 值封闭枚举**：新增华为云 namespace 需改枚举发版；权衡取「Schema 显式合法取值、AI 不瞎拼」的收益，metric_name/维度名仍走发现保持开放。
- **与真实 SDK 冲突时停下来问**（`CLAUDE.md §5.1`），不臆造字段/方法。
