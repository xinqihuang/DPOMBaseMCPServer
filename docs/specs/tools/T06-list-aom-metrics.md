# T06 — 实现 list_aom_metrics

> 状态: **Draft（待 spec v0.2 主干合入后开始）** · 估时: 1.5d · 依赖: T01-T03（common 基础设施已就位）· 后置: T07 query_aom_metric_data · 关联 spec: `docs/specs/tools/list_aom_metrics.md` v0.2

## 目标

按 spec v0.2 实现 `list_aom_metrics` MCP tool，含 AOM adapter 模块完整填充（之前只是占位）、业务编排、MCP tool 注册、单元测试、类型契约测试、冒烟脚本，以及 §7 提到的"配套基础设施变更"（projectId 配置、aom-readonly 限流配置、health indicator 升级）。

## 范围

**做**:

- spec §6 列出的全部测试用例（UT-01~20、TC-01~05、3 个 smoke 用例）
- spec §7 配套基础设施变更
- adapter 层完整 DTO + 接口 + 实现
- 业务层 `AomMetricsService` 输入校验
- MCP tool 注册 + 错误转换
- 部署后冒烟脚本

**不做**（防止任务蔓延）:

- ❌ `query_aom_metric_data`（查指标值，T07）
- ❌ APM tool（T08+）
- ❌ 跨 projectId / 跨 region 查询
- ❌ 缓存层
- ❌ 改动 CES adapter（即使发现 CES 实现有可改进点，写成 issue 不写进本 PR）

## 前置阅读

**必读**（不读会写错）:

1. **`docs/specs/tools/list_aom_metrics.md` v0.2** — 完整 spec，含 9 条 AI 易错点
2. **`docs/specs/tools/list_ces_metrics.md`** — 参考但**不要直接复用代码**（spec §4 列出了 8 处关键差异）
3. **`CLAUDE.md`** — 编码规范（特别注意：`LOG` 大写、不用 Lombok、JDK 21 records）
4. **`docs/decisions/ADR-002-testing-strategy.md`** — Scheme C 纯 mock 策略

**强烈推荐**:

5. `agentic-common/src/main/java/com/huawei/smartom/agentic/common/resilience/HuaweiCloudInvocation.java` — 看一遍调用包装模式，照着用
6. `agentic-adapter-ces/src/main/java/com/huawei/smartom/agentic/adapter/ces/CesMetricsAdapterImpl.java` — **作为结构参照，不复制代码**

**仅当遇到 SDK 行为不确定时查**:

7. 华为云 AOM SDK v3.1.196 源码（已 clone）：`com/huaweicloud/sdk/aom/v2/` 路径下
8. AOM API 文档 `ListMetricItems`：https://support.huaweicloud.com/intl/en-us/api-aom/ListMetricItems.html

## 产物清单

```
docs/specs/tools/list_aom_metrics.md                  ← 已 v0.2，无需新增

agentic-common/
  src/main/java/.../common/config/
    HuaweiCloudProperties.java                        ← 修改: 加 projectId 字段 + getter/setter
  src/main/java/.../common/health/
    HuaweiCloudCredentialsHealthIndicator.java        ← 修改: 加 projectId 缺失检查
  src/test/java/.../common/health/
    HuaweiCloudCredentialsHealthIndicatorTest.java    ← 修改: 加 projectId-missing 用例

agentic-adapter-aom/
  pom.xml                                             ← 已存在，无需改
  src/main/java/com/huawei/smartom/agentic/adapter/aom/
    config/
      AomClientConfig.java                            ← 新增: AomClient bean
    dto/
      AomListMetricsRequest.java                      ← 新增: 业务输入 DTO (record)
      AomListMetricsResponse.java                     ← 新增: 业务输出 DTO (record)
      AomMetricInfo.java                              ← 新增: 单条 metric (record)
      AomMetricDimension.java                         ← 新增: 维度 (record)
      AomPagination.java                              ← 新增: 分页 (record, 注意 next_token 是 Integer!)
    AomMetricsAdapter.java                            ← 新增: 接口
    AomMetricsAdapterImpl.java                        ← 新增: 实现
  src/test/java/com/huawei/smartom/agentic/adapter/aom/
    AomMetricsAdapterImplTest.java                    ← 新增: 20 个 UT
    contract/
      AomListMetricsContractTest.java                 ← 新增: 5 个 TC
    config/
      AomClientConfigTest.java                        ← 新增: 1 个 Spring context 测试
  src/test/resources/sdk-samples/aom/
    list-metric-items-response.json                   ← 新增: 文档样例 JSON

agentic-monitoring/
  src/main/java/.../monitoring/aom/
    AomMetricsService.java                            ← 新增: 业务校验层 (§3.2 7 条规则)
  src/test/java/.../monitoring/aom/
    AomMetricsServiceTest.java                        ← 新增: 校验用例 UT-04~11
  pom.xml                                             ← 加 aom adapter 依赖

agentic-mcp/
  src/main/java/.../mcp/tool/
    AomMetricsTool.java                               ← 新增: @Tool 注册
  src/main/java/.../mcp/config/
    McpServerConfig.java                              ← 修改: ToolCallbackProvider 加 aomMetricsTool
  src/main/resources/
    application.yml                                   ← 修改: huaweicloud.project-id + resilience4j.ratelimiter.instances.aom-readonly
  src/test/java/.../mcp/tool/
    AomMetricsToolTest.java                           ← 新增: 错误转换 UT

scripts/smoke/
  smoke-list_aom_metrics.sh                           ← 新增: 3 个冒烟用例
```

## 关键技术要求

### 1. DTO 设计（对照 spec §3 & §4）

**`AomMetricsDimension`**:

```
public record AomMetricDimension(
    @JsonProperty("name") String name,
    @JsonProperty("value") String value) {}
```

**`AomMetricInfo`**（注意 `dimension_value_hash` 字段，与 CES 不同）:

```
public record AomMetricInfo(
    @JsonProperty("namespace") String namespace,
    @JsonProperty("metric_name") String metricName,
    @JsonProperty("unit") String unit,
    @JsonProperty("dimensions") List<AomMetricDimension> dimensions,
    @JsonProperty("dimension_value_hash") String dimensionValueHash) {}
```

**`AomPagination`**（与 CES 关键差异：`nextToken` 是 Integer 不是 String）:

```
public record AomPagination(
    @JsonProperty("count") int count,
    @JsonProperty("total") int total,
    @JsonProperty("offset") Integer offset,
    @JsonProperty("next_token") Integer nextToken,
    @JsonProperty("has_more") boolean hasMore) {}
```

**`AomListMetricsRequest`**（业务输入；compact constructor 设默认值）:

```
public record AomListMetricsRequest(
    String namespace,
    String metricName,
    List<AomMetricDimension> dimensions,
    String inventoryId,
    Integer limit,
    Integer start) {

    public AomListMetricsRequest {
        if (limit == null) {
            limit = 100;
        }
        if (start == null) {
            start = 0;
        }
    }
}
```

**`AomListMetricsResponse`**: 与 CES 类似的封装结构 `{ metrics: [...], pagination: {...} }`。

### 2. AomMetricsAdapter 接口

```
public interface AomMetricsAdapter {
    AomListMetricsResponse listMetrics(AomListMetricsRequest request);
}
```

### 3. AomMetricsAdapterImpl

**两条调用路径**（spec §4 调用模式）:

- 路径 A：仅 inventoryId
- 路径 B：namespace + 可选 metricName + 可选 dimensions

**实现要点**:

- 用 `HuaweiCloudInvocation.execute("aom-readonly", "huaweicloud-retryable", "aom.listMetricItems", ...)`
- SDK request 的 limit/start 需要 `String.valueOf(intValue)`
- `namespace` 用 `QueryMetricItemOptionParam.NamespaceEnum.fromValue(namespace)` 转换
- 路径 A：`request.withType("inventory")`，body 只设 inventoryId，**不设 metricItems**
- 路径 B：body 只设 metricItems，**不设 inventoryId、不设 type**
- 决策树：如果 `inventoryId != null` 走路径 A（即使 namespace 也提供了，按 spec §7 决议 inventory_id 优先 + WARN 日志）

**SDK 来源类全限定名（写代码时直接 import）**:

```
com.huaweicloud.sdk.aom.v2.AomClient
com.huaweicloud.sdk.aom.v2.region.AomRegion
com.huaweicloud.sdk.aom.v2.model.ListMetricItemsRequest
com.huaweicloud.sdk.aom.v2.model.ListMetricItemsResponse
com.huaweicloud.sdk.aom.v2.model.MetricAPIQueryItemParam
com.huaweicloud.sdk.aom.v2.model.QueryMetricItemOptionParam
com.huaweicloud.sdk.aom.v2.model.Dimension              // 注意! 不是 CES 的 MetricsDimension
com.huaweicloud.sdk.aom.v2.model.MetricItemResultAPI
com.huaweicloud.sdk.aom.v2.model.MetaDataSeries
```

### 4. AomMetricsService（业务校验层）

实现 spec §3.2 全部 7 条校验规则。规则之间**短路求值**：早返回，避免后面规则的 NPE。

**正则常量**（写成 `private static final Pattern XXX` 大写命名）:

```
NAMESPACE_PATTERN = ^(PAAS\.(CONTAINER|NODE|SLA|AGGR)|CUSTOMMETRICS|[A-Za-z][A-Za-z0-9_.]{2,63})$
INVENTORY_ID_PATTERN = ^(host|application|instance|container|process|network|storage|volume)_[A-Za-z0-9-]+$
```

校验失败抛 `InvalidParamException(message)`，message 要明确指明哪一项不合规。

### 5. AomClientConfig

参考 `CesClientConfig` 结构，但要点不同：

- 需要 `projectId`：`new BasicCredentials().withProjectId(p).withAk(ak).withSk(sk)`
- region：`AomRegion.valueOf(properties.getRegion())`
- HTTP timeout：10 秒（与 CES 一致）
- 启动日志：`LOG.info("AomClient initialized, region={}, projectId={}", region, projectId)`
  - **注意不要把 ak/sk 打日志**

### 6. MCP Tool 注册

`AomMetricsTool.listAomMetrics(...)` 方法签名按 spec §3.2 输入参数表逐一映射。

**特别注意**:

- `@ToolParam(description=...)` 描述必须与 spec §3.1 description 内容**完全一致**（这是 agent 决策依据）
- `dimensions` 参数类型在 MCP 层用 `List<AomMetricDimension>`（Spring AI 会自动用 Jackson 把传入 JSON 反序列化）
- catch `SmartomException` 转 `ErrorResponse`（沿用 `CesMetricsTool` 模式）

### 7. 测试（与 spec §6 严格 1:1 对应）

**`AomMetricsAdapterImplTest`** 20 个 UT，每个方法名直接对应 spec UT-ID：

```
@DisplayName("UT-01: namespace-only ...")
void ut01NamespaceOnlyMapped() { ... }
```

**`AomMetricsServiceTest`** 校验用例 UT-04~11，验证 service 在校验阶段就拦截，**adapter mock 用 `verify(adapter, never()).listMetrics(any())`** 确认未调用下游。

**`AomListMetricsContractTest`** 5 个 TC：反射检查 SDK 类字段 + 样例 JSON 反序列化。**TC-04 是 AOM 独有**（验证 NamespaceEnum 嵌套类及静态常量），CES 没这条。

**`AomClientConfigTest`** 用 `@SpringBootTest(classes={AomClientConfig.class, HuaweiCloudProperties.class})` + `@TestPropertySource` 注入测试 ak/sk/region/project-id，验证 AomClient bean 能创建。

### 8. 冒烟脚本

`scripts/smoke/smoke-list_aom_metrics.sh <host:port>`，3 个用例对应 spec §6 末尾。

**注意冒烟脚本可能依赖测试环境**——如果测试集群里没有应用接入 AOM，第一个用例会返回空 metrics（不算失败，只是断言要写成 `count >= 0` 而不是 `count > 0`）。这点要在 README 里说明。

## 验收标准（Definition of Done）

合并 PR 前，下列每一项都必须为绿：

- [ ] `mvn clean install` 全部模块通过（含 Checkstyle、单元测试、SpringBootTest）
- [ ] spec §6 的 20 个 UT 全部存在且通过（grep 验证 `ut01` ~ `ut20` 方法名都在）
- [ ] spec §6 的 5 个 TC 全部存在且通过
- [ ] `AomClientConfigTest` 通过
- [ ] `mvn spring-boot:run -pl agentic-mcp` 能起来（不需要真 AK/SK，readiness 报 DOWN 是预期）
- [ ] 改动后 `huaweicloudCredentials` 健康指标在缺 projectId 时报 DOWN（手工 curl 验证）
- [ ] 冒烟脚本可执行（`chmod +x` + bash 语法检查）
- [ ] 没有引入 Lombok（grep "@Slf4j\|@Data\|@Builder" 应为空）
- [ ] 所有 logger 都是 `private static final Logger LOG`（grep "Logger log " 应为空）
- [ ] spec v0.2 的"配套基础设施变更"全部落地（projectId 配置 + aom-readonly 限流 + health indicator 升级）
- [ ] PR 描述里包含 spec 链接（`docs/specs/tools/list_aom_metrics.md`）和本任务卡链接

## AI 易错点提醒（写代码前再读一遍）

> 这一节直接来自 spec §4 末尾 9 条，**Claude Code 写代码时如果看着抄 CES 实现，几乎一定会踩中其中几条**。请在写每个文件之前默念一遍。

1. **不要复用 CES adapter 代码**。AOM 和 CES 是两套独立 SDK 包，类名相同含义不同。
2. **`limit` / `start` 是 String 类型**（SDK），在 adapter 层用 `String.valueOf(intValue)` 转换。
3. **POST 请求 + body 中带过滤条件**。CES 是 GET + query string。SDK 内部处理，但写 mock 测试时要明白。
4. **`namespace` 是 `NamespaceEnum`**，用 `QueryMetricItemOptionParam.NamespaceEnum.fromValue(name)` 转换。**不是 String。**
5. **`inventoryId` 模式下，body 的 `metricItems` 必须为 null**（不传），不能传空 list。AOM API 会拒绝空 list。
6. **`projectId` 是必需的**：BasicCredentials 三件套 ak/sk/projectId。这是 CES 没有的。
7. **AOM 错误码格式不同**（`AOM.xxx` / `SVCSTG_AMS_xxx`），但 `SdkExceptionMapper` 按 HTTP status 分类，**不需要改它**。
8. **响应体可能含 `errorCode: SVCSTG_AMS_2000000`（成功码）**——这是 AOM 历史遗留，HTTP 200 时 body 里也有 errorCode 字段。**不要把这当业务错误。**
9. **`Dimension` 类是 `com.huaweicloud.sdk.aom.v2.model.Dimension`**，不是 CES 的 `MetricsDimension`。包名相似，含义相同，但**不是同一个类**，不能混用。

**额外 2 条（v0.2 spec 决议补充）**:

10. **`HuaweiCloudCredentialsHealthIndicator` 改动要兼容 CES 场景**——CES 不需要 projectId，所以 projectId 检查不能放在所有场景里。**当前实现策略**：projectId 缺失只报 DOWN+detail，不阻断；CES 工作流不查 projectId（reading），所以仍可工作。这件事要在 health indicator 的 javadoc 里说明。
11. **`application.yml` 的 `huaweicloud.project-id: ${HUAWEICLOUD_PROJECT_ID:}`**——保留默认空字符串，避免 Spring Boot 启动期解析失败。

## 完成后

- 把本任务卡状态从 Draft 改为 Done（顶部）
- 在 PR description 里贴 spec + 任务卡链接
- 在团队群里 @ PL 发 review 请求，附 spec §7 决议链接（防止 reviewer 重新讨论已锁定问题）
- 部署到测试环境后跑一次冒烟脚本，结果贴在 PR 评论里
- 把 SDK 速查表（`docs/sdk-cheatsheet.md`，如不存在新建）追加 AOM 部分（参照本 spec §4 字段映射表）
