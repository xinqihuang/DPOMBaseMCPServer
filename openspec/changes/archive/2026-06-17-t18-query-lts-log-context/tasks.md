> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T18-query-lts-log-context.md`，状态 Done）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Service 层（agentic-monitoring）

- [x] 1.1 新增 `LtsLogContextService.queryContext`，对 `request==null` 直接拒绝
- [x] 1.2 实现 spec §3.2 七条校验：必填项非空 / 首次模式与 scroll 模式互斥 / `line_num`+`cursor_time` 成对 / size ∈ [0,500] / 前后向不同时为 0
- [x] 1.3 校验通过后委托 `LtsLogAdapter.listLogContext(...)`，复用 T16 DTO

## 2. MCP 工具层（agentic-mcp）

- [x] 2.1 新增 `LtsLogContextTool`，`@Tool(name="query_lts_log_context")`，暴露 7 个 `@ToolParam`（description 含 "use this after query_lts_logs"）
- [x] 2.2 按位置装配 `LtsListLogContextRequest`（7 字段对齐 record 顺序）
- [x] 2.3 捕获 `SmartomException` → `ErrorResponse`，透传 errorCode / message / upstreamTraceId
- [x] 2.4 `McpServerConfig` 注册 `LtsLogContextTool`（紧跟 `LtsLogTool`）

## 3. 测试

- [x] 3.1 `LtsLogContextServiceTest`（UT-S1~S9：null / 两个必填空白 / 双模式缺失 / 单边 line_num / size 越界 / 前后向同 0 / 纯 scroll / 全合法）
- [x] 3.2 `LtsLogContextToolTest`（UT-T1~T3：全合法透传 / `InvalidParamException`→`INVALID_PARAM` / `UpstreamException(429)`→`UPSTREAM_THROTTLED` 且透传 trace id）

## 4. 文档

- [x] 4.1 更新 `docs/tasks/README.md` 加入 T18 行（状态 Done）

## 5. 遗留项（本期未交付）

- [ ] 5.1 冒烟脚本 `scripts/smoke/smoke-query_lts_log_context.sh`
- [ ] 5.2 Micrometer 指标看板配置
- [ ] 5.3 README 使用示例
- [ ] 5.4 LTS 健康检查探针
- [ ] 5.5 `scroll_id` 作为独立输出字段（当前复用尾行 line_num+cursor_time 翻页）
- [ ] 5.6 跨流上下文 / 多目标合并
- [ ] 5.7 自动 pre-fetch：`query_lts_logs` 直接附带前后 N 条上下文
