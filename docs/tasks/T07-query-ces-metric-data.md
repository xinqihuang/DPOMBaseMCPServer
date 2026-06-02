# T07 — 实现 query_ces_metric_data

> 状态: **Done**（提交 `4c346d6`，回溯补卡） · 估时: 1d · 依赖: T04（CES adapter 基座）/ T05（list_ces_metrics 沉淀的模式） · 关联 spec: `docs/specs/tools/query_ces_metric_data.md` · 关联 ADR: `docs/decisions/ADR-004-ces-enum-catalog.md`

## 目标

按 `docs/specs/tools/query_ces_metric_data.md` 实现完整的 `query_ces_metric_data` MCP tool：让 Agent 拉取单条 CES 指标在指定时间区间、聚合粒度下的数据点序列。

## 范围

**做**:
- CES adapter 新增 `queryMetricData` 能力（封装 `ShowMetricData` SDK 调用）
- 自定义 DTO：`CesQueryMetricDataRequest` / `CesQueryMetricDataResponse` / `CesDatapoint`
- 业务编排 `CesMetricDataService`（参数校验：namespace 正则 / dimensions 长度 / from-to / filter-period 必填）
- MCP tool `CesMetricDataTool` 注册 + 错误码转换
- Tool 层把字符串/整数入参解析为 `CesMetricFilter` / `CesMetricPeriod` 枚举（后由 T14 `ce6fd6c` 锁死为枚举字段，详见 ADR-004）

**不做**（防止任务蔓延）:
- ❌ 批量查询（拆到 T14 `batch_query_ces_metric_data`）
- ❌ Service / Adapter 层 UT、Contract Test、冒烟脚本（spec §6/§7 列出，本期未交付，进遗留项）
- ❌ AOM / APM 对应 tool（拆到 T08 / 后续）

## 前置阅读

**必读**:
1. `docs/specs/tools/query_ces_metric_data.md` — 完整 spec
2. `CLAUDE.md` §3 / §4 — 编码规范、SDK 不泄漏、错误码统一
3. `docs/specs/tools/list_ces_metrics.md` — 前置发现 tool 与命名约定

**强烈推荐**:
4. `docs/decisions/ADR-004-ces-enum-catalog.md` — 枚举分档决策（T14 进一步落地）
5. CES `ShowMetricData` API：https://support.huaweicloud.com/intl/en-us/api-ces/

## 产物清单

```
docs/specs/tools/
  query_ces_metric_data.md                            ← 本任务对应 spec（回溯补）
docs/tasks/
  T07-query-ces-metric-data.md                        ← 本任务卡（回溯补）

agentic-adapter/agentic-adapter-ces/
  src/main/java/com/huawei/smartom/agentic/adapter/ces/
    CesMetricsAdapter.java                            ← 加 queryMetricData 方法
    CesMetricsAdapterImpl.java                        ← 新增 toShowMetricDataSdkRequest / toQueryMetricDataResponseDto / toDatapoint
    dto/
      CesQueryMetricDataRequest.java                  ← record，filter/period 后续由 T14 改为枚举
      CesQueryMetricDataResponse.java                 ← record
      CesDatapoint.java                               ← record

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/ces/
    CesMetricDataService.java                         ← 新增（namespace 正则 + dimensions 1-4 + from<to + 必填项）

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/CesMetricDataTool.java                       ← 新增 @Tool 注册，filter/period 字符串→枚举解析
    config/McpServerConfig.java                       ← 注册 CesMetricDataTool
  src/test/java/com/huawei/smartom/agentic/mcp/
    tool/CesMetricDataToolTest.java                   ← 7 条 UT（T14 阶段补齐 Tool 层覆盖）
```

## 关键技术要求

### 1. DTO 设计

`CesQueryMetricDataRequest` 一开始用 `String filter` / `Integer period`，T14（`ce6fd6c`）按 ADR-004 重构为枚举：

```java
public record CesQueryMetricDataRequest(
        String namespace,
        String metricName,
        List<CesMetricDimension> dimensions,
        CesMetricFilter filter,
        CesMetricPeriod period,
        Long from,
        Long to) {
}
```

`CesDatapoint` 平铺 max/min/average/sum/variance（CES 上游就是这种结构）。

### 2. Adapter 关键映射

```java
ShowMetricDataRequest sdk = new ShowMetricDataRequest()
        .withNamespace(request.namespace())
        .withMetricName(request.metricName())
        .withFilter(ShowMetricDataRequest.FilterEnum.fromValue(request.filter().getValue()))
        .withPeriod(ShowMetricDataRequest.PeriodEnum.fromValue(request.period().getSeconds()))
        .withFrom(request.from())
        .withTo(request.to());

for (int idx = 0; idx < dims.size(); idx++) {
    String value = dim.name() + "," + dim.value();
    switch (idx) {
        case 0 -> sdk.setDim0(value);
        case 1 -> sdk.setDim1(value);
        case 2 -> sdk.setDim2(value);
        case 3 -> sdk.setDim3(value);
        default -> {}  // Service 层已拦下 size > 4
    }
}
```

### 3. Service 层校验

- `namespace` 正则 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`
- `dimensions` 长度 [1, 4]，每个 name/value 非空
- `from` 严格小于 `to`
- `filter` / `period` / `metricName` 必填（filter / period 已是枚举，Service 只检查非 null）

### 4. Tool 层入参解析

```java
private CesMetricFilter parseFilter(String filter) {
    if (filter == null) {
        throw new InvalidParamException("filter is required");
    }
    try {
        return CesMetricFilter.fromValue(filter);
    }
    catch (IllegalArgumentException e) {
        throw new InvalidParamException(e.getMessage());
    }
}
```

`parsePeriod(Integer)` 同理走 `CesMetricPeriod.fromSeconds`。

## 验收标准（实际完成项映射 spec §7）

- [x] Tool 层 7 条 UT 全部通过（`CesMetricDataToolTest`，T14 阶段补齐）
- [x] MCP 配置文件注册 `CesMetricDataTool`
- [x] 复用 `ces-readonly` RateLimiter
- [x] 日志含 namespace / metricName / filter / period / from / to / 耗时 / upstream trace id
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（`4c346d6`，filter/period 枚举化由 `ce6fd6c` 完成）
- [ ] Service 层 UT（后续任务补 `CesMetricDataServiceTest`）
- [ ] Adapter 层 UT（后续任务补 `CesMetricsAdapterImplTest` 新增方法）
- [ ] 类型契约测试 TC-01~04（后续补）
- [ ] 贵阳冒烟脚本（后续补）
- [ ] Micrometer 指标 + README 示例（后续补）

## AI 易错点提醒

**spec §4 已列出**：
1. `dim0`…`dim3` 是 4 个独立字符串字段，不是数组；按 `dimensions` 数组顺序填入
2. 每个 `dimX` 是 `"name,value"` 拼接的字符串，不是结构化对象（与 `batch_query_ces_metric_data` 用 `MetricsDimension` 不同）
3. `ShowMetricDataRequest.PeriodEnum.fromValue(int)` 接收整数；`BatchListMetricDataRequestBody.PeriodEnum.fromValue(String)` 接收字符串——同 SDK 两套 API 类型不一致
4. 响应类是 `ShowMetricDataResponse`，数据点是 `Datapoint`（不要和 `DatapointForBatchMetric` 混淆）
5. `Datapoint.getUnit()` 来自 datapoint 自带字段，与 `list_ces_metrics` 的 `metric.unit` 来源不同，本 tool 直接透传

**实施过程的额外踩坑点**：
6. 一开始 DTO 用 `String filter` / `Integer period` 配合 `Set<String>` / `Set<Integer>` 校验，T14 重构为枚举后这段校验集合移除，Service 层只检查 `null`——回看 git history 时注意 `CesMetricDataService` 在两个提交之间形态不同
7. Tool 层 catch `IllegalArgumentException` 时不要把整个堆栈塞进 errorMessage，只取 `e.getMessage()`，否则 Agent 日志会被 Stack 填满
8. `parsePeriod(Integer)` 入参可以为 null，`CesMetricPeriod.fromSeconds(int)` 是 `int` 入参——拆箱前必须先判空，否则 NPE 会被 `SmartomException` catch 漏掉变成 INTERNAL

## 完成后

PR：`feat(ces): implement query_ces_metric_data tool`（已提交：`4c346d6`，枚举重构 `ce6fd6c`）。

PR 描述附上：
- 关联 spec
- Tool 层 UT 清单（7 条，在 T14 阶段一起合入）
- 遗留项（Service / Adapter UT、Contract Test、冒烟脚本）
