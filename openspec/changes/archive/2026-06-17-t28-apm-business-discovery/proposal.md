## Why

存量回填，工具已于早期 commit 交付。APM 诊断的所有下游工具都需要 `business_id` 与 `env_id`，但这两个标识必须运行时发现、禁止臆造（AGENTS.md §4.3）。本变更补齐 APM CMDB 发现链的第 0、1 步，使 Agent 能从"租户有哪些应用"一路解析到"环境与探针状态"。

## What Changes

- 新增 `list_apm_business`（v1 `ListBusiness`，无入参，列应用，输出 `business_id`）。
- 新增 `search_apm_application`（v1 `SearchApplication`，按 business 列组件/环境与探针计数，输出 `env_id`）。
- 前者接入 discovery 缓存（稳定目录），后者不缓存（探针为运行态）。

## Capabilities

### New Capabilities

- `list-apm-business`: 列出租户在 APM 注册的全部应用，提供后续所有 APM 工具所需的 `business_id`（发现链第 0 步）。
- `search-apm-application`: 列出指定应用下的组件与环境及探针在线/停止/离线计数，提供 `env_id`（发现链第 1 步）。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-apm`（2 个 adapter + 请求/响应 DTO）、`agentic-monitoring`（2 个 service）、`agentic-mcp`（2 个 tool）。
- 配置：复用 `apm-readonly` RateLimiter、`huaweicloud.apm-business-id` / `apm-region` 默认值、`apm.discovery-cache`（仅 list_apm_business）。
