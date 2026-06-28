> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T11-get-apm-topology.md`，状态 Done，提交 `4c346d6`）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-apm）

- [x] 1.1 `ApmTraceAdapter` 接口新增 `getTopology` 方法
- [x] 1.2 `ApmTraceAdapterImpl` 实现 `apm.showTopology` SDK 包装（复用既有 `apmClient`，不注入 `x-business-id` 头）
- [x] 1.3 新增 DTO record：`ApmGetTopologyRequest` / `ApmGetTopologyResponse` / `ApmTopologyNode` / `ApmTopologyLine`（将 `TraceTopologyLineInfo` 拍平为 4 个 client/server 时间字段）
- [x] 1.4 私有 `toLine` 映射：`clientInfo` / `serverInfo` 三元判空，`nodes` / `lines` 用 `Collections.emptyList()` 兜底
- [x] 1.5 SDK 异常 → `ErrorCode` 映射（429 / 401 / 5xx / Timeout）+ `upstream_trace_id` 透传

## 2. Service 层（agentic-monitoring）

- [x] 2.1 `ApmTraceService` 加 `getTopology(...)`（在 T10 既有 service 上加方法，非新建）
- [x] 2.2 traceId 必填校验（null / 空串 / 仅空白 → `InvalidParamException` → `INVALID_PARAM`），格式不预判

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `ApmTopologyTool`，`@Tool(name="get_service_topology")`，单一入参 `traceId`，无分页
- [x] 3.2 Tool 层复用 `try/catch SmartomException → ErrorResponse.of(...)` 模式
- [x] 3.3 `McpServerConfig` 注入 `ApmTopologyTool` 到 `ToolCallbackProvider`

## 4. 非功能

- [x] 4.1 复用 `apm-readonly` RateLimiter（与 `query_traces` 共享 10 QPS）
- [x] 4.2 `apm.showTopology start` INFO 日志含 traceId
- [x] 4.3 Checkstyle 0 violations

## 5. 遗留项（本期未交付）

- [ ] 5.1 Service 层 UT（`ApmTraceServiceTest.getTopology`：null / 空 / 空白 traceId → INVALID_PARAM；合法值委托）
- [ ] 5.2 Adapter 层 UT（`ApmTraceAdapterImplTest.getTopology`：nodes/lines 兜底、clientInfo=null 不 NPE、字段对齐、404 映射）
- [ ] 5.3 Contract Test（`TraceTopologyNode` / `TraceTopologyLine` / `TraceTopologyLineInfo` / `ShowTopologyResponse` 字段反射）
- [ ] 5.4 冒烟脚本 `scripts/smoke/smoke-get_service_topology.sh`
- [ ] 5.5 Micrometer 指标在 actuator/prometheus 可见
- [ ] 5.6 README 含 tool 使用示例
