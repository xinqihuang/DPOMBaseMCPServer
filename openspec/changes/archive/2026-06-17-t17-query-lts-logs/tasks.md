> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T17-query-lts-logs.md`，状态 Done）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Service 层（agentic-monitoring）

- [x] 1.1 新增 `LtsLogService.queryLogs`，复用 T16 `LtsLogAdapter` 与 `LtsListLogsRequest` / `LtsListLogsResponse`
- [x] 1.2 实现 5 类输入校验（必填 id / 时间窗 start<end / limit∈[1,5000] / search_type∈{forwards,backwards} / line_num 与 cursor_time 成对）
- [x] 1.3 校验失败抛 `InvalidParamException` → `INVALID_PARAM`，关键路径短路不调用 adapter

## 2. MCP 工具层（agentic-mcp）

- [x] 2.1 新增 `LtsLogTool`，`@Tool(name="query_lts_logs")`，暴露 17 个 `@ToolParam`，纯透传（无业务校验）
- [x] 2.2 tool 层按位置构造 17 字段 `LtsListLogsRequest`，catch `SmartomException` → `ErrorResponse`，透传 `upstream_trace_id`
- [x] 2.3 `McpServerConfig` 注册 `LtsLogTool` 到 `toolCallbackProvider`

## 3. 测试

- [x] 3.1 `LtsLogServiceTest`（UT-S1~S8，覆盖 spec §3.2 全部校验规则 + 全合法委托）
- [x] 3.2 `LtsLogToolTest`（UT-T1~T3：success passthrough / INVALID_PARAM / UPSTREAM_THROTTLED 且 trace id 透传）

## 4. 遗留项（本期未交付）

- [ ] 4.1 `query_lts_log_context` tool（T18）
- [ ] 4.2 `list_log_groups` / `list_log_streams` 等发现性工具
- [ ] 4.3 Contract Test、冒烟脚本 `scripts/smoke/smoke-query_lts_logs.sh`
- [ ] 4.4 Micrometer 看板配置、README 使用示例
- [ ] 4.5 LTS 健康检查
- [ ] 4.6 结构化日志查询（`/struct-content/query` 路径）、客户端 SQL 语法校验
