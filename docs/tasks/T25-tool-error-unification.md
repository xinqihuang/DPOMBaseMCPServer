# T25 — MCP 工具层错误处理统一：RuntimeException 兜底 + 面向 Agent 的 hint

> 状态: **Done** · 估时: 0.5d · 依赖: T03（common 错误模型）· 影响: agentic-common、agentic-mcp 全部 20 个工具

## 背景与动机

本服务是 MCP Server，消费方是大模型 Agent。错误必须以**结构化 JSON**返回给模型，让模型自行决定下一步（重试 / 改参数 / 换工具 / 上报）。现状两个问题：

1. **RuntimeException 裸抛**：20 个工具只 catch `SmartomException`。任何非业务异常（映射 NPE、序列化错误等）会抛给 Spring AI MCP 框架，框架只返回 `e.getMessage()` 一行文本（isError=true），模型拿不到 error_code / retryable，无法决策。
2. **`retryable` 信息量不足**：布尔值只回答「能不能重试」，不回答「该怎么办」。模型对 `UPSTREAM_AUTH_FAILED` 最优行为是停止并上报，对 `INVALID_PARAM` 是重读工具描述修参数——这些应由服务端显式提示。
3. 附带收益：21 处重复 try/catch 收敛为单点。

## 设计

1. **`ErrorCode` 增加 `hint` 字段**（英文，与 @Tool 描述语言一致）：每个错误码携带一句面向 Agent 的行动建议，`getHint()` 暴露。单一来源，禁止在工具层另写提示文案。
2. **`ErrorResponse` 增加 `hint` 组件**（JSON 字段 `hint`），由 `of(...)` 工厂从 `ErrorCode` 自动填充。
3. **新增 `ToolCallSupport`**（agentic-mcp，包私有，与 `ToolValidations` 同级）：
   - `static Object execute(String toolName, Supplier<?> action)`
   - catch `SmartomException` → `ErrorResponse`（WARN 日志含 errorCode + upstreamTraceId + 耗时）
   - catch `RuntimeException` → `ErrorResponse(INTERNAL)`（ERROR 日志含全栈；返回给模型的消息只含异常类名 + message，**不含堆栈**）
   - 成功路径打 INFO 含工具名 + 耗时
   - 注：catch `RuntimeException` 是 MCP 进程边界的兜底，属 CLAUDE.md §3.4「明确类型」的有意例外，已在 Javadoc 说明。
4. **20 个工具**全部改为 `return ToolCallSupport.execute("<tool_name>", () -> ...)`，删除各自的 try/catch、LOG 字段及失效 import。

## 不做

- ❌ 不改工具入参 / 成功响应结构 / @Tool 描述
- ❌ 不依赖 Spring AI `ToolExecutionExceptionProcessor`（MCP server 路径不经过它，验证见 spring-ai McpToolUtils）
- ❌ 不在本卡引入 Micrometer 埋点（另卡）

## 验收

- [x] `ToolCallSupportTest`：成功透传 / SmartomException → 结构化错误（含 hint、retryable、traceId）/ RuntimeException → INTERNAL（消息不含堆栈）
- [x] `ErrorCodeTest` 补 hint 非空断言；现有工具 UT 全绿（错误路径断言不变）
- [x] 全量 `mvn test` + `mvn checkstyle:check` 通过
