## Why

智能运维 Agent 通过 `query_traces` 找到一条慢 / 错误调用后，需要进一步看清这条 trace 在跨服务间是如何流转的、耗时集中在哪一段。`query_traces` 只产出扁平的 span 列表，无法直接回答"调用经过了哪些服务、谁调用谁、client 端还是 server 端耗时高"。因此需要一个按 traceId 重建调用链拓扑（节点 + 有向边 + client/server 时间）的下钻工具，作为 APM 发现链中根因分析的一环。

> 注：本变更为**存量回填**——工具已于早期 commit 交付（提交 `4c346d6`，2026-06-02 回填文档），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读工具 `get_service_topology`，封装 APM SDK `showTopology`，按单一入参 `traceId` 返回调用链拓扑 `{ global_trace_id, nodes[], lines[] }`。
- 响应将 SDK 嵌套的 `TraceTopologyLineInfo`（clientInfo / serverInfo）**拍平**为 `client_start_time` / `client_time_used` / `server_start_time` / `server_time_used` 四字段，避免 Agent 端再做嵌套展开。
- 复用 T10 既有的 `apmClient` Bean 与 `apm-readonly` 限流域；上游异常映射到统一 `ErrorCode`。

## Capabilities

### New Capabilities

- `get-service-topology`: 按 traceId 调用 APM `showTopology`，返回调用链节点（node_id / node_name / hint）与有向边（start/end node、span_id、client/server 时间、hint），供 `query_traces` 之后的拓扑下钻与根因分析。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-apm`（`ApmTraceAdapter` 加 `getTopology` 方法 + `ApmTraceAdapterImpl` 实现 + 4 个 DTO record：`ApmGetTopologyRequest` / `ApmGetTopologyResponse` / `ApmTopologyNode` / `ApmTopologyLine`）、`agentic-monitoring`（`ApmTraceService` 加 `getTopology` 方法）、`agentic-mcp`（新增 `ApmTopologyTool` + `McpServerConfig` 注册）。
- 配置：复用 `apm-readonly` RateLimiter（与 `query_traces` 共享 10 QPS），无新增配置项。
- 不涉及写操作；在 T10 既有的 `ApmTraceAdapter` / `ApmTraceService` 上加方法，不新建 Service。
