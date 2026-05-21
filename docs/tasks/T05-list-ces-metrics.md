# T05 — 实现 list_ces_metrics

> 状态: Ready · 估时: 1d · 依赖: T01-T04 · 后置: 后续 tool 复用本任务沉淀的模式

## 目标

按 `docs/specs/tools/list_ces_metrics.md` 实现完整的 `list_ces_metrics` MCP tool，含 adapter 实现、业务编排、MCP tool 注册、单元测试、类型契约测试、冒烟脚本。

## 范围

**做**: spec 中 §7 验收标准列出的所有项目。

**不做**:
- AOM / APM 对应 tool（后续任务）
- 查询指标数据值 (query_metric_data)（后续任务）

## 前置阅读

**必读**:
1. `docs/specs/tools/list_ces_metrics.md` — 完整 spec
2. `CLAUDE.md` — 项目约束
3. `docs/architecture.md` §5 — list_ces_metrics 的数据流图

## 产物清单

```
agentic-adapter-ces/
  src/main/java/com/huawei/smartom/agentic/adapter/ces/
    CesMetricsAdapter.java                          ← 加 listMetrics 方法
    CesMetricsAdapterImpl.java                      ← 实现
    dto/
      CesListMetricsRequest.java                    ← record
      CesListMetricsResponse.java                   ← record
      CesMetricInfo.java                            ← record
      CesMetricDimension.java                       ← record
      CesPagination.java                            ← record
  src/test/java/com/huawei/smartom/agentic/adapter/ces/
    CesMetricsAdapterImplTest.java                  ← UT-01 ~ UT-15
    contract/CesListMetricsContractTest.java        ← TC-01 ~ TC-04
  src/test/resources/sdk-samples/ces/
    list-metrics-response.json                      ← 文档样例 JSON

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/
    ces/CesMetricsService.java                      ← 业务编排 (参数校验 + 调 adapter)
  src/test/java/com/huawei/smartom/agentic/monitoring/
    ces/CesMetricsServiceTest.java

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/CesMetricsTool.java                        ← @Tool 注册
    config/McpServerConfig.java                     ← 注册 CesMetricsTool 到 ToolCallbackProvider
  src/test/java/com/huawei/smartom/agentic/mcp/
    tool/CesMetricsToolTest.java                    ← MCP 层 tool 调用测试

scripts/smoke/
  smoke-list_ces_metrics.sh                         ← 冒烟脚本
  README.md                                         ← 冒烟脚本使用说明

docs/specs/tools/
  list_ces_metrics.md                               ← 标记为 implemented

README.md                                           ← 更新 tool 清单
```

## 关键技术要求

### 1. DTO 设计

**全部用 record**：

```java
public record CesListMetricsRequest(
        String namespace,        // nullable
        String metricName,       // nullable
        String dimName,          // nullable
        String dimValue,         // nullable
        Integer limit,           // 默认 100
        String start,            // nullable, marker
        String order             // 默认 "desc"
) {
    public CesListMetricsRequest {
        // 紧凑构造做规范化，不做业务校验
        if (limit == null) {
            limit = 100;
        }
        if (order == null) {
            order = "desc";
        }
    }
}
```

**校验在业务层做**（不在 DTO 构造时），因为校验失败要返回 `ErrorCode.INVALID_PARAM`，DTO 构造抛 IllegalArgumentException 会泄漏到错误码外。

输出 DTO：

```java
public record CesListMetricsResponse(
        List<CesMetricInfo> metrics,
        CesPagination pagination
) {}

public record CesMetricInfo(
        String namespace,
        String metricName,
        String unit,
        List<CesMetricDimension> dimensions
) {}

public record CesMetricDimension(String name, String value) {}

public record CesPagination(
        int count,
        int total,
        String nextMarker,    // nullable
        boolean hasMore
) {}
```

### 2. CesMetricsAdapter 接口

```java
public interface CesMetricsAdapter {

    /**
     * 查询 CES 已注册的指标定义列表。
     *
     * @param request 查询请求，所有字段可为 null（除 limit、order 有默认）
     * @return 指标列表 + 分页信息
     * @throws SmartomException 上游错误已映射；不抛 SDK 异常
     */
    CesListMetricsResponse listMetrics(CesListMetricsRequest request);
}
```

### 3. CesMetricsAdapterImpl

实现核心步骤（参考架构文档 §5）：

```java
@Override
public CesListMetricsResponse listMetrics(CesListMetricsRequest request) {
    long start = System.currentTimeMillis();
    try {
        ListMetricsRequest sdkRequest = toSdkRequest(request);
        ListMetricsResponse sdkResponse = invocation.execute(
                "ces-readonly",
                "huaweicloud-retryable",
                "ces.listMetrics",
                () -> cesClient.listMetrics(sdkRequest));
        return toResponseDto(sdkResponse);
    } finally {
        LOG.info("ces.listMetrics done, durationMs={}, namespace={}, metricName={}",
                System.currentTimeMillis() - start,
                request.namespace(),
                request.metricName());
    }
}

private ListMetricsRequest toSdkRequest(CesListMetricsRequest r) {
    ListMetricsRequest sdk = new ListMetricsRequest();
    if (r.namespace() != null) {
        sdk.setNamespace(r.namespace());
    }
    if (r.metricName() != null) {
        sdk.setMetricName(r.metricName());
    }
    if (r.dimName() != null && r.dimValue() != null) {
        sdk.setDim0(r.dimName() + "," + r.dimValue());
    }
    sdk.setLimit(r.limit());
    if (r.start() != null) {
        sdk.setStart(r.start());
    }
    sdk.setOrder(r.order());
    return sdk;
}

private CesListMetricsResponse toResponseDto(ListMetricsResponse sdkResp) {
    List<CesMetricInfo> metrics = sdkResp.getMetrics() == null ? List.of() :
            sdkResp.getMetrics().stream()
                    .map(this::toMetricInfo)
                    .toList();
    MetaData meta = sdkResp.getMetaData();
    String nextMarker = meta == null ? null : meta.getMarker();
    int count = meta == null ? metrics.size() : meta.getCount();
    int total = meta == null ? metrics.size() : meta.getTotal();
    boolean hasMore = nextMarker != null && count > 0;
    return new CesListMetricsResponse(
            metrics,
            new CesPagination(count, total, nextMarker, hasMore));
}

// toMetricInfo, toDimension 类推
```

**重要**：以上 SDK 类名和方法名以华为云 SDK v3.1.196 源码为准。常见类：
- `com.huaweicloud.sdk.ces.v1.model.ListMetricsRequest`
- `com.huaweicloud.sdk.ces.v1.model.ListMetricsResponse`
- `com.huaweicloud.sdk.ces.v1.model.MetricInfoList`
- `com.huaweicloud.sdk.ces.v1.model.MetricsDimension`
- `com.huaweicloud.sdk.ces.v1.model.MetaData`

setter 名以 SDK 实际为准（可能是 `setMetricName` 也可能是 `withMetricName` 链式）。

### 4. CesMetricsService (业务层校验)

```java
@Service
public class CesMetricsService {

    private static final Pattern NAMESPACE_PATTERN =
            Pattern.compile("^[A-Z][A-Za-z0-9]{2,31}\\.[A-Za-z0-9_]+$");

    private final CesMetricsAdapter adapter;

    public CesMetricsService(CesMetricsAdapter adapter) {
        this.adapter = adapter;
    }

    public CesListMetricsResponse listMetrics(CesListMetricsRequest request) {
        validate(request);
        return adapter.listMetrics(request);
    }

    private void validate(CesListMetricsRequest r) {
        // dim_name / dim_value 成对校验
        if ((r.dimName() == null) != (r.dimValue() == null)) {
            throw new InvalidParamException(
                    "dim_name and dim_value must be provided together");
        }
        // limit 范围
        if (r.limit() < 1 || r.limit() > 1000) {
            throw new InvalidParamException("limit must be in [1, 1000]");
        }
        // namespace 格式
        if (r.namespace() != null && !NAMESPACE_PATTERN.matcher(r.namespace()).matches()) {
            throw new InvalidParamException(
                    "namespace format invalid, expected like 'SYS.ECS'");
        }
        // order 枚举
        if (!"asc".equals(r.order()) && !"desc".equals(r.order())) {
            throw new InvalidParamException("order must be 'asc' or 'desc'");
        }
    }
}
```

### 5. MCP Tool 注册

```java
@Component
public class CesMetricsTool {

    private final CesMetricsService service;

    public CesMetricsTool(CesMetricsService service) {
        this.service = service;
    }

    @Tool(
            name = "list_ces_metrics",
            description = """
                    List available CES (Cloud Eye Service) metric definitions for
                    Huawei Cloud resources. Use this to discover which metrics can
                    be queried for a given namespace (e.g., SYS.ECS, SYS.RDS) or a
                    specific resource. Returns metric metadata (name, namespace,
                    dimensions, unit), not actual data points. Call query_metric_data
                    afterwards to get values."""
    )
    public Object listCesMetrics(
            @ToolParam(description = "CES namespace like SYS.ECS, optional", required = false) String namespace,
            @ToolParam(description = "Exact metric name, optional", required = false) String metricName,
            @ToolParam(description = "Dimension name, e.g., instance_id. Must accompany dim_value.", required = false) String dimName,
            @ToolParam(description = "Dimension value. Must accompany dim_name.", required = false) String dimValue,
            @ToolParam(description = "Page size [1, 1000], default 100", required = false) Integer limit,
            @ToolParam(description = "Pagination marker from previous response", required = false) String start,
            @ToolParam(description = "Sort order: asc or desc, default desc", required = false) String order
    ) {
        try {
            CesListMetricsRequest req = new CesListMetricsRequest(
                    namespace, metricName, dimName, dimValue, limit, start, order);
            return service.listMetrics(req);
        } catch (SmartomException e) {
            // 转成 ErrorResponse, 让 Agent 拿到结构化错误
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
    }
}
```

注册到 ToolCallbackProvider：

```java
@Bean
public ToolCallbackProvider toolCallbackProvider(
        HelloWorldTool helloWorldTool,
        CesMetricsTool cesMetricsTool) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(helloWorldTool, cesMetricsTool)
            .build();
}
```

### 6. 单元测试

按 spec §6.1 列的 UT-01 到 UT-15，**每个用例对应一个 `@Test` 方法**，方法名格式：

```java
@Test
@DisplayName("UT-05: limit = 1001 → INVALID_PARAM, SDK not called")
void listMetrics_whenLimitExceeds1000_returnsInvalidParamWithoutCallingSdk() { ... }
```

- 测试类分两个：
  - `CesMetricsAdapterImplTest`：测 UT-01/02/03/09/10/11/12/13/14（SDK 交互层）
  - `CesMetricsServiceTest`：测 UT-04/05/06/07/08（参数校验）
  - UT-15（unit 字段始终存在）在 adapter test 里加 assertion

### 7. 类型契约测试

`CesListMetricsContractTest`：

```java
class CesListMetricsContractTest {

    @Test
    @DisplayName("TC-01: SDK MetricInfoList class has required fields")
    void metricInfoListHasRequiredFields() throws Exception {
        Class<?> clazz = MetricInfoList.class;
        assertHasField(clazz, "namespace");
        assertHasField(clazz, "metricName");
        assertHasField(clazz, "unit");
        assertHasField(clazz, "dimensions");
    }

    @Test
    @DisplayName("TC-04: sample JSON deserializes without nulls")
    void sampleJsonDeserializesCorrectly() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        String json = readResource("/sdk-samples/ces/list-metrics-response.json");
        ListMetricsResponse resp = mapper.readValue(json, ListMetricsResponse.class);
        assertThat(resp.getMetrics()).isNotEmpty();
        assertThat(resp.getMetaData()).isNotNull();
        assertThat(resp.getMetaData().getCount()).isNotNull();
    }

    private void assertHasField(Class<?> clazz, String fieldName) {
        boolean found = Arrays.stream(clazz.getDeclaredFields())
                .anyMatch(f -> f.getName().equals(fieldName));
        assertThat(found)
                .as("Field '" + fieldName + "' should exist in " + clazz.getSimpleName())
                .isTrue();
    }
}
```

样例 JSON（来自华为云 CES 文档样例，存到 `sdk-samples/ces/list-metrics-response.json`）：

```json
{
  "metrics": [
    {
      "namespace": "SYS.ECS",
      "dimensions": [
        {"name": "instance_id", "value": "d9112af5-6913-4f3b-bd0a-3f96711e004d"}
      ],
      "metric_name": "cpu_util",
      "unit": "%"
    }
  ],
  "meta_data": {
    "count": 1,
    "marker": "SYS.ECS.cpu_util.instance_id:d9112af5-6913-4f3b-bd0a-3f96711e004d",
    "total": 7
  }
}
```

### 8. 冒烟脚本

`scripts/smoke/smoke-list_ces_metrics.sh`：

```bash
#!/usr/bin/env bash
# 部署到贵阳后跑此脚本，验证 list_ces_metrics tool 实际工作。
# 用法: ./smoke-list_ces_metrics.sh <mcp-server-host:port>

set -euo pipefail

HOST="${1:-localhost:8080}"

# 用 npx @modelcontextprotocol/inspector --cli 或直接 curl SSE，按团队工具选型
# 此处给出 curl 思路，实际可能需要 MCP client wrapper

echo "Test 1: namespace=SYS.ECS, limit=5 → expect non-empty metrics"
# ... 调用 + 断言

echo "Test 2: namespace=SYS.NONEXISTENT → expect empty metrics, no error"
# ... 调用 + 断言

echo "Test 3: limit=1001 → expect INVALID_PARAM"
# ... 调用 + 断言

echo "All smoke tests passed."
```

实际脚本以团队 MCP client 工具为准。可考虑用一个简单 Java main 或 Python 脚本调 SSE。

## 验收标准

按 spec §7 完整执行：

- [ ] 所有 UT 用例通过 (15 条)
- [ ] 所有 TC 用例通过 (4 条)
- [ ] MCP Inspector 看到 `list_ces_metrics`，description 正确
- [ ] 配置文件 `ces-readonly` RateLimiter QPS 可调
- [ ] 日志含入参摘要 / 耗时 / upstream trace id
- [ ] Micrometer 指标 `mcp_tool_invocation` 可见
- [ ] 贵阳环境冒烟脚本通过（3 条用例）
- [ ] README 含 tool 使用示例
- [ ] Checkstyle 0 violations

## AI 易错点提醒

**spec §4 已列出**：
1. dim.0 字段格式 `"key,value"` 拼接，不是对象
2. ListMetrics 只支持 dim.0 一个维度过滤
3. start 是 marker 字符串
4. namespace 校验自己做

**额外**：
5. **SDK 类的 getter/setter 命名**：v3 SDK 的 setter 一般有两种风格——`setXxx(...)` 和链式 `withXxx(...)`。**两种都有**，链式 `withXxx` 返回 this 适合 builder 风格，`setXxx` 返回 void。**测试和实现里看具体类的方法签名**，AI 不要凭印象。
6. **`@Tool` 注解的位置**：Spring AI 1.0.x 的 `@Tool` 注解可能在 `org.springframework.ai.tool.annotation` 包，**以引入的 starter 实际包路径为准**。
7. **`@ToolParam(required = false)`**：可选参数必须显式 `required = false`，否则 MCP schema 会把它标记为 required。
8. **MCP tool 返回值的序列化**：Spring AI 会用 Jackson 自动序列化返回值。**record 默认能序列化**，但要确认 Jackson 模块设置（`spring.jackson.property-naming-strategy=SNAKE_CASE` 会影响输出 JSON 字段名是 camel 还是 snake）。**建议输出 JSON 用 snake_case** 与华为云风格一致，spec §3.3 写的就是 snake_case。
9. **`SmartomException` 在 MCP tool 层要 catch 转 ErrorResponse**——否则 Spring AI 默认会以异常形式返回，错误结构不可控。
10. **Resilience4j RateLimiter 的限流抛 `RequestNotPermitted`**：HuaweiCloudInvocation 里要 catch 这个异常映射到 `UPSTREAM_THROTTLED`（T03 已实现，T05 验证一下）。

## 完成后

PR：`feat(T05): implement list_ces_metrics tool with full UT + TC coverage`。

PR 描述中附上：
- spec 链接
- 测试用例对应表（UT-01 → 测试方法名）
- 冒烟脚本说明
