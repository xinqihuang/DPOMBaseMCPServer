## Why

存量回填，工具已于早期 commit 交付。智能运维 Agent 需要查询华为云 CES（Cloud Eye Service）的指标与告警数据，这些能力（`list_ces_metrics` / `query_ces_metric_data` 等，属 T05）必须建立在一个统一的 CES SDK 调用基座之上：没有集中配置的 `CesClient` Bean 与统一的 `CesMetricsAdapter` 接口契约，后续每个 CES 查询工具都会各自构造凭证、各自处理 region / endpoint，凭证装配与限流重试编排无法收敛。本变更补齐 CES adapter 基座的 OpenSpec 规格，把这层横切基础设施纳入 spec-driven 管理。本卡只搭基座，不实现任何具体查询方法（属 T05）。

## What Changes

- 新增 `agentic-adapter-ces` 模块的运行基座：
  - 引入华为云 `huaweicloud-sdk-ces` 依赖（版本经 parent pom 的 `huaweicloud-sdk-bom` 统一管理），并依赖 `agentic-common`。
  - `CesClientConfig`（`@Configuration`）提供 `CesClient` Bean：基于 `HuaweiCloudProperties` 的 AK/SK 构造 `BasicCredentials`，按 `region` 解析 `CesRegion`，用 `CesClient.newBuilder()` 装配，启动期日志输出已初始化的 region。
  - `CesMetricsAdapter` 接口（本期为空契约，方法在 T05 加），约定所有方法内部统一应用限流 / 重试 / 异常映射，对外抛出 `SmartomException`。
  - `CesMetricsAdapterImpl` 占位实现（`@Component`，注入 `CesClient` 与 `HuaweiCloudInvocation`，方法在 T05 加）。
  - `CesClientConfigTest`：Spring context 启动期验证 `CesClient` Bean 成功装配且非空。
- 不实现 `listMetrics` 等具体方法、不定义任何 DTO、不接入 AOM / APM（均属后续任务）。

## Capabilities

### New Capabilities

- `ces-adapter-base`（基础设施能力，非 MCP 工具）：CES adapter 基座，提供 `CesClient` Bean 装配与 `CesMetricsAdapter` 接口契约。本模块不直接对外暴露 MCP 工具，仅为 T05 及之后的 CES 查询工具提供 SDK 入口与统一调用约定。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：新增 `agentic-adapter-ces`（`config/CesClientConfig`、`CesMetricsAdapter` 接口、`CesMetricsAdapterImpl` 占位实现）；依赖 `agentic-common`（`HuaweiCloudProperties` / `HuaweiCloudInvocation` / `SmartomException`）。
- 配置：复用既有 `huaweicloud.ak` / `huaweicloud.sk` / `huaweicloud.region` 配置，无新增配置项；HttpConfig 本期不设置，沿用 SDK 默认超时（超时控制在 `HuaweiCloudInvocation` 层做，属 T05）。
- 依赖：`agentic-adapter-ces` 首次引入 `huaweicloud-sdk-ces`；`agentic-mcp` 启动需能扫到 `CesMetricsAdapterImpl` Bean（验证 `scanBasePackages` 覆盖 `com.huawei.smartom.agentic.adapter.ces`）。
- 不涉及任何写操作；不改动 AOM / APM adapter。
