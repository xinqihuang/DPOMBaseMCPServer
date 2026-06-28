## ADDED Requirements

### Requirement: CES SDK Client Bean 装配

系统 SHALL 通过 `CesClientConfig`（`@Configuration`）暴露唯一的 `CesClient` Bean，作为所有 CES 查询能力的 SDK 入口。该 Bean MUST 基于 `HuaweiCloudProperties` 的 ak / sk 构造 `BasicCredentials`（项目级凭证，非 `GlobalCredentials`），并按 `region` 解析的 `CesRegion` 装配；Bean 初始化时 SHALL 输出包含 region 的 INFO 日志。

#### Scenario: Spring 启动期成功装配
- **GIVEN** 配置了 `huaweicloud.ak` / `huaweicloud.sk` / `huaweicloud.region`
- **WHEN** Spring context 启动并加载 `CesClientConfig`
- **THEN** `CesClient` Bean SHALL 被创建且非空
- **AND** 启动日志 SHALL 含已初始化的 region

#### Scenario: region 字符串映射到 SDK 枚举
- **GIVEN** `huaweicloud.region` 为 SDK 期望格式的枚举名（如 `CN_SOUTHWEST_2`）
- **WHEN** 装配 `CesClient`
- **THEN** 系统 SHALL 用 `CesRegion.valueOf(region)` 解析并装配，不在配置与代码间维护两套格式

### Requirement: CesMetricsAdapter 接口契约

系统 SHALL 定义 `CesMetricsAdapter` 接口作为 CES 监控查询能力的统一抽象，并提供 `@Component` 占位实现 `CesMetricsAdapterImpl`（构造注入 `CesClient` 与 `HuaweiCloudInvocation`）。该接口的所有未来方法 MUST 在内部统一应用限流 / 重试 / 异常映射，对外只抛出 `SmartomException`，SDK 异常不得透传到 service / MCP 层。本期接口为空契约，不实现任何具体查询方法。

#### Scenario: 占位实现可被容器扫描装配
- **GIVEN** `agentic-mcp` 以 `scanBasePackages="com.huawei.smartom.agentic"` 启动
- **WHEN** 容器扫描组件
- **THEN** `CesMetricsAdapterImpl` Bean SHALL 被发现并装配，依赖 `CesClient` 与 `HuaweiCloudInvocation` 注入成功

#### Scenario: 异常对外语义约定
- **WHEN** 后续方法（T05）内部上游调用失败
- **THEN** 对外 SHALL 抛出 `SmartomException`（携带 `ErrorCode` 与可空 `upstreamTraceId`）
- **AND** 原始 SDK `ServiceResponseException` SHALL NOT 透传到上层
