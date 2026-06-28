## Why

存量回填，已于早期 commit 交付（原任务卡 `docs/tasks/T16-lts-adapter-base.md`，状态 In Progress→Done），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

智能运维 Agent 需要查询华为云 LTS（Log Tank Service）的日志内容与日志上下文，作为后续 LTS 相关 MCP 工具（T17–T18）的统一上游接入层。本变更为该能力新建独立的 adapter 基座子模块 `agentic-adapter-lts`，封装 LTS SDK 的 2 个只读日志查询方法（`ListLogs` / `ListLogContext`），并复用 `agentic-common` 既有的凭据工厂、限流 / 重试 / 异常映射通道。本变更属**基础设施 / 横切（infra）**层：只交付 adapter 层 + 单元测试，不落地 monitoring service，也不注册任何 MCP tool，因此**不引入新的对外能力（capability spec）**。

## What Changes

- 新建独立 Maven 子模块 `agentic-adapter-lts`（与 `agentic-adapter-ces` / `-aom` / `-apm` 同级），含 `pom.xml` / `src/main` / `src/test`。
- 新增 `LtsClientConfig`（`@Configuration`），注入单例 `LtsClient` Bean，复用 `HuaweiCloudClientFactory.credentialsWithProjectId(...)` + `defaultHttpConfig()`。
- 新增 `LtsLogAdapter` 接口 + `LtsLogAdapterImpl` 实现，封装 2 个只读方法 `listLogs` / `listLogContext`。
- 新增 5 个 DTO record：`LtsListLogsRequest` / `LtsListLogsResponse` / `LtsListLogContextRequest` / `LtsListLogContextResponse` / `LtsLogEntry`（SDK `LogContents` 重命名），实现 SDK 类型不外泄。
- `HuaweiCloudProperties` 新增字段 `ltsRegion`（LTS 在部分 region 不开服，与主 region 解耦，参考 APM 的 `apmRegion`），默认 `cn-north-9`。
- 根 `pom.xml` 的 `dependencyManagement` 新增 `huaweicloud-sdk-lts`（共用 `${huaweicloud-sdk.version}` = 3.1.177，不单独 override）；`agentic-adapter/pom.xml` 注册新子模块。
- `application.yml` 新增 `lts-readonly` RateLimiter（10 QPS）与 `huaweicloud.lts-region` 配置项。
- 单元测试：Client bean 装配、2 个方法成功路径、SDK 异常映射（429 / 401 / 5xx / Timeout）。

## Capabilities

### New Capabilities

- 无（基础设施变更）。本变更仅交付 adapter 接入层，不对外暴露 MCP 工具能力；对外能力由后续 T17–T18 在此基座上各自落地。

### Modified Capabilities

- 无。

## Impact

- 模块：新增 `agentic-adapter-lts`（`LtsClientConfig` + `LtsLogAdapter` / `LtsLogAdapterImpl` + 5 个 DTO record）；修改 `agentic-common`（`HuaweiCloudProperties` 加 `ltsRegion`）；修改 `agentic-adapter`（聚合 pom 注册子模块）；修改根 `pom.xml`（dependencyManagement 加 `huaweicloud-sdk-lts`）；修改 `agentic-mcp`（`application.yml` 加 `lts-readonly` 限流与 `lts-region`）。
- 配置：新增 `huaweicloud.lts-region`（默认 `cn-north-9`，可经 `HUAWEICLOUD_LTS_REGION` 覆盖）与 `lts-readonly` RateLimiter（10 QPS，与 ces-readonly / aom-readonly 一致）。
- 依赖方向：遵循 `mcp → monitoring → adapter → common`，本模块仅依赖 `agentic-common` 与 LTS SDK。
- 不涉及写操作；不改动既有 CES / AOM / APM adapter；不新增 monitoring service / MCP tool / 健康检查 probe。
