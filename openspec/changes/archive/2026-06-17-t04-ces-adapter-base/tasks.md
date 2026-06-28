> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T04-ces-adapter-base.md`，状态 Ready→Done）。已交付项勾选 `[x]`；原任务卡「不做（本期未交付）」项列在末尾未勾选。

## 1. 依赖与模块

- [x] 1.1 `agentic-adapter-ces/pom.xml` 引入 `com.huaweicloud.sdk:huaweicloud-sdk-ces`（版本经 parent pom `huaweicloud-sdk-bom` 管理，不写 version）
- [x] 1.2 `agentic-adapter-ces` 依赖 `agentic-common`（`${project.version}`）

## 2. Client 配置

- [x] 2.1 `config/CesClientConfig`（`@Configuration`）：`@Bean CesClient cesClient(HuaweiCloudProperties)`，用 `BasicCredentials`（AK/SK）+ `CesRegion.valueOf(region)` 装配，启动期 `LOG.info` 输出 region
- [x] 2.2 region 字符串 → `CesRegion` 映射统一到 SDK 期望格式（避免连字符 / 枚举名双重维护）

## 3. Adapter 接口与占位实现

- [x] 3.1 `CesMetricsAdapter` 空接口占位（Javadoc 约定：内部统一限流 / 重试 / 异常映射，对外抛 `SmartomException`）
- [x] 3.2 `CesMetricsAdapterImpl`（`@Component`）占位，构造注入 `CesClient` 与 `HuaweiCloudInvocation`（方法在 T05 加）

## 4. 测试与验收

- [x] 4.1 `CesClientConfigTest`：`@SpringBootTest` + `@TestPropertySource` 验证 `CesClient` Bean 非空
- [x] 4.2 `agentic-mcp` 启动能扫到 `CesMetricsAdapterImpl` Bean（验证 `scanBasePackages` 覆盖 `adapter.ces`）
- [x] 4.3 `mvn test -pl agentic-adapter-ces` 全绿 + Checkstyle 0 violations

## 5. 遗留项（本期未交付，属 T05）

- [ ] 5.1 `listMetrics` 等具体 CES 查询方法实现
- [ ] 5.2 CES 请求 / 响应 DTO 定义（随具体 tool 出）
- [ ] 5.3 HttpConfig / `HuaweiCloudInvocation` 层 10s 超时与限流重试实际接入
- [ ] 5.4 AOM / APM 对应 adapter 基座
