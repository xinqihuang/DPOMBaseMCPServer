# Spec: query_traces

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 能够搜索华为云 APM 调用链 span，用于延迟 / 错误问题排查。

典型场景:
- Agent 按 `traceId` 拉取单条调用链关联的所有 span
- Agent 巡检某入口 url（`source` 模糊匹配）最近一段时间的慢调用 / 错误调用
- Agent 配合指标尖刺，下钻到 trace 层

定位: APM 调用链入口 tool；与 `get_service_topology` 互补——本 tool 出 span 列表，topology 出节点 / 边。

## 2. 范围边界

**做**:
- 搜索 APM `ClientSpanInfo` 列表（`ShowSpanSearch`）
- 支持按 traceId / source / 时间窗口 / 错误标志 / 最小耗时过滤
- 1-based 偏移分页
- `businessId` 既可入参传，也可走配置项默认值

**不做**:
- 不返回完整 span 树（用 `get_service_topology`）
- 不做日志 / 异常详情（建议走 AOM 日志 tool）
- 不做跨 region / 跨账号
- 不做客户端聚合或排序（透传上游顺序）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `query_traces`
- description（Agent 看到的）:

  > Search APM (Application Performance Management) trace spans for a Huawei
  > Cloud application. Use this when investigating latency / error issues —
  > filter by traceId, entry url/method (source), time window, error flag, or
  > minimum elapsed time. Call get_service_topology afterwards with a specific
  > traceId to see the call graph.

- annotations: `readOnlyHint=true` · `destructiveHint=false` · `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `businessId` | long | 否 | 配置项默认值 | APM 应用 id（`x-business-id` 请求头）；null 时使用 `HuaweiCloudProperties.getApmBusinessId()` |
| `startTimeString` / `endTimeString` | string | 否 | null | 格式 `yyyy-MM-dd HH:mm:ss` |
| `traceId` | string | 否 | null | 精确 traceId 过滤 |
| `source` | string | 否 | null | 入口 url / 方法名，模糊匹配 |
| `hasError` | bool | 否 | null | true 时仅返回出错 span |
| `timeUsedMin` | long | 否 | null | 最小耗时（毫秒），需 >= 0 |
| `page` | int | 否 | 1 | 页码（1-based），需 >= 1 |
| `pageSize` | int | 否 | 50 | 单页大小，[1, 500] |

**校验规则（Service 层）**: `page<1` / `pageSize` 越界 / `timeUsedMin<0` → `INVALID_PARAM`。其余字段透传给上游。

### 3.3 输出契约（成功）

```json
{
  "total": 12,
  "spans": [
    {
      "trace_id": "...", "span_id": "0", "global_trace_id": "...",
      "source": "/api/order/checkout", "real_source": "POST /api/order/checkout",
      "class_name": "com.example.OrderController",
      "start_time": 1700000000123, "time_used": 458,
      "code": 500, "has_error": true, "error_reasons": "...",
      "http_method": "POST", "tags": {"app": "order"}
    }
  ]
}
```

- `total` 由 SDK `getTotal()` 透传，可能为 `null`
- `tags` 是 `Map<String,String>`，**始终非 null**（adapter 用 `Map.of()` 兜底）
- `http_method` 仅 URL 监控项有值
- `spans[]` 顺序按上游返回顺序透传

### 3.4 输出契约（失败）

```json
{"error_code": "...", "error_message": "...", "upstream_trace_id": "...", "retryable": true}
```

错误码映射同 list_alarms（INVALID_PARAM / UPSTREAM_* / TIMEOUT / INTERNAL）。

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.apm.v1.ApmClient`
- **SDK 方法**: `showSpanSearch(ShowSpanSearchRequest)`
- **SDK 版本**: v3.1.177
- **请求体类型**: `TraceSearchParam`（`ShowSpanSearchRequest.withBody(...)` 挂载）

字段映射: `businessId` 走 `ShowSpanSearchRequest.setXBusinessId(Long)`（**请求头**）；其余字段走 `TraceSearchParam` 的 `setStartTimeString` / `setEndTimeString` / `setTraceId` / `setSource` / `setHasError` / `setTimeUsedMin` / `withPage` / `withPageSize`；`region` 通过 `withRegion(properties.getApmRegion())` 必填。

**AI 容易写错的点**:
1. `businessId` 是 **HTTP 请求头**（`x-business-id`），用 `setXBusinessId`，不在 body 里
2. `region` **必须**显式 `withRegion(...)` 写到 body，否则上游报参数缺失
3. 时间字段是 **字符串** `yyyy-MM-dd HH:mm:ss`，不是毫秒时间戳（CES / APM 不同，别混）
4. `ClientSpanInfo.getTags()` 可能为 `null`，必须 `Map.of()` 兜底
5. 响应字段是 `getSpanInfoList()` + `getTotal()`，不是 `getSpans()`
6. `businessId` 缺失时走 `HuaweiCloudProperties.getApmBusinessId()`，**fallback 在 adapter 层做**

## 5. 非功能要求

- **限流**: 独立 `apm-readonly` RateLimiter（与 CES 隔离配额，10 QPS 默认）
- **重试**: `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试 3 次，指数退避 200ms / 800ms / 3.2s
- **超时**: 10s
- **可观测**: `mcp_tool_invocation{tool="query_traces"}` + adapter `apm.showSpanSearch start` INFO（businessId / traceId / source / page / pageSize）

## 6. 测试策略（Definition of Done）

### 单元测试 / 类型契约测试 / 部署冒烟

**本期未交付**。建议后续任务补：

- Service 层 UT（`ApmTraceServiceTest.queryTraces`）：`request=null` / `page=0` / `pageSize=0` / `pageSize=501` / `timeUsedMin=-1` 各自 INVALID_PARAM；默认值正常委托
- Adapter 层 UT（`ApmTraceAdapterImplTest`）：businessId fallback、tags=null 兜底、各字段对齐、SDK 401 不重试
- Contract Test：`TraceSearchParam` / `ClientSpanInfo` / `ShowSpanSearchResponse` 字段反射
- 冒烟脚本 `scripts/smoke/smoke-query_traces.sh`：(1) 最近 1h 正常拉取 (2) pageSize=501 (3) page=0

## 7. 验收标准（DoD）

- [x] MCP Inspector 能看到 `query_traces`，description 正确
- [x] `apm-readonly` RateLimiter 已配置（`application.yml`）
- [x] 日志含入参摘要（`apm.showSpanSearch start` INFO）
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（提交 `4c346d6`）
- [ ] Tool / Service / Adapter / Contract Test（后续任务补）
- [ ] Micrometer 指标在 actuator/prometheus 看到
- [ ] 贵阳冒烟脚本通过
- [ ] README 含 tool 使用示例
