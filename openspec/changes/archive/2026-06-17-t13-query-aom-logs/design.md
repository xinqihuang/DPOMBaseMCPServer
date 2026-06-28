## Context

存量工具回填。原始 spec：`docs/specs/tools/query_logs.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T13-query-aom-logs.md`（状态 Done，提交 `4c346d6`）。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类 / 方法 / 版本、字段映射表、错误码 → retryable、非功能要求（限流 / 重试 / 超时 / 可观测）、时间参数格式约定、以及 AI 易错点。

## Goals / Non-Goals

**Goals:**
- 在已有 `AomMetricsAdapterImpl` 上扩展 `queryLogs(...)`，无损暴露 AOM `ListLogItems` 的日志检索能力。
- 把上游 `result` JSON 字符串以 String 透传给 Agent / 上层自行解析，避免随上游 schema 升级断裂。
- 复用 AOM 只读链路既有的限流 / 重试 / 异常映射模式，降低注入复杂度。

**Non-Goals:**
- 不解析 `result` 字符串内部结构。
- 不做翻页 / `lineNum` 游标管理（MVP 不暴露）。
- 不支持跨 region / 跨 projectId。
- 不做日志写入或日志组 / 日志结构定义管理（写操作，不在 MVP 范围）。
- 不新建独立 `AomLogAdapterImpl`。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.aom.v2.AomClient`
- **SDK 方法**：`listLogItems(ListLogItemsRequest)`
- **SDK 版本**：v3.1.177（钉死，字段缺失先怀疑版本；见 CLAUDE.md §1）
- **AOM API**：`ListLogItems`（`type=querylogs`）

**请求字段映射（MCP 输入 → SDK `ListLogItemsRequest` / `QueryBodyParam`）**：

| MCP 输入 | SDK Request 字段 | 类型 |
|---|---|---|
| 固定常量 | `request.withType("querylogs")` | String |
| `category` | `body.withCategory(...)` | String |
| `startTime` | `body.withStartTime(...)` | Long |
| `endTime` | `body.withEndTime(...)` | Long |
| `pageSize` | `body.withPageSizeSize(String.valueOf(pageSize))` | String（**字段名带双 `Size`**） |
| `isDesc` | `body.withIsDesc(...)` | Boolean |
| `keyWord` | `body.setKeyWord(...)`（**仅在非 null 时调用**） | String |

**响应字段映射（SDK `ListLogItemsResponse` → DTO `AomQueryLogsResponse`，3 字段透传）**：

| SDK 字段 | DTO 字段 | 类型 | 说明 |
|---|---|---|---|
| `getResult()` | `result` | String | 上游原始 JSON 字符串，**不解析** |
| `getErrorCode()` | `errorCode` | String | 成功为 `SVCSTG_AMS_2000000` |
| `getErrorMessage()` | `errorMessage` | String | 成功通常为 `null` |

### 时间参数格式

- `startTime` / `endTime` 为 **UTC 毫秒**（`long`），区别于本仓其他工具的不一致时间格式约定：
  - 上游 String 原样透传（如 `list_apm_alarm_data` 的 `alarm_start_time`）；
  - ISO8601 / `OffsetDateTime`（如 CES v2 alarm history）；
  - `startMillis` / `endMillis` / `durationMinutes` 三件套（部分 trace / metric 工具）。
- 本工具固定 **UTC 毫秒 long**，service 层校验 `endTime` 必须**严格大于** `startTime`。Tool description 须显式写明 "'start_time'/'end_time' are UTC milliseconds"，避免 Agent 误传秒或 ISO 字符串。

### DTO 紧凑构造默认值

- `AomQueryLogsRequest` 紧凑构造**只设默认、不做业务校验**：`pageSize == null → 100`、`isDesc == null → Boolean.TRUE`。
- 校验在 service 层，失败走 `InvalidParamException` → `INVALID_PARAM`；紧凑构造抛 `IllegalArgumentException` 会泄漏到统一错误码之外（CLAUDE.md §3.4 + T05 同款约束）。

### Service 层校验

- `ALLOWED_CATEGORIES = {app_log, node_log, custom_log}`；`PAGE_SIZE_MIN=1`、`PAGE_SIZE_MAX=1000`。
- 5 项校验：`request == null` / `category` 白名单 / `startTime`+`endTime` 非空 / `endTime > startTime` / `pageSize ∈ [1, 1000]`。

### 易错点

1. **`withPageSizeSize` 字段名双 `Size` 不是笔误**，是上游 API 命名；且走 String：`String.valueOf(pageSize)`，不是直接传 int / `Integer.toString`。
2. **`withType("querylogs")` 必传**：标识这是日志查询。
3. **`result` 是 String 不是 object**：DTO 字段保持 String，不在 adapter 层 `ObjectMapper.readTree`。
4. **`keyWord`（驼峰，首字母小写）不是 `keyword`**；且仅在非 null 时调用 `setKeyWord`。
5. **紧凑构造只设默认不抛异常**：校验失败必须走 `InvalidParamException`。
6. **复用 `AomMetricsAdapterImpl` 而非新建 `AomLogAdapterImpl`**：日志接口语义独立，但底层共用同一 `AomClient` + 同一 `aom-readonly` 限流域。
7. **Tool 层只 catch `SmartomException` 转 `ErrorResponse`**，不 catch `Exception`（CLAUDE.md §3.4）。
8. **MCP 可选参数显式标 `@ToolParam(required = false)`**，否则 schema 误标 required。

## Risks / Trade-offs

- **`result` 透传 vs 结构化**：透传换取对上游 schema 升级的鲁棒性，代价是 Agent 需自行 `JSON.parse`；通过 Tool description "Returns the raw upstream 'result' JSON string for downstream parsing" 明确告知。
- **错误码 → retryable 映射**：

  | 上游情况 | error_code | retryable |
  |---|---|---|
  | 入参校验失败 | INVALID_PARAM | false |
  | HTTP 429 / SDK throttling | UPSTREAM_THROTTLED | true |
  | HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
  | HTTP 5xx | UPSTREAM_ERROR | true |
  | 调用超时 | TIMEOUT | true |
  | 序列化 / 未分类异常 | INTERNAL | false |

- **非功能**：限流复用 `aom-readonly`（与 `list_aom_metrics` / `query_aom_metric_data` 共享配额）；仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做 3 次指数退避重试（200ms / 800ms / 3.2s，`huaweicloud-retryable`）；单次 SDK 调用超时 10s；Micrometer `mcp_tool_invocation{tool="query_logs"}`，INFO 日志（adapter 起点）含 `category` / `startTime` / `endTime` / `pageSize`，WARN 日志（tool 兜底）含 `errorCode` / `upstreamTraceId`。
- **遗留**：UT（Service / Adapter / Tool）、类型契约测试、贵阳冒烟脚本、Micrometer 指标、README 示例本期未交付（见 tasks.md 末尾）。
