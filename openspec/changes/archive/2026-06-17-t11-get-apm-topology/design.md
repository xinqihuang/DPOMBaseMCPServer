## Context

存量工具回填。原始 spec：`docs/specs/tools/get_service_topology.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T11-get-apm-topology.md`（状态 Done，提交 `4c346d6`）。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类 / 方法 / 版本、字段映射、嵌套对象拍平、不一致的时间参数格式、错误码与非功能要求、AI 易错点。

注意命名错位：任务卡文件名是 `T11-get-apm-topology`，但实际 `@Tool(name=...)` 与 capability 名均跟 tool name 走，为 `get_service_topology` / `get-service-topology`，而非 `get_apm_topology`。

## Goals / Non-Goals

**Goals:**
- 按 traceId 无损暴露 APM `showTopology` 的调用链拓扑（节点 + 有向边 + client/server 时间）。
- 作为 `query_traces` 的后置下钻工具，让 Agent 重建图结构并定位耗时分布。
- 将 SDK 嵌套对象拍平，降低 Agent 端解析成本。

**Non-Goals:**
- 不做 trace 搜索（由 `query_traces` 负责）。
- 不做拓扑过滤 / 子图切分 / 拓扑可视化（图渲染由调用方完成）。
- 不返回 span 详细 tags / 异常详情。
- 不做跨 region / 跨账号。
- 本期不交付 UT / Contract Test / 冒烟脚本 / Micrometer 看板 / README 示例（列入遗留项）。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.apm.v1.ApmClient`
- **SDK 方法**：`showTopology(ShowTopologyRequest)`
- **SDK 版本**：v3.1.177（钉死，字段缺失先怀疑版本）
- **入参装配**：`traceId → ShowTopologyRequest.withTraceId(String)`

**响应字段映射**：

顶层：`global_trace_id` ← `ShowTopologyResponse` 顶层 global trace id。

节点 `nodes[]`（`TraceTopologyNode` → `ApmTopologyNode`）：

| SDK `TraceTopologyNode` | DTO `ApmTopologyNode` | 类型 |
|---|---|---|
| getNodeId | node_id | Long |
| getNodeName | node_name | String |
| getHint | hint | String |

有向边 `lines[]`（`TraceTopologyLine` → `ApmTopologyLine`，嵌套拍平）：

| SDK 来源 | DTO `ApmTopologyLine` | 类型 |
|---|---|---|
| getStartNodeId | start_node_id | Long |
| getEndNodeId | end_node_id | Long |
| getSpanId | span_id | String |
| getClientInfo().getStartTime() | client_start_time | Long |
| getClientInfo().getTimeUsed() | client_time_used | Long |
| getServerInfo().getStartTime() | server_start_time | Long |
| getServerInfo().getTimeUsed() | server_time_used | Long |
| getHint | hint | String |

拍平实现要点（私有 `toLine` 方法）：

```java
TraceTopologyLineInfo client = sdk.getClientInfo();
TraceTopologyLineInfo server = sdk.getServerInfo();
return new ApmTopologyLine(
    sdk.getStartNodeId(), sdk.getEndNodeId(), sdk.getSpanId(),
    client == null ? null : client.getStartTime(),
    client == null ? null : client.getTimeUsed(),
    server == null ? null : server.getStartTime(),
    server == null ? null : server.getTimeUsed(),
    sdk.getHint());
```

`nodes` / `lines` 上游可能返回 null，adapter 用 `Collections.emptyList()` 兜底，保证响应中两数组始终非 null。

### Service 层校验

仅做 traceId 必填校验，格式不预判（不同 region / 采样策略下格式可能微调，交由上游 APM 判定）：

```java
if (request.traceId() == null || request.traceId().isBlank()) {
    throw new InvalidParamException("trace_id is required");
}
```

`ApmTraceService` 同时承载 `queryTraces` 与 `getTopology` 两个方法——本任务不是新建 Service，而是在 T10 的 service 上加方法。

### 时间参数格式（不一致约定）

本工具的时间相关字段全部是**响应侧**的整数：`client_start_time` / `server_start_time` 为 **UTC 毫秒时间戳（epoch millis, Long）**，`client_time_used` / `server_time_used` 为**耗时毫秒数（Long）**。与 APM 发现链其它工具的时间参数格式刻意区分，避免混淆：

- 本工具（`get_service_topology`）：响应里是 **UTC 毫秒 Long**，无入参时间。
- `list_apm_alarm_data`：`alarm_start_time` / `alarm_end_time` 为**上游格式字符串**（String，未固定格式，原样透传）。
- 其它 APM 检索类（如 trace 搜索）：常见 **ISO8601 字符串** 或 `startMillis` / `endMillis` / `durationMinutes` 三元组形式。

即上游同一域内同时存在「字符串」「ISO8601」「UTC 毫秒」「startMillis.endMillis.durationMinutes」四种时间表达，本工具落在「UTC 毫秒」一类，且仅出现在响应，不做任何本地解析或格式转换。

### 非功能 / 限流 / 重试 / 超时 / 可观测

- **限流 key**：复用 `apm-readonly` RateLimiter（与 `query_traces` 共享 10 QPS 配额）。
- **重试**：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试 3 次，指数退避 200ms / 800ms / 3.2s。
- **超时**：SDK 传输层 10s。
- **可观测**：Micrometer `mcp_tool_invocation{tool="get_service_topology"}`；adapter `apm.showTopology start` INFO 日志，含 traceId。

### 错误码 → retryable

| 上游情况 | ErrorCode | retryable |
|---|---|---|
| traceId 缺失 / 空 / 仅空白 | `INVALID_PARAM` | false |
| 429 限流 | `UPSTREAM_THROTTLED` | true |
| 401 / 403 鉴权失败 | `UPSTREAM_AUTH_FAILED` | false |
| 5xx / 其它上游错误 | `UPSTREAM_ERROR` | true |
| 传输层超时 | `TIMEOUT` | true |
| 本服务内部异常 | `INTERNAL` | false |

失败响应携带 `retryable` 与 `upstream_trace_id`（华为云 `X-Request-Id`，可空）。SDK 异常不透传到 MCP 层，Tool 层复用 `try { ... } catch (SmartomException e) { return ErrorResponse.of(...); }` 模式，与 `ApmTraceTool` 对齐。

## Risks / Trade-offs

- **命名易误导**："topology" 是按**单 trace** 重建的图，不是服务级常驻拓扑；capability/tool 名为 `get_service_topology` 而非任务卡文件名暗示的 `get_apm_topology`。
- **AI 易错点**：
  1. `clientInfo` / `serverInfo` 是嵌套对象，可能为 null，必须三元判空再取字段，否则 NPE。
  2. `nodeId` 是 `Long` 不是 `String`（与 traceId 不同），别按字符串处理。
  3. 该接口**不接受** `x-business-id` 头（与 `showSpanSearch` / `query_traces` 不同），不要套 header。
  4. `nodes` / `lines` 上游可能为 null，需 `Collections.emptyList()` 兜底。
  5. 本工具**无分页**——单 trace 拓扑结果集天然有限，不要按 `query_traces` 套 page / pageSize。
- **遗留**：UT / Contract Test / 冒烟脚本 / Micrometer 看板 / README 示例本期未交付（见 tasks.md 遗留项）。MCP `annotations`（readOnlyHint 等）在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅为语义意图。
