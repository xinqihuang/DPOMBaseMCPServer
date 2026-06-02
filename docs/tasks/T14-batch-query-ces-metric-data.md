# T14 — 实现 batch_query_ces_metric_data + CES 参数枚举目录

> 状态: **Done**（提交 `ce6fd6c`，2026-06-02 完成） · 估时: 1d · 依赖: T04（CES adapter 基座）/ T07（`query_ces_metric_data` 已落地的服务层模式）· 关联 spec: `docs/specs/tools/batch_query_ces_metric_data.md` · 关联 ADR: `docs/decisions/ADR-004-ces-enum-catalog.md`

## 目标

1. 在 CES adapter 上新增 `BatchListMetricData` 能力，让 Agent 一次拉取多条指标数据
2. 将 `query_ces_metric_data` 与 `batch_query_ces_metric_data` 的入参规整为类型安全枚举（filter/period 严格、namespace/metric 宽容目录），消除散落的 `Set` 校验
3. 为两个 tool 补齐基本的 Tool 层单元测试

## 范围

**做**:
- `BatchListMetricData` SDK 调用包装：adapter 接口 + 实现 + DTO
- 业务编排 `CesBatchMetricDataService`（参数校验）
- MCP tool `CesBatchMetricDataTool` 注册 + 错误转换
- 5 个 CES 枚举：`CesMetricFilter` / `CesMetricPeriod`（严格）+ `CesNamespace` / `CesDimensionKey` / `CesMetric`（宽容目录，覆盖 ECS 基础监控 19 条）
- 重构 `CesQueryMetricDataRequest` / `CesBatchQueryMetricDataRequest` 使用枚举字段
- Tool 层基本校验：filter/period 非空 + 字符串→枚举解析
- 单元测试：`CesMetricDataToolTest`（7 条）+ `CesBatchMetricDataToolTest`（7 条）

**不做**（防止任务蔓延）:
- ❌ Service 层 / Adapter 层 UT、Contract Test、冒烟脚本（spec §6/§7 列出，但本期未交付，进遗留项）
- ❌ AOM / APM 对应批量查询
- ❌ 缓存层
- ❌ namespace / metric 严格枚举（按 ADR-004 决议不做）

## 前置阅读

**必读**:
1. `docs/specs/tools/batch_query_ces_metric_data.md` — 完整 spec
2. `docs/decisions/ADR-004-ces-enum-catalog.md` — 枚举分档决策
3. `CLAUDE.md` §3 / §4 — 编码规范、SDK 不泄漏、错误码统一

**强烈推荐**:
4. `docs/specs/tools/list_ces_metrics.md` — list_ces_metrics（前置发现 tool）spec
5. CES API：https://support.huaweicloud.com/intl/en-us/api-ces/
6. ECS 基础指标：https://support.huaweicloud.com/intl/en-us/usermanual-ecs/ecs_03_1002.html

## 产物清单

```
docs/specs/tools/
  batch_query_ces_metric_data.md                        ← 新增 spec
docs/decisions/
  ADR-004-ces-enum-catalog.md                           ← 新增 ADR
docs/tasks/
  T14-batch-query-ces-metric-data.md                    ← 本任务卡
  README.md                                             ← 新增 T14 行

agentic-adapter/agentic-adapter-ces/
  src/main/java/com/huawei/smartom/agentic/adapter/ces/
    CesMetricsAdapter.java                              ← 加 batchQueryMetricData 方法
    CesMetricsAdapterImpl.java                          ← 实现 + 改 toShowMetricDataSdkRequest 用枚举
    dto/
      CesMetricFilter.java                              ← 新增（严格枚举）
      CesMetricPeriod.java                              ← 新增（严格枚举）
      CesNamespace.java                                 ← 新增（宽容目录）
      CesDimensionKey.java                              ← 新增（宽容目录）
      CesMetric.java                                    ← 新增（ECS 基础监控 19 条）
      CesBatchMetricQuery.java                          ← 新增（单条查询项）
      CesBatchQueryMetricDataRequest.java               ← 新增（批量请求）
      CesBatchMetricResult.java                         ← 新增（单条结果）
      CesBatchQueryMetricDataResponse.java              ← 新增（批量响应）
      CesQueryMetricDataRequest.java                    ← 修改 filter/period 改为枚举

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/ces/
    CesBatchMetricDataService.java                      ← 新增
    CesMetricDataService.java                           ← 修改：删 ALLOWED_FILTERS/PERIODS

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/CesBatchMetricDataTool.java                    ← 新增
    tool/CesMetricDataTool.java                         ← 修改：filter/period 字符串→枚举解析
    config/McpServerConfig.java                         ← 注册 CesBatchMetricDataTool
  src/test/java/com/huawei/smartom/agentic/mcp/
    tool/CesMetricDataToolTest.java                     ← 新增（7 条 UT）
    tool/CesBatchMetricDataToolTest.java                ← 新增（7 条 UT）
```

## 关键技术要求

### 1. 枚举设计（按 ADR-004 分档）

**严格枚举**（DTO 直接持有）：

```java
public enum CesMetricFilter {
    AVERAGE("average"), MAX("max"), MIN("min"), SUM("sum"), VARIANCE("variance");

    @JsonValue public String getValue() { return value; }

    @JsonCreator
    public static CesMetricFilter fromValue(String value) {
        // 未知值抛 IllegalArgumentException，Tool 层 catch 转 InvalidParamException
    }
}
```

`CesMetricPeriod` 同理，对应秒数 1/60/300/1200/3600/14400/86400。

**宽容目录**（DTO 仍持 String）：

```java
public enum CesNamespace {
    SYS_ECS("SYS.ECS"), SYS_RDS("SYS.RDS"), /* ... */;

    @JsonCreator
    public static CesNamespace fromValue(String value) {
        // 未知值返回 null，不抛异常
    }
}
```

`CesMetric` 每条枚举常量绑定 `(id, namespace, primaryDimension, unit, description)`，供文档生成 / IDE 补全使用。

### 2. Adapter 实现：枚举→SDK 映射

```java
.withFilter(ShowMetricDataRequest.FilterEnum.fromValue(request.filter().getValue()))
.withPeriod(ShowMetricDataRequest.PeriodEnum.fromValue(request.period().getSeconds()))
```

批量版本注意 SDK `PeriodEnum.fromValue` 接收**字符串**，要 `String.valueOf(seconds)`。

### 3. Tool 层基本校验

```java
private CesMetricFilter parseFilter(String filter) {
    if (filter == null) {
        throw new InvalidParamException("filter is required");
    }
    try {
        return CesMetricFilter.fromValue(filter);
    } catch (IllegalArgumentException e) {
        throw new InvalidParamException(e.getMessage());
    }
}
```

Service 层不再校验 filter/period 取值集合（类型强制），但仍校验：
- `from < to`
- `dimensions` 长度 [1, 4]
- `metrics` 长度 [1, 500]（批量）
- namespace 正则

### 4. 单元测试矩阵

`CesMetricDataToolTest` / `CesBatchMetricDataToolTest` 每个含 7 条用例（见 spec §6）：

| ID | 用例 |
|---|---|
| 1 | success passthrough：字符串解析为枚举后透传 |
| 2 | filter null → INVALID_PARAM，不调 service |
| 3 | period null → INVALID_PARAM |
| 4 | filter 未知 → INVALID_PARAM，errorMessage 含值 |
| 5 | period 未知 → INVALID_PARAM |
| 6 | service InvalidParamException → ErrorResponse |
| 7 | service UpstreamException → ErrorResponse 含 trace id |

## 验收标准

实际完成项（spec §7 mapping）：

- [x] Tool 层 14 条 UT 全部通过（`mvn test` 在所有相关模块 BUILD SUCCESS）
- [x] MCP 配置文件注册 `CesBatchMetricDataTool`
- [x] 日志含 metricCount / filter / period / from / to
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（`ce6fd6c`）
- [ ] Service / Adapter / Contract Test（后续任务补）
- [ ] 贵阳冒烟脚本（后续任务补）
- [ ] Micrometer 指标 + README 示例（后续任务补）

## AI 易错点提醒

**spec §4 已列出**：
1. SDK `BatchListMetricDataRequestBody.PeriodEnum.fromValue(...)` 接收字符串
2. Batch dimensions 是结构化对象，不是 dim0 拼接
3. 响应 datapoint 不带 unit，unit 在父级
4. 响应类是 `BatchMetricData`，不要和 `MetricInfoList` 混淆

**枚举改造特有**：
5. **Lambda 参数名长度 ≥ 2**：Checkstyle `LambdaParameterName` 规则要求 `^\w{2,64}$`，不要写 `d -> ...`，要 `dim -> ...`
6. **`@JsonValue` + `@JsonCreator` 配对**：Jackson 反序列化枚举需要二者都有；只加 `@JsonValue` 会让 LLM 传字符串时反序列化失败
7. **宽容目录 `fromValue` 返回 null，严格枚举抛异常**：两种语义不要混淆，Tool 层只对严格枚举做 `try/catch → InvalidParamException`
8. **`CesMetric` 不要锁死 DTO**：DTO `metricName` 仍是 String，`CesMetric` 仅用于文档 / 查找；锁死会阻塞新 metric 透传

## 完成后

PR：`feat(ces): add batch_query_ces_metric_data tool and CES enum catalog`（已提交：`ce6fd6c`）。

PR 描述附上：
- 关联 spec 与 ADR
- 14 条 UT 列表
- 遗留项（Service/Adapter UT、Contract Test、冒烟脚本）
