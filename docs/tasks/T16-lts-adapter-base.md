# T16 — LTS Adapter 基座 + 2 个只读日志查询方法

> 状态: **In Progress**（用户已确认开放项，开始编码） · 估时: 1d · 依赖: T03（common：错误码 / 异常 / 限流重试 / 健康检查 / `HuaweiCloudClientFactory`）· 后置: 后续 T17–T18 在此基座上各自落地 MCP tool

## 已确认决策

- **`huaweicloud.lts-region` 默认值**：`cn-north-9`
- **LTS SDK 版本**：与其他华为云 SDK 一致使用 `${huaweicloud-sdk.version}` = **3.1.177**（Maven Central 已发布，编译期验证通过）。早先因本地缓存缺失而怀疑 3.1.177 不可用是误判

## 目标

为 LTS（Log Tank Service）新建独立 Maven 子模块 `agentic-adapter-lts`，封装华为云 LTS SDK 的 2 个只读日志查询能力，**只完成 adapter 层 + 单元测试**：interface、impl、DTO、Client config、限流配置。不写 monitoring service，不写 MCP tool。

## 上游 API 复核结果

用户原始 3 个链接经 SDK 仓库（`huaweicloud-sdk-java-v3/services/lts`）`LtsMeta` 复核：

| 用户给的入口 | 实际 SDK 方法 | URI（SDK 注册） |
|---|---|---|
| #1 `ListLogContext` 文档页 | `LtsClient.listLogContext(ListLogContextRequest) → ListLogContextResponse` | `POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/context` |
| #2 `ListLogs` 文档页 | `LtsClient.listLogs(ListLogsRequest) → ListLogsResponse` | `POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/content/query` |
| #3 `/content/query` URL | **同 #2** —— 这就是 `ListLogs` 的实际请求路径 | （同上） |

结论：**只需要封装 2 个 SDK 方法**，不是 3 个。`ListLogs` 是用户列表中 #2 与 #3 共指的同一 API。

## 范围

**做**:

- 新 Maven 子模块 `agentic-adapter-lts`（与 `agentic-adapter-ces` / `-aom` / `-apm` 同级），含 `pom.xml`、`src/main`、`src/test`
- `LtsClientConfig`：`@Configuration`，注入单例 `LtsClient` Bean（复用 `HuaweiCloudClientFactory.credentialsWithProjectId(...)` + `defaultHttpConfig()`）
- `HuaweiCloudProperties` 新增字段 `ltsRegion`（LTS 在部分 region 不开服，与主 region 解耦，参考 APM 已有的 `apmRegion`），默认值待用户确认
- `LtsLogAdapter` interface + `LtsLogAdapterImpl` 实现 2 个方法
- 自定义 DTO（record）：2 个 Request / 2 个 Response / 1 个共用日志条目 `LtsLogEntry`（SDK 用 `LogContents`，命名换成 `LtsLogEntry`）
- `application.yml` 新增 `lts-readonly` RateLimiter（默认 10 QPS，与 ces-readonly / aom-readonly 一致）
- `pom.xml`（root）`dependencyManagement` 新增 `huaweicloud-sdk-lts`
- `agentic-adapter/pom.xml` 注册新子模块
- 单元测试覆盖：Client bean 装配、2 个方法的成功路径、SDK 异常映射（429 / 401 / 5xx / Timeout 各至少 1 条）

**不做**（防止任务蔓延，进遗留项）:

- ❌ `agentic-monitoring/.../lts/` 业务服务（后续任务）
- ❌ MCP Tool 注册（后续 T17–T18 各自做）
- ❌ Contract Test（spec 风格的 SDK 反射 / JSON 反序列化测试，本期未交付）
- ❌ 部署后冒烟脚本
- ❌ 健康检查里加 LTS connectivity probe
- ❌ 写操作（创建 / 删除日志组）
- ❌ 跨 region / 跨账号
- ❌ 结构化日志查询（`listStructuredLogsWithTimeRange` / `listQueryStructuredLogs`，URI 为 `/struct-content/query`，与本期 `/content/query` 不同；按需在后续任务追加）
- ❌ SQL/分析模式查询的客户端语法校验（`is_analysis_query=true` 时上游会再做 SQL 解析，adapter 不预检）

## 前置阅读

**必读**:

1. `CLAUDE.md` — 项目编码规范、SDK 不泄漏、错误码统一
2. `docs/architecture.md` — 模块依赖方向（mcp → monitoring → adapter → common）
3. `docs/tasks/T04-ces-adapter-base.md` — adapter 基座的最早模板
4. `docs/decisions/ADR-004-ces-enum-catalog.md` — 枚举分档原则（决定本期 LTS 不做严格枚举，包括 `search_type`）
5. 用户给出的 2 个上游 API 文档链接

**强烈推荐**:

6. `agentic-adapter/agentic-adapter-aom/src/main/java/.../config/AomClientConfig.java` — 用 `HuaweiCloudClientFactory.credentialsWithProjectId(...)` 的最新风格，**LTS 与 AOM 都要带 projectId**
7. `agentic-common/src/main/java/.../sdk/HuaweiCloudClientFactory.java` — 必须复用，不要复制粘贴 SDK builder 模板
8. `agentic-common/src/main/java/.../resilience/HuaweiCloudInvocation.java` — 限流 / 重试 / 异常映射的统一入口
9. `agentic-adapter/agentic-adapter-ces/src/main/java/.../CesMetricsAdapterImpl.java` — adapter 实现的典型骨架
10. SDK 源码（已复核）：`huaweicloud-sdk-java-v3/services/lts/src/main/java/com/huaweicloud/sdk/lts/v2/`

## 产物清单

```
docs/tasks/
  T16-lts-adapter-base.md                                ← 本任务卡

pom.xml (root)                                           ← 修改：dependencyManagement 加 huaweicloud-sdk-lts
agentic-adapter/pom.xml                                  ← 修改：注册 <module>agentic-adapter-lts</module>

agentic-common/
  src/main/java/com/huawei/smartom/agentic/common/config/
    HuaweiCloudProperties.java                           ← 修改：加 ltsRegion 字段 + getter/setter + Javadoc

agentic-adapter/agentic-adapter-lts/                     ← 新增子模块
  pom.xml
  src/main/java/com/huawei/smartom/agentic/adapter/lts/
    config/
      LtsClientConfig.java                               ← @Configuration，LtsClient bean
    LtsLogAdapter.java                                   ← interface（2 个方法）
    LtsLogAdapterImpl.java                               ← @Component impl
    dto/
      LtsListLogsRequest.java                            ← record
      LtsListLogsResponse.java                           ← record
      LtsListLogContextRequest.java                      ← record
      LtsListLogContextResponse.java                     ← record
      LtsLogEntry.java                                   ← record（SDK 的 LogContents 重命名）
  src/test/java/com/huawei/smartom/agentic/adapter/lts/
    config/LtsClientConfigTest.java                      ← Spring context 启动测试
    LtsLogAdapterImplTest.java                           ← UT 矩阵（见下）

agentic-mcp/
  src/main/resources/application.yml                     ← 修改：resilience4j.ratelimiter.instances 加 lts-readonly + huaweicloud.lts-region
```

## 关键技术要求

### 1. SDK 版本

与 CES / AOM / APM 共用项目根 pom 的 `huaweicloud-sdk.version=3.1.177`。`huaweicloud-sdk-lts:3.1.177` 已在 Maven Central 发布，与本任务卡复核 SDK 仓库（用户指定）时的 3.1.194 字段一致——`QueryLtsLogParams` / `ListLogContextRequestBody` / `LogContents` 等核心类的字段名与类型在 3.1.177 与 3.1.194 之间稳定，无需 override。

dependencyManagement 直接：

```xml
<dependency>
    <groupId>com.huaweicloud.sdk</groupId>
    <artifactId>huaweicloud-sdk-lts</artifactId>
    <version>${huaweicloud-sdk.version}</version>
</dependency>
```

### 2. Maven 子模块结构

`agentic-adapter-lts/pom.xml` 参考 `agentic-adapter-aom/pom.xml`：

```xml
<parent>
    <groupId>com.huawei.smartom.agentic</groupId>
    <artifactId>agentic-adapter</artifactId>
    <version>${project.version}</version>
</parent>
<artifactId>agentic-adapter-lts</artifactId>

<dependencies>
    <dependency>
        <groupId>com.huaweicloud.sdk</groupId>
        <artifactId>huaweicloud-sdk-lts</artifactId>
    </dependency>
    <dependency>
        <groupId>com.huawei.smartom.agentic</groupId>
        <artifactId>agentic-common</artifactId>
    </dependency>
    <!-- spring + resilience4j + junit + mockito 同 ces / aom 模块 -->
</dependencies>
```

聚合父 `agentic-adapter/pom.xml` 加入 `<module>agentic-adapter-lts</module>`。

### 3. LtsClientConfig

复用 `HuaweiCloudClientFactory`：

```java
@Configuration
public class LtsClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(LtsClientConfig.class);

    @Bean
    public LtsClient ltsClient(HuaweiCloudProperties properties) {
        LtsClient client = LtsClient.newBuilder()
                .withCredential(HuaweiCloudClientFactory.credentialsWithProjectId(properties))
                .withHttpConfig(HuaweiCloudClientFactory.defaultHttpConfig())
                .withRegion(LtsRegion.valueOf(properties.getLtsRegion()))
                .build();
        LOG.info("LtsClient initialized, ltsRegion={}, projectId={}",
                properties.getLtsRegion(), properties.getProjectId());
        return client;
    }
}
```

`LtsRegion` 路径：`com.huaweicloud.sdk.lts.v2.region.LtsRegion`。**LTS 是项目级服务**（URI 含 `{project_id}`），SDK 从 `BasicCredentials.withProjectId(...)` 自动注入 path 变量，所以**必须**用 `credentialsWithProjectId`。

### 4. HuaweiCloudProperties 增量

```java
/**
 * LTS region id（例如 {@code af-north-1}、{@code cn-north-4}、{@code cn-southwest-2}）。
 * LTS 在部分 region 提供，与主 region 解耦。默认值由 application.yml 给出。
 */
private String ltsRegion;
```

`application.yml` 同步加 `huaweicloud.lts-region: ${HUAWEICLOUD_LTS_REGION:cn-north-9}`。

### 5. LtsLogAdapter 接口

```java
public interface LtsLogAdapter {

    /**
     * 按时间区间 / 关键字 / 标签 / SQL 查询日志内容。
     * 对应 LTS {@code ListLogs} ——
     * {@code POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/content/query}。
     */
    LtsListLogsResponse listLogs(LtsListLogsRequest request);

    /**
     * 给定一条目标日志的 line_num + __time__ 游标，拉取其前后 N 条上下文。
     * 对应 LTS {@code ListLogContext} ——
     * {@code POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/context}。
     */
    LtsListLogContextResponse listLogContext(LtsListLogContextRequest request);
}
```

### 6. DTO 字段（与 SDK 1:1 对齐，命名遵循 record + camelCase 风格）

**`LtsListLogsRequest`**（对应 `QueryLtsLogParams` 体 + 2 个 path 参数）：

| DTO 字段 | SDK 对应 | 类型 | 说明 |
|---|---|---|---|
| `logGroupId` | path `log_group_id` | `String` | 必填 |
| `logStreamId` | path `log_stream_id` | `String` | 必填 |
| `startTimeMillis` | body `start_time` | `Long` | **SDK 字段是 String，adapter 在 `toSdk*` 里用 `String.valueOf(long)` 转换**；对上层暴露 Long 与 CES/AOM 对齐 |
| `endTimeMillis` | body `end_time` | `Long` | 同上 |
| `labels` | body `labels` | `Map<String, String>` | 日志标签过滤；可为 null |
| `keywords` | body `keywords` | `String` | 关键字过滤；可为 null |
| `query` | body `query` | `String` | SQL 查询；可为 null |
| `isAnalysisQuery` | body `is_analysis_query` | `Boolean` | `true` 走 SQL 分析模式（响应走 `analysisLogs`）；可为 null |
| `isCount` | body `is_count` | `Boolean` | `true` 时只返回 count；可为 null |
| `limit` | body `limit` | `Integer` | 单页大小；可为 null |
| `isDesc` | body `is_desc` | `Boolean` | 排序方向；可为 null |
| `highlight` | body `highlight` | `Boolean` | 关键字高亮；可为 null |
| `isIterative` | body `is_iterative` | `Boolean` | 迭代查询；可为 null |
| `searchType` | body `search_type` | `String` | 仅取 `forwards` / `backwards`；首次查询传 null（上游默认 `init`）。**不做严格枚举**，按 ADR-004 容忍未来扩展 |
| `lineNum` | body `line_num` | `String` | 游标分页：上次结果尾行的行号 |
| `cursorTime` | body `__time__` | `String` | 游标分页：与 `lineNum` 配对的时间戳字符串。**DTO 字段名换成可读的 `cursorTime`**，转 SDK 时映射到 `__time__` |
| `scrollId` | body `scroll_id` | `String` | scroll API 分页 |

**`LtsListLogsResponse`**：

| DTO 字段 | SDK 对应 | 类型 |
|---|---|---|
| `count` | `count` | `Integer` |
| `logs` | `logs` | `List<LtsLogEntry>` |
| `isQueryComplete` | `isQueryComplete` | `Boolean` |
| `analysisLogs` | `analysisLogs` | `List<Object>` |

**`LtsLogEntry`**（SDK 的 `LogContents`）：

| DTO 字段 | SDK 对应 | 类型 |
|---|---|---|
| `content` | `content` | `String` |
| `lineNum` | `line_num` | `String` |
| `labels` | `labels` | `Map<String, String>` |

**`LtsListLogContextRequest`**（对应 `ListLogContextRequestBody` + 2 个 path 参数）：

| DTO 字段 | SDK 对应 | 类型 | 说明 |
|---|---|---|---|
| `logGroupId` | path `log_group_id` | `String` | 必填 |
| `logStreamId` | path `log_stream_id` | `String` | 必填 |
| `lineNum` | body `line_num` | `String` | 目标日志行号 |
| `cursorTime` | body `__time__` | `String` | 目标日志的 `__time__` |
| `backwardsSize` | body `backwards_size` | `Integer` | 向前取多少条 |
| `forwardsSize` | body `forwards_size` | `Integer` | 向后取多少条 |
| `scrollId` | body `scroll_id` | `String` | scroll 模式分页 |

**`LtsListLogContextResponse`**：

| DTO 字段 | SDK 对应 | 类型 |
|---|---|---|
| `logs` | `logs` | `List<LtsLogEntry>` |
| `totalCount` | `total_count` | `Integer` |
| `backwardsCount` | `backwards_count` | `Integer` |
| `forwardsCount` | `forwards_count` | `Integer` |
| `isQueryComplete` | `isQueryComplete` | `Boolean` |

### 7. LtsLogAdapterImpl 调用骨架

每个方法走同一个限流 / 重试 / 异常映射通道：

```java
private static final String RATE_LIMITER_NAME = "lts-readonly";
private static final String RETRY_NAME = "huaweicloud-retryable";
private static final String API_LIST_LOGS = "lts.listLogs";
private static final String API_LIST_LOG_CONTEXT = "lts.listLogContext";

@Override
public LtsListLogsResponse listLogs(LtsListLogsRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    LOG.info("lts.listLogs start, logGroupId={}, logStreamId={}, "
            + "startTimeMillis={}, endTimeMillis={}, keywords={}, query={}",
            request.logGroupId(), request.logStreamId(),
            request.startTimeMillis(), request.endTimeMillis(),
            request.keywords(), request.query());

    ListLogsRequest sdkReq = toSdkListLogsRequest(request);
    ListLogsResponse sdkResp = invocation.execute(
            RATE_LIMITER_NAME, RETRY_NAME, API_LIST_LOGS,
            () -> ltsClient.listLogs(sdkReq));

    return toListLogsResponseDto(sdkResp);
}
```

转换逻辑要点：
- `String.valueOf(request.startTimeMillis())` —— Long → SDK 期望的 String
- 用 `QueryLtsLogParams.SearchTypeEnum.fromValue(request.searchType())` 容忍未知值（SDK 内部 `fromValue` 对未知值返回新实例而非抛异常）
- `__time__` 字段在 DTO 命名为 `cursorTime`，setter 调用 `body.setTime(...)`（SDK 字段在 Java 端的名字是 `time`）

### 8. application.yml 限流配置

```yaml
huaweicloud:
  # ... 已有项
  lts-region: ${HUAWEICLOUD_LTS_REGION:cn-southwest-2}

resilience4j:
  ratelimiter:
    instances:
      # ... 已有的 ces-readonly / aom-readonly / apm-readonly / ces-write
      lts-readonly:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 0
```

### 9. 单元测试矩阵（必交付）

`LtsLogAdapterImplTest`：

| ID | 方法 | 用例 | 期望 |
|---|---|---|---|
| UT-01 | listLogs | 全合法参数（含 query / keywords / labels / 游标） | SDK Request body 字段全部对齐，`start_time`/`end_time` 是 String 形式毫秒，`__time__` 与 `cursor_time` DTO 字段映射正确 |
| UT-02 | listLogs | SDK 抛 429 (`ClientRequestException`) | 重试 3 次后 `UPSTREAM_THROTTLED`，retryable=true，`upstreamTraceId` 透传 |
| UT-03 | listLogs | SDK 抛 401 | 不重试，`UPSTREAM_AUTH_FAILED` |
| UT-04 | listLogs | SDK 抛 5xx (`ServerResponseException`) | 重试 3 次后 `UPSTREAM_ERROR` |
| UT-05 | listLogs | SDK 抛 `RequestTimeoutException` | `TIMEOUT` |
| UT-06 | listLogs | 响应 `analysisLogs` 非空 | DTO `analysisLogs` 字段透传 |
| UT-07 | listLogContext | 合法参数 | SDK Request body 字段对齐，DTO `logs` 顺序与 SDK 一致 |
| UT-08 | listLogContext | SDK 抛 429 | 重试后 `UPSTREAM_THROTTLED` |
| UT-09 | listLogContext | 响应 `isQueryComplete=false` | DTO 透传 |

`LtsClientConfigTest`：Spring context 启动 + Bean 非 null，参考 `CesClientConfigTest`。

## 验收标准

- [ ] `mvn -pl agentic-adapter-lts -am test` 全绿
- [ ] `mvn -pl agentic-mcp -am compile` 全绿（确认新模块不影响主应用启动）
- [ ] `LtsClient` Bean 在 Spring 启动时成功初始化（用 mock region 也能装配）
- [ ] 2 个方法在 mock SDK 抛 429 / 401 / 5xx / Timeout 下都映射到正确 `ErrorCode`
- [ ] Checkstyle 0 violations
- [ ] SDK Request/Response 类型**没有泄漏**到 adapter 之外（`grep -r "com.huaweicloud.sdk.lts" agentic-monitoring agentic-mcp` 应无结果）

## AI 易错点提醒

1. **`start_time` / `end_time` 在 SDK 里是 String，不是 Long**。SDK 字段类型见 `QueryLtsLogParams`：`private String startTime`、`private String endTime`，Javadoc 说"UTC 毫秒级"。Adapter DTO 对外暴露 `Long startTimeMillis` 与 CES / AOM 对齐，转 SDK 时用 `String.valueOf(...)`；**不要**为了"匹配 SDK"而把 DTO 也写成 String。
2. **`__time__` 字段名带前后双下划线**。SDK Java 字段名只叫 `time`（`getTime()` / `setTime(...)`），JSON 键才是 `__time__`。DTO 字段建议命名 `cursorTime` 提高可读性，转换器里调 `body.setTime(cursorTime)`。
3. **分页是行号游标，不是 offset**。`line_num` + `__time__` 是一对游标值，从上次响应里 `logs[lastIndex].lineNum` 与对应时间拿到；**不要**自创 page/offset 概念，也不要把 `scroll_id` 和 `line_num` 混用——它们是两种独立分页模式。
4. **`search_type` 仅取 `forwards` / `backwards`**。首次查询应传 null，上游默认 `init`。按 ADR-004 容忍策略，DTO 用 String 而非枚举，转 SDK 时 `QueryLtsLogParams.SearchTypeEnum.fromValue(value)`（SDK 该方法对未知值返回新实例不抛异常）。
5. **`is_analysis_query=true` 时响应走 `analysisLogs` 而非 `logs`**。`analysisLogs: List<Object>` 是 SDK 故意保留通用类型——adapter 不要尝试反序列化成强类型；DTO 字段也保 `List<Object>`，由上层 service 自行解释。
6. **`LogContents` 在 SDK 里**只有 `content` / `line_num` / `labels` 三个字段，没有 `host_name` / `host_ip` / `log_id`（这些是别的 SDK 类的字段）。DTO `LtsLogEntry` 只保留这 3 个，不要造假字段。
7. **LtsClient 必须用 `credentialsWithProjectId`**：LTS URI 含 `{project_id}` 但 `LtsMeta` 没注册 path requestField，SDK 从凭据自动注入。只用 AK/SK 的 `credentials(...)` 会让请求带空 projectId 导致 404。
8. **DTO 全用 `record`**，构造、equals、hashCode 都不用写；**不要**给 adapter DTO 加 Lombok / 自定义 getter。
9. **LTS SDK 版本与其他华为云 SDK 共用 `${huaweicloud-sdk.version}`（3.1.177）**：经 Maven Central 验证 `huaweicloud-sdk-lts:3.1.177` 可拉到，字段与 3.1.194 完全一致。**不要**为 LTS 单独引入版本属性。
10. **`labels` 在响应里 SDK 类型是 `Map<String, String>`**（已确认），不会出现 Object 兜底问题。DTO 直接 `Map<String, String>`。
11. **`ltsRegion` 默认 `cn-north-9`**（用户确认）。`application.yml` 同步给到此默认值，覆盖通过环境变量 `HUAWEICLOUD_LTS_REGION`。

## 完成后

PR：`feat(T16): agentic-adapter-lts skeleton with listLogs / listLogContext`。

PR 描述附上：
- 使用的 LTS SDK 版本：`${huaweicloud-sdk.version}` = 3.1.177（共用，无 override）
- `huaweicloud.lts-region` 默认值：`cn-north-9`
- 单元测试 UT-01 ~ UT-09 对应表
- 遗留项清单（contract test / 冒烟 / 健康检查 / monitoring + tool / 结构化日志查询 4 项）
