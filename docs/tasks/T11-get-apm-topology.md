# T11 — 实现 get_service_topology（APM 调用链拓扑）

> 状态: **Done**（提交 `4c346d6`，2026-06-02 回填文档） · 估时: 0.5d · 依赖: T10（APM adapter + `ApmClient` bean + `apm-readonly` RateLimiter 已就绪）· 关联 spec: `docs/specs/tools/get_service_topology.md`

## 目标

在 T10 已建立的 APM adapter 上新增 `ShowTopology` 能力，提供 MCP tool `get_service_topology` 让 Agent 按 traceId 获取调用链拓扑（节点 + 有向边 + client/server 时间）。

## 范围

**做**:
- `ApmTraceAdapter` 接口新增 `getTopology`；`ApmTraceAdapterImpl` 实现 `apm.showTopology` SDK 包装
- 新增 DTO：`ApmGetTopologyRequest` / `ApmGetTopologyResponse` / `ApmTopologyNode` / `ApmTopologyLine`
- `ApmTraceService` 加 `getTopology(...)`：traceId 非空校验
- MCP tool `ApmTopologyTool`，`@Tool(name = "get_service_topology")` 注册
- `McpServerConfig` 注入 `ApmTopologyTool` 到 `ToolCallbackProvider`

**不做**（防止任务蔓延）:
- ❌ Tool / Service / Adapter 层 UT、Contract Test、冒烟脚本（spec §6 列出，本期未交付，进遗留项）
- ❌ trace 搜索本身（用 `query_traces`）
- ❌ 拓扑过滤 / 子图切分
- ❌ span 详细 tags / 异常详情

## 前置阅读

**必读**:
1. `docs/specs/tools/get_service_topology.md` — 完整 spec
2. `docs/specs/tools/query_traces.md` — 前置 tool，明确 traceId 来源
3. `CLAUDE.md` §3 / §4 — 编码规范、SDK 不泄漏、错误码统一

## 实际产物清单

```
docs/specs/tools/
  get_service_topology.md                             ← 本任务回填（v1.0）
docs/tasks/
  T11-get-apm-topology.md                             ← 本任务卡

agentic-adapter/agentic-adapter-apm/
  src/main/java/com/huawei/smartom/agentic/adapter/apm/
    ApmTraceAdapter.java                              ← 加 getTopology 方法
    ApmTraceAdapterImpl.java                          ← 加实现 + 私有 toLine 映射
    dto/
      ApmGetTopologyRequest.java                      ← 新增 record
      ApmGetTopologyResponse.java                     ← 新增 record
      ApmTopologyNode.java                            ← 新增 record
      ApmTopologyLine.java                            ← 新增 record

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/apm/
    ApmTraceService.java                              ← 加 getTopology 方法

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/ApmTopologyTool.java                         ← 新增（@Tool 注册）
    config/McpServerConfig.java                       ← 注入 ApmTopologyTool
```

## 关键技术要求

### 1. DTO 设计

`ApmTopologyLine` 将 SDK 嵌套的 `TraceTopologyLineInfo`（clientInfo / serverInfo）拍平为四个字段：`clientStartTime` / `clientTimeUsed` / `serverStartTime` / `serverTimeUsed`，避免 Agent 端再做嵌套展开。

### 2. Service 层校验

```java
if (request.traceId() == null || request.traceId().isBlank()) {
    throw new InvalidParamException("trace_id is required");
}
```

仅做必填校验——traceId 格式由上游 APM 判定，本层不预判（不同 region / 不同采样策略下格式可能微调）。

### 3. Adapter SDK 映射要点

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

`nodes` / `lines` 上游可能返回 null，用 `Collections.emptyList()` 兜底。

### 4. Tool 层错误处理

复用 `try { ... } catch (SmartomException e) { return ErrorResponse.of(...); }` 模式，与 `ApmTraceTool` 对齐。

## 验收标准

实际完成项（spec §7 mapping）：

- [x] MCP Inspector 能看到 `get_service_topology`，description 正确
- [x] 复用 `apm-readonly` RateLimiter
- [x] 日志含 traceId（`apm.showTopology start` INFO）
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（`4c346d6`）
- [ ] Tool / Service / Adapter UT（后续任务补）
- [ ] Contract Test（后续任务补）
- [ ] 贵阳冒烟脚本（后续任务补）
- [ ] Micrometer 指标 + README 示例（后续任务补）

## AI 易错点提醒

**spec §4 已列出**：
1. `clientInfo` / `serverInfo` 可能为 null，必须三元判空再取字段
2. `nodeId` 是 `Long`，不是 `String`（与 traceId 不同）
3. 该接口**不接受** `x-business-id` 头（与 `showSpanSearch` 不同）
4. "topology" 是按单 trace 重建的图，**不是**服务级常驻拓扑，名字易误导
5. `nodes` / `lines` 上游可能为 null，需 `Collections.emptyList()` 兜底

**额外**：
6. **Tool 名是 `get_service_topology` 而不是 `get_apm_topology`**——任务卡文件名为 `T11-get-apm-topology.md`，但 `@Tool(name=...)` 是 `get_service_topology`，spec 文件名跟 tool name 走
7. **`ApmTraceService` 同时承载 `queryTraces` 与 `getTopology` 两个方法**——本任务不是新建 Service，而是在 T10 的 service 上加方法
8. **`get_service_topology` 没有分页**——单 trace 拓扑结果集天然有限，不要按 `query_traces` 套 page / pageSize

## 完成后

PR：`feat(T11): add get_service_topology APM topology tool`（已提交：`4c346d6`）。
