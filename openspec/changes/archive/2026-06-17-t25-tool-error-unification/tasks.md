> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T25-tool-error-unification.md`，状态 Done）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. common 错误模型（agentic-common）

- [x] 1.1 `ErrorCode` 增加 `hint` 字段（英文，与 `@Tool` 描述语言一致），构造改为 `(boolean retryable, String hint)`，新增 `getHint()`（保证非空 / 非空串）
- [x] 1.2 为 6 个错误码各写一句面向 Agent 的行动建议：`INVALID_PARAM` / `UPSTREAM_THROTTLED` / `UPSTREAM_AUTH_FAILED` / `UPSTREAM_ERROR` / `TIMEOUT` / `INTERNAL`（提示文案单点在此维护，工具层不得另写）
- [x] 1.3 `ErrorResponse` record 增加 `hint` 组件（JSON 字段 `hint`），`of(...)` 工厂从 `ErrorCode` 同时派生 `error_code` / `retryable` / `hint`

## 2. 统一调用包装器（agentic-mcp）

- [x] 2.1 新增 `ToolCallSupport`（与 `ToolValidations` 同级，包私有 `final` + 私有构造）：`static Object execute(String toolName, Supplier<?> action)`
- [x] 2.2 catch `SmartomException` → `ErrorResponse.of(...)`；WARN 日志含 errorCode + upstreamTraceId + 耗时（不打全栈）
- [x] 2.3 catch `RuntimeException` → `ErrorResponse.of(INTERNAL, ...)`；ERROR 日志含全栈；返回 Agent 的消息只含异常类名 + message（不含堆栈）；catch 顺序 `SmartomException` 先于 `RuntimeException`
- [x] 2.4 成功路径打 INFO 含工具名 + 耗时
- [x] 2.5 Javadoc 说明 `catch (RuntimeException)` 为 MCP 进程边界兜底、属 CLAUDE.md §3.4 有意例外

## 3. 全量工具改写（agentic-mcp）

- [x] 3.1 全部工具改为 `return ToolCallSupport.execute("<tool_name>", () -> ...)`，`<tool_name>` 与各自 `@Tool(name=...)` 一致
- [x] 3.2 删除各工具自有的 try/catch、LOG 字段及失效 import
- [x] 3.3 不改任何工具入参 / 成功响应结构 / `@Tool` 描述

## 4. 测试

- [x] 4.1 `ToolCallSupportTest`：成功透传 / `SmartomException` → 结构化错误（含 hint、retryable、traceId）/ `RuntimeException` → `INTERNAL`（消息不含堆栈）
- [x] 4.2 `ErrorCodeTest` 补 hint 非空断言
- [x] 4.3 现有各工具 UT 全绿（错误路径断言随结构化兜底同步调整）
- [x] 4.4 全量 `mvn test` + `mvn checkstyle:check` 通过

## 5. 遗留项（本期未交付）

- [ ] 5.1 Micrometer `mcp_tool_invocation{tool="..."}` 埋点（另卡，本卡不引入）
- [ ] 5.2 Spring AI `ToolExecutionExceptionProcessor` 不经 MCP server 路径的验证留痕（McpToolUtils）
