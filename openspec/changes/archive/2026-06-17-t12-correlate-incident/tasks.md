> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T12-correlate-incident.md`，提交 `4c346d6`，状态 Done）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. DTO 层（agentic-monitoring / correlate 包）

- [x] 1.1 新增 `CorrelateIncidentRequest` record（7 字段：时间窗 + 5 过滤项）
- [x] 1.2 新增 `CorrelateIncidentResponse` record（4 分支结果，顺序 cesAlarms → aomLogs → apmTraces → apmTopology）
- [x] 1.3 新增 `CorrelateBranchResult` record（三态 skipped / data / error + 静态工厂 `ofSkipped` / `success` / `failure`）
- [x] 1.4 新增 `CorrelateError` record（`errorCode` / `retryable` / `message` / `upstreamTraceId`）

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `CorrelateIncidentService`，时间窗 4 项校验（request null / start null / end null / end ≤ start → INVALID_PARAM）
- [x] 2.2 虚拟线程扇出：try-with-resources `newVirtualThreadPerTaskExecutor()` + 4 个 `CompletableFuture` + `allOf().join()` 聚合
- [x] 2.3 跳过策略在编排层各分支内部判断（logCategory / apmTraceId+apmSource / apmTraceId）
- [x] 2.4 `runBranch` 单点 catch `SmartomException` → `CorrelateError`（不 catch Exception），WARN 日志含 errorCode + upstreamTraceId
- [x] 2.5 `waitFor` 兜底：`ExecutionException` / `InterruptedException` → INTERNAL failure
- [x] 2.6 下游入参装配：CES 时间转 `String.valueOf`、AOM 保持毫秒、APM 按 traceId/source

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `CorrelateIncidentTool`，`@Tool(name="correlate_incident")` + description（强调分支独立 / skipped 语义）
- [x] 3.2 `McpServerConfig` 注册到 `ToolCallbackProvider`

## 4. 质量

- [x] 4.1 Checkstyle 0 violations（含 Lambda 参数名 ≥ 2 字符、`}` 与 catch/else 换行）
- [x] 4.2 MCP Inspector 验证 tool 注册与 description

## 5. 遗留项（本期未交付）

- [ ] 5.1 Service 层 UT-01~08（4 分支 × 3 状态 × 2 异常矩阵，`CorrelateIncidentServiceTest`）
- [ ] 5.2 Tool 层 UT（输入校验 + ErrorResponse 转换）
- [ ] 5.3 Micrometer 分支级指标（当前仅整体 tool 级）
- [ ] 5.4 贵阳冒烟脚本 `scripts/smoke/smoke-correlate_incident.sh`（3 条）
- [ ] 5.5 README 使用示例
