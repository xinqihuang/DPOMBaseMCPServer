## Context

存量基础设施回填。原始任务卡：`docs/tasks/T04-ces-adapter-base.md`（状态 Ready，估时 0.3d，依赖 T03，后置 T05）。本文承载 CES adapter 基座的架构决策：华为云 CES SDK 的类 / 版本钉定、Bean 装配与凭证选型、region / endpoint 映射约定、依赖方向，以及 AI 在搭 SDK client 时高频踩坑的细节（Region 枚举命名、BasicCredentials vs GlobalCredentials、HttpConfig 超时）。本卡只搭基座、不暴露任何业务 MCP 工具，故无 delta spec；具体查询方法、DTO、时间参数格式等契约在 T05 落地。

## Goals / Non-Goals

**Goals:**
- 提供一个集中配置、Spring context 启动期即可成功装配的 `CesClient` Bean，作为所有 CES 查询工具的唯一 SDK 入口。
- 固化 `CesMetricsAdapter` 接口契约：方法内部统一应用限流 / 重试 / 异常映射，对外只抛 `SmartomException`，不让 SDK 异常透传。
- 引入并钉定 `huaweicloud-sdk-ces` 依赖与 region / 凭证装配约定，使 T05 的查询方法开箱即用。
- 用 `CesClientConfigTest` 验证 Bean 装配链路打通，并使 `agentic-mcp` 能扫到 `CesMetricsAdapterImpl`。

**Non-Goals:**
- `listMetrics` / `listMetricData` 等任何具体 CES 查询方法实现 —— 属 T05。
- 任何 CES DTO（请求 / 响应投影）定义 —— 随 T05 具体 tool 出。
- AOM / APM adapter 的对应基座 —— 属后续任务。
- HttpConfig 超时调优、限流 / 重试在调用路径上的实际拦截 —— 属 T05（超时在 `HuaweiCloudInvocation` 层做，非 HttpConfig）。
- 任何 MCP 工具暴露与 `McpServerConfig` 注册。

## Decisions

### SDK 类与版本

- **SDK 依赖**：`com.huaweicloud.sdk:huaweicloud-sdk-ces`，版本经 parent pom 的 `huaweicloud-sdk-bom`（T01 纳入管理，3.1.x）统一约束，子模块只声明 `groupId/artifactId` 不写 version。artifactId 以华为云 Maven 仓库实际为准。
- **SDK Client 类**：`com.huaweicloud.sdk.ces.v1.CesClient`。
- **Region 类**：`com.huaweicloud.sdk.ces.v1.region.CesRegion`。常量命名以 SDK 源码为准（如 `CN_SOUTHWEST_2`），不得凭印象写 `CN_SOUTH_WEST_2` 之类；字段缺失 / 编译失败先怀疑 SDK 版本与常量名。

### Bean 装配与凭证

- `CesClientConfig`（`@Configuration`）暴露 `@Bean CesClient cesClient(HuaweiCloudProperties properties)`。
- 凭证：CES 是项目级（region 级）服务，用 `com.huaweicloud.sdk.core.auth.BasicCredentials`（`.withAk` / `.withSk`），**不是** `GlobalCredentials`（后者用于全局服务，如 IAM）。
- region 解析：`HuaweiCloudProperties.region` 字段为 `String`（T03 定义），在 `CesClientConfig` 内做到 `CesRegion` 的映射。约定统一采用 SDK 期望的格式（即 `CesRegion.valueOf(...)` 能解析的枚举名，如 `CN_SOUTHWEST_2`），避免「配置写连字符串、代码写枚举名」的双重维护。
- 装配：`CesClient.newBuilder().withCredential(credentials).withRegion(CesRegion.valueOf(region)).build()`；启动期 `LOG.info("CesClient initialized for region=...")`。
- `CesMetricsAdapterImpl` 为 `@Component`，构造注入 `CesClient` 与 `agentic-common` 的 `HuaweiCloudInvocation`（统一限流 / 重试 / 异常映射包装器），本期方法体为空。

### 接口契约（行为约定，方法在 T05 加）

- `CesMetricsAdapter` 为空接口占位，Javadoc 约定：所有未来方法在内部应用限流、重试、异常映射，对外抛出 `SmartomException`（携带 `ErrorCode` 与可空 `upstreamTraceId`），SDK 的 `ServiceResponseException` 不得透传到 service / MCP 层。
- 限流域沿用 T01 占位的 `ces-readonly`（10 QPS）；重试沿用 `huaweicloud-retryable`（仅 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，最多 3 次指数退避）—— 本期仅约定，不在代码路径拦截。

### 依赖方向

- `agentic-adapter-ces` → `agentic-common`（用 `HuaweiCloudProperties` / `HuaweiCloudInvocation` / `SmartomException` / `ErrorCode`）。
- 不依赖 `agentic-monitoring` / `agentic-mcp` / 其他 adapter；被 `agentic-monitoring`（T05 的 CES service）反向依赖。

### 测试

- `CesClientConfigTest`：`@SpringBootTest(classes={CesClientConfig.class, HuaweiCloudProperties.class})` + `@TestPropertySource`（`huaweicloud.ak/sk/region`），断言 `@Autowired CesClient` 非空。验证目标是 Bean 装配链路打通，不发起真实上游调用。

## Risks / Trade-offs

- **Region 枚举命名陷阱**：`CesRegion` 常量名（`CN_SOUTHWEST_2` vs `CN_SOUTH_WEST_2`）AI 高频写错，须以 SDK 源码为准；`CesRegion.valueOf(properties.getRegion())` 要求配置值与枚举名严格一致，否则启动期 `IllegalArgumentException`。
- **配置格式双重维护**：`huaweicloud.region` 既可能写 `cn-southwest-2` 也可能写 `CN_SOUTHWEST_2`，须在 `CesClientConfig` 统一到 SDK 期望格式，避免连字符 / 下划线不一致导致 valueOf 失败。
- **凭证类型选错**：CES 用 `BasicCredentials` 而非 `GlobalCredentials`，选错会导致鉴权失败。
- **HttpConfig 默认超时偏长**：SDK 默认传输超时约 60s，本期不设 HttpConfig；真正的 10s 超时控制将在 T05 的 `HuaweiCloudInvocation` 层做，不在 HttpConfig，避免双处配置。
- **scanBasePackages 覆盖**：`agentic-mcp` 启动须能扫到 `com.huawei.smartom.agentic.adapter.ces` 下的 `CesMetricsAdapterImpl`，否则 T05 接入查询工具时 Bean 静默缺失，难排查 —— 在基座阶段就用 `CesClientConfigTest` 与 mcp 启动验证锁定。
- **artifactId 不确定性**：`huaweicloud-sdk-ces` 的最终 artifactId / 是否走 BOM 以华为云 Maven 仓库为准，引入时需核对，避免提前钉死错误坐标。
