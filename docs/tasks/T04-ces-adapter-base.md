# T04 — CES Adapter 基座

> 状态: Ready · 估时: 0.3d · 依赖: T03 · 后置: T05

## 目标

实现 `com.huawei.smartom.agentic.adapter.ces` 模块的基座：CES SDK Client 配置 + adapter 接口定义。**不实现任何具体的查询方法**，那是 T05。

## 范围

**做**:
- 引入华为云 CES SDK 依赖
- 配置 `CesClient` Bean（AK/SK + region + endpoint）
- 定义 `CesMetricsAdapter` 接口（暂时为空）
- 占位的实现类骨架
- Client Bean 的单元测试（启动期 Bean 装配正常）

**不做**:
- `listMetrics` 等具体方法实现（T05）
- DTO 定义（在 T05 跟随具体 tool 出）
- AOM / APM 的对应工作（后续任务）

## 产物清单

```
agentic-adapter-ces/
  pom.xml                                    ← 加 huaweicloud-sdk-ces 依赖
  src/main/java/com/huawei/smartom/agentic/adapter/ces/
    config/
      CesClientConfig.java                   ← @Configuration, CesClient bean
    CesMetricsAdapter.java                   ← interface (空)
    CesMetricsAdapterImpl.java               ← class (空，仅占位 @Component)
  src/test/java/com/huawei/smartom/agentic/adapter/ces/
    config/CesClientConfigTest.java          ← Spring context 启动测试
```

## 关键技术要求

### pom.xml 依赖

```xml
<dependency>
    <groupId>com.huaweicloud.sdk</groupId>
    <artifactId>huaweicloud-sdk-ces</artifactId>
    <version>${huaweicloud-sdk.version}</version>
</dependency>

<dependency>
    <groupId>com.huawei.smartom.agentic</groupId>
    <artifactId>agentic-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

注意：huaweicloud-sdk-ces 这个 artifactId 以华为云 Maven 仓库实际为准。SDK 一般也提供 BOM (`huaweicloud-sdk-bom`)，如果用 BOM 就不写 version。

### CesClientConfig

```java
@Configuration
public class CesClientConfig {

    private static final Logger log = LoggerFactory.getLogger(CesClientConfig.class);

    @Bean
    public CesClient cesClient(HuaweiCloudProperties properties) {
        BasicCredentials credentials = new BasicCredentials()
                .withAk(properties.getAk())
                .withSk(properties.getSk());

        String endpoint = CesRegion.valueOf(properties.getRegion()).getEndpoint();
        // 或 CesRegion.CN_SOUTHWEST_2 取常量；以 SDK 实际 API 为准

        CesClient client = CesClient.newBuilder()
                .withCredential(credentials)
                .withRegion(CesRegion.valueOf(properties.getRegion()))
                .build();

        LOG.info("CesClient initialized for region={}", properties.getRegion());
        return client;
    }
}
```

### CesMetricsAdapter (interface 占位)

```java
package com.huawei.smartom.agentic.adapter.ces;

/**
 * 封装华为云 CES (Cloud Eye Service) 监控查询能力。
 *
 * <p>所有方法在内部应用限流、重试、异常映射，对外抛出 {@link SmartomException}。
 */
public interface CesMetricsAdapter {
    // 方法在 T05 加
}
```

### CesMetricsAdapterImpl (空骨架)

```java
package com.huawei.smartom.agentic.adapter.ces;

@Component
public class CesMetricsAdapterImpl implements CesMetricsAdapter {

    private static final Logger log = LoggerFactory.getLogger(CesMetricsAdapterImpl.class);

    private final CesClient cesClient;
    private final HuaweiCloudInvocation invocation;

    public CesMetricsAdapterImpl(CesClient cesClient, HuaweiCloudInvocation invocation) {
        this.cesClient = cesClient;
        this.invocation = invocation;
    }

    // 方法在 T05 加
}
```

## 单元测试要求

### CesClientConfigTest

```java
@SpringBootTest(classes = {
        CesClientConfig.class,
        HuaweiCloudProperties.class
})
@TestPropertySource(properties = {
        "huaweicloud.ak=test-ak",
        "huaweicloud.sk=test-sk",
        "huaweicloud.region=CN_SOUTHWEST_2"
})
class CesClientConfigTest {

    @Autowired
    private CesClient cesClient;

    @Test
    @DisplayName("CesClient bean is created and non-null")
    void cesClientBeanIsCreated() {
        assertThat(cesClient).isNotNull();
    }
}
```

## 验收标准

- [ ] `mvn test -pl agentic-adapter-ces` 全绿
- [ ] CesClient Bean 在 Spring 启动时成功初始化
- [ ] `agentic-mcp` 模块启动时能扫到 CesMetricsAdapterImpl Bean（验证 scanBasePackages 设置正确）
- [ ] Checkstyle 0 violations

## AI 易错点提醒

1. **Region 枚举的写法**：华为云 SDK 的 Region 类是 `com.huaweicloud.sdk.ces.v1.region.CesRegion`。常量命名可能是 `CN_SOUTHWEST_2`、也可能是 `CN_SOUTH_WEST_2`，**以 SDK 源码为准**，不要凭印象写。
2. **配置文件里 `huaweicloud.region`**：可以是字符串如 `"cn-southwest-2"`，也可以是枚举名 `"CN_SOUTHWEST_2"`。**T03 里 `HuaweiCloudProperties` 字段是 String，T04 在 `CesClientConfig` 里做映射**——建议统一用 SDK 期望的格式（去查 SDK），避免双重维护。
3. **BasicCredentials 还是 GlobalCredentials**：CES 是项目级服务用 `BasicCredentials`；全局服务用 `GlobalCredentials`。CES 用前者。
4. **HttpConfig 暂不设置**：默认超时太长（一般 60s），但本期先用默认，T05 里如果调试超时再调（spec 要求 10s 超时是在 HuaweiCloudInvocation 层做，不是 HttpConfig）。

## 完成后

PR：`feat(T04): CES adapter skeleton with CesClient bean`。
