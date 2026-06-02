# Spec: get_service_topology

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 拿到指定 traceId 的调用链拓扑（节点 + 有向边），用于可视化跨服务调用关系与耗时分布。

典型场景:
- Agent 通过 `query_traces` 找到一条慢 / 错误调用，调本 tool 看清调用链经过哪些服务
- Agent 做根因分析，识别"耗时集中在 client 端还是 server 端"
- Agent 比较两条相邻 traceId 的拓扑差异

定位: `query_traces` 的后置下钻 tool。`query_traces` 出扁平 span 列表，本 tool 出**重建后的图结构**。

## 2. 范围边界

**做**:
- 按 traceId 调用 APM `ShowTopology`，返回节点 + 边
- 暴露每条边的 client 与 server 时间信息

**不做**:
- 不做 trace 搜索（用 `query_traces`）
- 不做拓扑过滤 / 子图切分
- 不返回 span 详细 tags / 异常详情
- 不做跨 region / 跨账号
- 不做拓扑可视化（图渲染由调用方完成）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `get_service_topology`
- description（Agent 看到的）:

  > Get the call-graph topology for a specific APM trace. Returns the list of
  > service nodes and the directed edges (with client/server timing)
  > reconstructed from the trace's spans. Use this after query_traces to
  > visualise how a request flowed across services and where time was spent.
  > Required: a valid trace_id from APM.

- annotations: `readOnlyHint=true` · `destructiveHint=false` · `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `traceId` | string | **是** | — | APM trace_id（通常来自 `query_traces` 输出） |

**校验规则（Service 层）**: `traceId` null / 空串 / 仅空白 → `INVALID_PARAM`（"trace_id is required"）。

### 3.3 输出契约（成功）

```json
{
  "global_trace_id": "1.0.x.x.global",
  "nodes": [
    {"node_id": 1001, "node_name": "order-service", "hint": "java"},
    {"node_id": 1002, "node_name": "payment-service", "hint": "java"}
  ],
  "lines": [
    {
      "start_node_id": 1001, "end_node_id": 1002, "span_id": "0.1",
      "client_start_time": 1700000000100, "client_time_used": 350,
      "server_start_time": 1700000000110, "server_time_used": 280,
      "hint": "http"
    }
  ]
}
```

- `global_trace_id` 来自 SDK 响应顶层
- `nodes[]` / `lines[]` 始终非 null（adapter 用 `Collections.emptyList()` 兜底）
- `lines[].client*` 来自 `TraceTopologyLine.getClientInfo()`（`TraceTopologyLineInfo`），`server*` 同理；任一缺失时对应字段为 `null`，不会抛 NPE

### 3.4 输出契约（失败）

```json
{"error_code": "...", "error_message": "...", "upstream_trace_id": "...", "retryable": true}
```

错误码映射: `traceId` 缺失 → `INVALID_PARAM`；其余同标准映射（UPSTREAM_* / TIMEOUT / INTERNAL）。

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.apm.v1.ApmClient`
- **SDK 方法**: `showTopology(ShowTopologyRequest)`
- **SDK 版本**: v3.1.177

字段映射: `traceId → ShowTopologyRequest.withTraceId(String)`。响应：`nodes` ← `TraceTopologyNode.getNodeId/getNodeName/getHint`；`lines` ← `TraceTopologyLine.getStartNodeId/getEndNodeId/getSpanId/getHint`；client/server 时间 ← `getClientInfo()`/`getServerInfo()` 各自的 `getStartTime()`/`getTimeUsed()`。

**AI 容易写错的点**:
1. `clientInfo` / `serverInfo` 是嵌套对象，可能为 `null`，必须三元判空：`client == null ? null : client.getStartTime()`
2. `nodeId` 是 `Long`（不是 `String`），别按 traceId 习惯当字符串
3. 该接口**不接受** `x-business-id` 头（与 `showSpanSearch` 不同），不要按 `query_traces` 套路加 header
4. 该接口的拓扑是按 **单 trace** 重建的，不是服务级常驻拓扑——名字"topology"易误导
5. `nodes[]` / `lines[]` 上游可能为 `null`，必须 `Collections.emptyList()` 兜底

## 5. 非功能要求

- **限流**: 复用 `apm-readonly` RateLimiter（与 `query_traces` 共享 10 QPS 配额）
- **重试**: `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试 3 次，指数退避 200ms / 800ms / 3.2s
- **超时**: 10s
- **可观测**: `mcp_tool_invocation{tool="get_service_topology"}` + adapter `apm.showTopology start` INFO（含 traceId）

## 6. 测试策略（Definition of Done）

### 单元测试 / 类型契约测试 / 部署冒烟

**本期未交付**。建议后续任务补：

- Service 层 UT（`ApmTraceServiceTest.getTopology`）：`request=null` / `traceId=null` / `traceId=""` / `traceId="   "` 各自 INVALID_PARAM；合法值正常委托
- Adapter 层 UT（`ApmTraceAdapterImplTest.getTopology`）：`nodes=null` / `lines=null` 兜底；`clientInfo=null` 不 NPE；完整字段对齐；上游 404 → 错误码映射
- Contract Test：`TraceTopologyNode` / `TraceTopologyLine` / `TraceTopologyLineInfo` / `ShowTopologyResponse` 字段反射
- 冒烟脚本 `scripts/smoke/smoke-get_service_topology.sh`：(1) 真实 traceId → 非空 nodes+lines (2) traceId=null → INVALID_PARAM (3) traceId="" → INVALID_PARAM

## 7. 验收标准（DoD）

- [x] MCP Inspector 能看到 `get_service_topology`，description 正确
- [x] 复用 `apm-readonly` RateLimiter
- [x] 日志含 traceId（`apm.showTopology start` INFO）
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（提交 `4c346d6`）
- [ ] Tool / Service / Adapter / Contract Test（后续任务补）
- [ ] Micrometer 指标在 actuator/prometheus 看到
- [ ] 贵阳冒烟脚本通过
- [ ] README 含 tool 使用示例
