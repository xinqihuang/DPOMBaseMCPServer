## Why

存量回填，已于早期 commit 交付（原任务卡 `docs/tasks/T24-ces-namespace-enum.md`，状态 Done（v2 修订）；v1 含服务端 fallback 版本已回滚，按 v2 PR `refactor(T24v2): drop server-side RDS fallback/resolved_namespace; expose SYS.RDS_MYSQL_CLUSTER in enum; keep cache` 合入），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

CES 三个查询/发现工具（`list_ces_metrics` / `query_ces_metric_data` / `batch_query_ces_metric_data`）的 `namespace` 入参原为自由 `String`，大模型只能凭先验拼 `SYS.ECS` 之类字面量，易拼错、无 Schema 约束。namespace 是全局固定目录（按 ADR-004 §4.3(a) 属「受控枚举」轴），适合在请求侧收敛为枚举，让合法取值经 Spring AI 反射直接出现在工具 JSON Schema 里；而 `metric_name` / 维度名按 namespace 变化且海量（§4.3(b)「运行时发现」轴），仍走已存在的 `list_ces_metrics` 发现链，不进枚举。

本变更属**基础设施 / 横切（infra）**层：它只收敛既有工具请求侧 namespace 入参的类型承载方式（自由 String → `CesNamespace` 枚举）、收紧工具描述里的发现链指引、为发现工具加缓存，**不引入任何对外的新工具能力，也不改任何 `@Tool(name=...)` 契约名与响应契约**。因此**不引入新的 capability spec、也不修改既有 capability spec**。

## What Changes

- 新增 `CesNamespace` 枚举（**15 个受支持取值**），每值携带 `SYS.*` 字面量 + 中文说明；`fromValue` 严格拒绝未知值，同时接受字面量写法（`SYS.ECS`）与常量名写法（`SYS_ECS`）。
- 三处工具入参 namespace 枚举化：`list_ces_metrics`（可选）、`query_ces_metric_data`、`batch_query_ces_metric_data`（工具入参项 `CesBatchMetricQueryInput`）。枚举 → String 的 `.getValue()` 映射集中在 Tool 层一处（`ToolValidations.cesNamespaceValue`）。
- 工具描述收紧：发现链调用顺序（选 namespace → `list_ces_metrics` 拿真实 metric_name+维度名 → 查数）、`metric_name`/维度名禁止编造、RDS 双 namespace（`SYS.RDS` 主备/单机 vs `SYS.RDS_MYSQL_CLUSTER` MySQL 集群版）的探测指引。
- `list_ces_metrics` 加 Caffeine TTL 缓存（`ces.discovery-cache`，默认 1d / 2000 条，key = 整个请求 record，失败/空结果不缓存，留 `@CacheEvict` 整体失效口）+ `application.yml` 配置。
- 非枚举 namespace 由框架（枚举反序列化失败）拒绝。
- **约束（防蔓延）**：枚举只动请求侧，响应 DTO 的 namespace 仍是无损 `String`、不加任何附加字段；adapter 请求/响应 DTO 一律保持 String、不感知枚举；服务端**不做任何隐式 namespace 替换/路由**（RDS 形态判定交由 Agent 自编排，v1 的 SYS_RDS fallback / `resolved_namespace` 已回滚，不得加回）；不枚举 `metric_name`、不做静态目录、不枚举全部华为云 namespace（只 15 个支持项）。

## Capabilities

### New Capabilities

- 无（基础设施变更）。本变更不对外暴露任何新 MCP 工具能力，只收敛既有 CES 工具请求侧 namespace 入参的类型承载（String → `CesNamespace` 枚举）+ 工具描述收紧 + 发现工具缓存。

### Modified Capabilities

- 无。三个 CES 工具的对外契约名与行为语义不变；namespace 入参从自由 String 收敛为受控枚举属请求侧输入校验收紧（非法值改由框架更早拒绝），响应契约「String namespace、无附加字段」保持不变，不构成 capability spec 的契约变更。

## Impact

- 模块（按依赖方向 `mcp → monitoring → adapter → common`）：
  - `agentic-mcp`：新增 `CesNamespace` 枚举；`CesMetricsTool` / `CesMetricDataTool` / `CesBatchMetricDataTool` 的 namespace 入参改用枚举（`CesBatchMetricQueryInput` 入参项同步）；`ToolValidations.cesNamespaceValue` 集中 `.getValue()` 映射；三工具描述文本收紧（发现链顺序 / 禁止编造 / RDS 探测指引）。
  - `agentic-adapter-ces`：adapter 请求/响应 DTO 保持 String，不感知枚举（无改动或仅缓存注解相关）。
- 缓存：`list_ces_metrics` 链路新增 Caffeine 缓存 `ces.discovery-cache`（默认 1d / 2000 条，key=请求 record，失败/空不缓存，`@CacheEvict` 整体失效口）。
- 配置：新增 `ces.discovery-cache` 相关 `application.yml` 配置项；复用既有 `ces-readonly` RateLimiter（不新增限流域）。
- 兼容性：响应侧无任何变化（namespace 仍 String、无新增字段），既有契约测试保持绿；唯一对外可见变化是工具 JSON Schema 中 namespace 出现枚举取值约束、非法值更早被框架拒绝。
- 不涉及写操作；不改响应 DTO；不新增工具；不改 `@Tool(name=...)`；服务端无隐式 namespace 路由。
