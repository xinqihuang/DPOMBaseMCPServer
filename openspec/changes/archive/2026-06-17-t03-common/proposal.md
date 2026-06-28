## Why

存量回填，工具已于早期 commit 交付。`agentic-common` 是所有 adapter / monitoring / mcp 模块的底层基座：没有统一的错误码、异常、限流重试编排和上游异常映射，各 adapter 会各自实现一套调用样板，错误语义与重试策略不一致，MCP tool 也无法向 Agent 返回结构化、可判定 `retryable` 的错误。本变更补齐 OpenSpec 规格，把这层横切基础设施纳入 spec-driven 管理。

## What Changes

- 新增 `com.huawei.smartom.agentic.common` 模块，提供：
  - `ErrorCode` 枚举（含 `retryable` 属性）与 `ErrorResponse` record（MCP tool error 返回结构）。
  - `SmartomException` 业务异常基类及子类 `InvalidParamException` / `UpstreamException`（携带 `errorCode` 与可空 `upstreamTraceId`）。
  - `HuaweiCloudInvocation` 调用包装器：统一封装"调华为云 SDK + 限流 + 重试 + 异常映射 + 日志"，`ResilienceConfig` 提供 RateLimiter / Retry 注册表配置。
  - `SdkExceptionMapper`：华为云 SDK 异常（`ServiceResponseException` 等）→ `ErrorCode` 映射。
  - `HuaweiCloudCredentialsHealthIndicator`：启动期 AK/SK 非空校验，纳入 readiness group。
- 将 `HuaweiCloudProperties` 从 `agentic-mcp` 迁移到 `agentic-common.config`，并更新 mcp 模块 import。

## Capabilities

### New Capabilities

- 无对外 MCP 工具（基础设施变更）。本模块不直接暴露工具，仅为上层 adapter / tool 提供横切能力；OpenSpec 以单一基础设施能力 `common-infrastructure` 承载错误码 / 异常 / 限流重试 / SDK 异常映射 / 健康检查的可测试契约。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：新增 `agentic-common`（error / exception / resilience / sdk / health / config 子包）；`agentic-mcp` 移除本地 `HuaweiCloudProperties` 并改依赖 common。
- 配置：`application.yml` 将 `huaweiCloudCredentials` 纳入 `management.endpoint.health.group.readiness.include`；Resilience4j RateLimiter / Retry 实例配置。
- 依赖方向：`common` 为最底层，被所有 adapter / monitoring / mcp 依赖，自身不依赖任何业务模块。
