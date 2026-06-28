## Why

存量回填，已于早期 commit 交付（原任务卡 `docs/tasks/T25-tool-error-unification.md`，状态 Done；依赖 T03 common 错误模型），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

本服务是 MCP Server，消费方是大模型 Agent，错误必须以**结构化 JSON**返回给模型，让模型自行决定下一步（重试 / 改参数 / 换工具 / 上报）。回填前存在三个问题：

1. **`RuntimeException` 裸抛**：全部工具只 catch `SmartomException`；任何非业务异常（入参映射 NPE、序列化错误等）会越过工具直接抛给 Spring AI MCP 框架，框架对工具异常只回传 `e.getMessage()` 一行文本（`isError=true`），Agent 拿不到 `error_code` / `retryable` / `hint`，无法决策。
2. **`retryable` 信息量不足**：布尔值只回答「能不能重试」，不回答「该怎么办」。同样不可重试，`UPSTREAM_AUTH_FAILED` 的最优动作是停止并上报，`INVALID_PARAM` 的最优动作是重读工具描述改参数——这类行动建议应由服务端在错误码单点显式给出。
3. **重复 try/catch**：每个工具各写一份相同的 catch + 日志样板，需收敛为单点，避免漂移。

本变更属**基础设施 / 横切（infra）**层：它统一 MCP 工具层的错误兜底与面向 Agent 的提示，**不新增任何对外 MCP 工具能力，也不改任何 `@Tool(name=...)` 契约名 / 入参 / 成功响应结构**，因此**不引入新的 capability spec，也不修改既有 capability spec**。各工具失败路径的 JSON 形状向后兼容地增加 `hint` 字段，错误码与 `retryable` 语义保持不变。

## What Changes

- `ErrorCode` 增加 `hint` 字段（英文，与 `@Tool` 描述语言一致）：每个错误码携带一句面向 Agent 的行动建议，`getHint()` 暴露；提示文案在此单点维护，工具层不得另写。
- `ErrorResponse` 增加 `hint` 组件（JSON 字段 `hint`），由 `of(...)` 工厂从 `ErrorCode` 自动派生填充（同时派生 `error_code` 与 `retryable`）。
- 新增 `ToolCallSupport`（`agentic-mcp`，包私有，与 `ToolValidations` 同级）：`static Object execute(String toolName, Supplier<?> action)` 统一执行工具主体并兜底异常——catch `SmartomException` → `ErrorResponse`（WARN 日志含 errorCode + upstreamTraceId + 耗时）；catch `RuntimeException` → `ErrorResponse(INTERNAL)`（ERROR 日志含全栈；返回给 Agent 的消息只含异常类名 + message，**不含堆栈**）；成功路径打 INFO 含工具名 + 耗时。
- 全部工具改为 `return ToolCallSupport.execute("<tool_name>", () -> ...)`，删除各自的 try/catch、LOG 字段及失效 import。
- **约束（防蔓延）**：不改工具入参 / 成功响应结构 / `@Tool` 描述；不依赖 Spring AI `ToolExecutionExceptionProcessor`（MCP server 路径不经过它）；不引入 Micrometer 埋点（另卡）。

## Capabilities

### New Capabilities

- 无（基础设施变更）。本变更不对外暴露任何新 MCP 工具能力，只统一既有工具层的错误兜底与提示派生。

### Modified Capabilities

- 无。各工具的对外契约名、入参与成功响应结构均不变；失败响应「只增 `hint` 字段、不改既有字段名与语义」属向后兼容补全，不构成 capability spec 的契约变更。

## Impact

- 模块：
  - `agentic-common`：`ErrorCode` 加 `hint` 字段 + `getHint()`；`ErrorResponse` record 加 `hint` 组件，`of(...)` 工厂从枚举派生。
  - `agentic-mcp`：新增 `ToolCallSupport`（包私有）；全部 `@Tool` 工具类改用 `ToolCallSupport.execute(...)`，删除各自 try/catch、LOG 字段与失效 import。
- 配置：无新增配置项；不动分页 / 限流（各 `*-readonly` RateLimiter 不变）/ 重试 / 错误码 → retryable 映射语义。
- 依赖方向：遵循 `mcp → monitoring → adapter → common`，本变更不新增模块、不改依赖边；`hint` 单点落在 `agentic-common` 的 `ErrorCode`。
- 兼容性：失败响应只增 `hint`、不改既有字段名（`error_code` / `error_message` / `upstream_trace_id` / `retryable`），对 Agent 向后兼容；唯一行为变化是非业务 `RuntimeException` 由「裸抛一行文本」改为「结构化 `INTERNAL` 错误（不含堆栈）」。
- 不涉及写操作；不新增工具；不改 request / 成功响应 DTO 与工具名。
