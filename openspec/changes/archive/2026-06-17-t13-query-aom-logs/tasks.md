> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T13-query-aom-logs.md`，状态 Done，提交 `4c346d6`）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-aom）

- [x] 1.1 `AomMetricsAdapter` 接口扩展 `queryLogs(...)` 方法签名
- [x] 1.2 `AomMetricsAdapterImpl#queryLogs` 包装 SDK `listLogItems`，复用 `aom-readonly` 限流 + `huaweicloud-retryable` 重试（不新建 `AomLogAdapterImpl`）
- [x] 1.3 新增 `toListLogItemsSdkRequest` 私有方法：`withType("querylogs")` + `withPageSizeSize(String.valueOf(pageSize))` + `keyWord` 仅非 null 时 `setKeyWord`
- [x] 1.4 新增 DTO record：`AomQueryLogsRequest`（6 字段 + 紧凑构造默认值，pageSize=100 / isDesc=true）/ `AomQueryLogsResponse`（result / errorCode / errorMessage）
- [x] 1.5 `result` 以 String 透传，不在 adapter 层解析
- [x] 1.6 SDK 异常 → `ErrorCode` 映射（429/401-403/5xx/Timeout）

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `AomLogService`，5 项校验（category 白名单 / startTime+endTime 非空 / endTime>startTime / pageSize∈[1,1000]）后委托 adapter
- [x] 2.2 校验失败走 `InvalidParamException` → `INVALID_PARAM`

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `AomLogTool`，`@Tool(name="query_logs")`，可选参数显式 `@ToolParam(required = false)`
- [x] 3.2 description 明确 "Returns the raw upstream 'result' JSON string for downstream parsing" 且时间为 UTC milliseconds
- [x] 3.3 Tool 层仅 catch `SmartomException` 转 `ErrorResponse`（含 errorCode + upstreamTraceId 兜底日志）
- [x] 3.4 `McpServerConfig` 注册 `AomLogTool` 到 `ToolCallbackProvider`

## 4. 质量门禁

- [x] 4.1 Checkstyle 0 violations
- [x] 4.2 代码合入 master（提交 `4c346d6`）

## 5. 遗留项（本期未交付）

- [ ] 5.1 Service 层 UT（UT-S1~S6，`AomLogServiceTest`）
- [ ] 5.2 Adapter 层 UT（UT-A1~A5，`AomMetricsAdapterImplTest` 新增方法）
- [ ] 5.3 Tool 层 UT（UT-T1~T2，`AomLogToolTest`）
- [ ] 5.4 类型契约测试（TC-01/02：`QueryBodyParam` / `ListLogItemsResponse` 字段反射断言）
- [ ] 5.5 贵阳冒烟脚本 `scripts/smoke/smoke-query_logs.sh`（3 条）
- [ ] 5.6 Micrometer 指标 + README 使用示例
