> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T10-query-apm-traces.md`，状态 Done，提交 `4c346d6`）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-apm）

- [x] 1.1 新增 `ApmTraceAdapter` 接口 + `ApmTraceAdapterImpl`（含 `queryTraces`，businessId fallback 走配置默认值）
- [x] 1.2 新增 DTO record：`ApmQueryTracesRequest` / `ApmQueryTracesResponse` / `ApmSpan`（含 tags `Map.of()` 兜底）
- [x] 1.3 新增 `ApmClientConfig` 装配 `ApmClient` Spring bean（复用 HUAWEICLOUD_AK/SK）
- [x] 1.4 SDK 异常 → `ErrorCode` 映射（429/401/5xx/Timeout）

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `ApmTraceService.queryTraces`，校验 page≥1 / pageSize∈[1,500] / timeUsedMin≥0
- [x] 2.2 业务异常 → `INVALID_PARAM`

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `ApmTraceTool`，`@Tool(name="query_traces")`，暴露 8 个 `@ToolParam`
- [x] 3.2 `McpServerConfig` 注入 `ApmTraceTool` 到 `ToolCallbackProvider`

## 4. 配置

- [x] 4.1 `HuaweiCloudProperties` 新增 `apmBusinessId` / `apmRegion` 字段
- [x] 4.2 `application.yml` 新增 `apm-readonly` RateLimiter 实例（10 QPS，与 ces-readonly 隔离）

## 5. 遗留项（本期未交付）

- [ ] 5.1 Tool / Service / Adapter UT（`ApmTraceServiceTest` / `ApmTraceAdapterImplTest` 等）
- [ ] 5.2 Contract Test（`TraceSearchParam` / `ClientSpanInfo` / `ShowSpanSearchResponse` 字段反射）
- [ ] 5.3 贵阳冒烟脚本 `scripts/smoke/smoke-query_traces.sh`
- [ ] 5.4 Micrometer 指标看板 + README 使用示例
