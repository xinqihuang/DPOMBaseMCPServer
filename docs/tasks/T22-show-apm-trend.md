# T22 — 实现 show_apm_trend tool

> 状态: **Done** · 估时: 1d · 依赖: T03（APM client 已就绪）· 关联 spec: `docs/specs/tools/show_apm_trend.md`

## 目标

为智能运维 Agent 提供 `show_apm_trend` 工具，按 `monitor_item_id × view_config × 时间窗`
拉取 APM 监控项趋势数据（折线 / 汇总表 / 明细表）。

按 T19 §4.1 准则，DTO **无损覆盖** SDK `ShowTrendResponse` + `TrendParam` + `TrendView`
+ `FieldItem` 的全部字段（共 6 个 SDK model，27 个独立字段）。

## 范围

**做**:

1. 新建 `ApmTrendAdapter` 接口 + `ApmTrendAdapterImpl` 实现（与 `ApmAlarmAdapter` 并列，
   同模块 `agentic-adapter-apm`）
2. 新建 `ApmTrendService`（与 `ApmAlarmService` 并列，同模块 `agentic-monitoring`）
3. 新 tool：`ApmTrendTool`，name = `show_apm_trend`
4. 新 DTO（6 个 record）：
   - `ApmTrendRequest`（businessId + viewConfig + 5 outer 字段）
   - `ApmTrendViewConfig`（12 字段，对齐 SDK `TrendView`，含 `fieldItemList: List<ApmTrendFieldItem>`）
   - `ApmTrendFieldItem`（7 字段，对齐 SDK `FieldItem`）
   - `ApmTrendResponse`（`lineList` + `latestDataTime`）
   - `ApmTrendLine`（6 字段，对齐 SDK `FrontLine`，含 `pointList: List<ApmTrendPoint>`）
   - `ApmTrendPoint`（`time: Long`, `value: Object`）
5. `McpServerConfig` 注册新工具（紧跟 `apmAlarmNotifyTool` 之后）
6. 测试：
   - `ApmTrendToolTest` (UT-T1 ~ UT-T3)
   - `ApmTrendServiceTest` (UT-S1 ~ UT-S6)
   - `ApmTrendAdapterImplTest` (UT-A1 ~ UT-A2，4 异常 case)
   - `ApmShowTrendContractTest` (TC-01)
7. 样本 JSON：`sdk-samples/apm/show-trend-response.json`，覆盖 `value: Number` + `value: String` 两种
8. 更新 `docs/tasks/README.md` 加 T22 行

**不做**:

- ❌ 不做客户端时间窗对齐 / 聚合 / 折线渲染
- ❌ 不解析 `FrontPoint.value` 内部结构（保留 `Object`）
- ❌ 不复用 `ApmAlarmAdapter` / `ApmAlarmService`——领域不同，独立模块更清晰
- ❌ 不新增 RateLimiter（复用 `apm-readonly`）

## 前置阅读

1. `docs/specs/tools/show_apm_trend.md` — 完整 spec
2. `docs/tasks/T20-list-apm-alarm-data.md` — APM adapter / service 骨架来源（同款架构）
3. `CLAUDE.md` §4.1 — DTO 无损投影准则
4. `agentic-adapter/agentic-adapter-apm/.../ApmAlarmAdapterImpl.java` — 同模块已有实现，本任务镜像其结构
5. SDK 真实 schema（必读，禁猜）：
   - `services/apm/.../model/ShowTrendRequest.java` / `ShowTrendResponse.java`
   - `services/apm/.../model/TrendParam.java` / `TrendView.java` / `FieldItem.java`
   - `services/apm/.../model/FrontLine.java` / `FrontPoint.java`

## 产物清单

```
docs/specs/tools/show_apm_trend.md                                ← 已生成
docs/tasks/T22-show-apm-trend.md                                  ← 本任务卡
docs/tasks/README.md                                              ← 修改: 加 T22 行

agentic-adapter/agentic-adapter-apm/
  src/main/java/com/huawei/smartom/agentic/adapter/apm/
    ApmTrendAdapter.java                                          ← 新增: 接口
    ApmTrendAdapterImpl.java                                      ← 新增: 实现
    dto/
      ApmTrendRequest.java                                        ← 新增
      ApmTrendViewConfig.java                                     ← 新增 (12 字段)
      ApmTrendFieldItem.java                                      ← 新增 (7 字段)
      ApmTrendResponse.java                                       ← 新增
      ApmTrendLine.java                                           ← 新增 (6 字段)
      ApmTrendPoint.java                                          ← 新增 (2 字段, value 是 Object)
  src/test/java/com/huawei/smartom/agentic/adapter/apm/
    ApmTrendAdapterImplTest.java                                  ← 新增
    contract/ApmShowTrendContractTest.java                        ← 新增
  src/test/resources/sdk-samples/apm/
    show-trend-response.json                                      ← 新增

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/apm/
    ApmTrendService.java                                          ← 新增
  src/test/java/com/huawei/smartom/agentic/monitoring/apm/
    ApmTrendServiceTest.java                                      ← 新增

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/ApmTrendTool.java                                        ← 新增
    config/McpServerConfig.java                                   ← 修改: 注册 ApmTrendTool
  src/test/java/com/huawei/smartom/agentic/mcp/
    tool/ApmTrendToolTest.java                                    ← 新增
```

## 关键技术要求

### 1. DTO 嵌套设计（关键决策）

`ApmTrendRequest` 持有 `viewConfig: ApmTrendViewConfig`，**不拍平**。MCP Tool 入参签名：

```java
@Tool(name = "show_apm_trend", description = "...")
public Object showTrend(
        @ToolParam(...) Long businessId,
        @ToolParam(...) Long instanceId,
        @ToolParam(...) Long monitorItemId,
        @ToolParam(...) Long envId,
        @ToolParam(...) String startTime,
        @ToolParam(...) String endTime,
        @ToolParam(description = "View config: view_type / metric_set / ...")
        ApmTrendViewConfig viewConfig) {
    ...
}
```

Spring AI MCP 会从 record 反射出 JSON Schema，Agent 端看到嵌套对象。

### 2. 枚举映射

`view_type` 和 `table_direction` 是 SDK 枚举，adapter 必须：
- DTO 字段类型用 `String`（不要把 SDK 枚举类型泄漏到 adapter 之外）
- adapter 映射时：`TrendView.ViewTypeEnum.fromValue(dto.viewType())`
- `fromValue` 抛 `IllegalArgumentException` → adapter 包成 `InvalidParamException`（业务校验已在 service 层
  拦下，这里是兜底，避免 SDK 异常透传）

### 3. `FrontPoint.value` 保留 `Object`

按 §4.1 准则不假设类型。DTO record 字段直接写 `Object value`，Jackson 在序列化时按
实际运行时类型输出（Long/Double/String）。契约测试 **必须**覆盖 Number + String 两种 case。

### 4. `latest_data_Time` 字段名修正

SDK 原 JSON key 是 `latest_data_Time`（大写 T）。DTO 内 Java 字段名写 `latestDataTime`，
adapter 直接 `sdkResp.getLatestDataTime()` 取值，DTO 序列化输出 `latest_data_time`（统一
snake_case）。**不要在 DTO 上加 `@JsonProperty("latest_data_Time")` 暴露 SDK 拼写问题
到我们的外部契约**。

### 5. Service 校验

```java
public ApmTrendResponse showTrend(ApmTrendRequest r) {
    Validations.requireNonNull(r, "request");
    Validations.requireNonNull(r.viewConfig(), "view_config");
    Validations.requireNonBlank(r.viewConfig().viewType(), "view_config.view_type");
    if (!VALID_VIEW_TYPES.contains(r.viewConfig().viewType())) {
        throw new InvalidParamException("view_config.view_type must be one of: trend/sumtable/rawtable");
    }
    Validations.requireNonBlank(r.viewConfig().metricSet(), "view_config.metric_set");
    Validations.requireNonBlank(r.startTime(), "start_time");
    Validations.requireNonBlank(r.endTime(), "end_time");
    Long effective = r.businessId() != null ? r.businessId() : properties.getApmBusinessId();
    if (effective == null) {
        throw new InvalidParamException("business_id is required");
    }
    return adapter.showTrend(r);
}
```

### 6. 契约测试

参照 T21 `ApmListAlarmNotifyContractTest`：
- mock client 返回反序列化样本
- 调用 `adapter.showTrend(request)`
- **断言 `lineList` 所有 6 字段 × `pointList` 所有 2 字段 × 顶层 `latestDataTime`**
- 样本含至少 2 条 `FrontLine`，每条 `pointList` ≥ 2 点，其中一个 `value` 是数字，另一个是字符串
- 删任一 DTO 字段 → 编译失败

## 验收标准

- [ ] `mvn -pl agentic-adapter-apm,agentic-monitoring,agentic-mcp -am test` 全绿
- [ ] spec 状态翻 Approved
- [ ] T22 状态 Done，README.md 加 T22 行
- [ ] MCP Inspector 看到 `show_apm_trend`，schema 含嵌套 `view_config`
- [ ] Checkstyle 0
- [ ] DTO 无损：响应侧 8 字段 + viewConfig 12 字段 + fieldItem 7 字段全留，契约测试覆盖
- [ ] **不破坏 T20 / T21 路径**

## AI 易错点提醒

1. **`FrontPoint.value` 是 `Object`**——DTO 必须保留 `Object`，不要私自改成 `Number` /
   `String`。SDK 那边可能是 Long/Double/String，假设其一会丢信息或 ClassCastException。
2. **`latest_data_Time` 大小写**：SDK JSON key 含大写 `T`，DTO 字段名用 `latestDataTime`
   并输出 `latest_data_time`（snake_case）。adapter 用 `sdkResp.getLatestDataTime()` 取值
   即可，不要碰 SDK JSON 注解。
3. **`view_type` / `table_direction` 是 SDK 枚举**：DTO 用 `String`，adapter 端 fromValue
   兜底 catch `IllegalArgumentException` 包成 `InvalidParamException`。
4. **不拍平 `view_config`**：Agent 看到的就是嵌套 record（用户决策 B）。如果拍平成
   12 个 outer `@ToolParam`，AI 易错点 #1 也会拍平 `field_item_list` 这种 List<复合对象>，
   schema 不可表达，必崩。
5. **`field_item_list` 是可选**：SDK 允许 null/空 list，DTO 用 `List<ApmTrendFieldItem>`
   且允许 null。adapter mapping 时 `null` 透传给 SDK，**不要**自作主张换成空 list（语义
   不一样，SDK 内部可能据此区分"未指定"vs"显式空"）。
6. **新模块独立**：本任务**不要**复用 `ApmAlarmAdapter` / `ApmAlarmService`。Trend 和
   Alarm 是不同领域，独立模块便于将来按域演进。
7. **不要凭记忆改 SDK 字段类型**：写 DTO 前对照 SDK 源码 model 类（curl 拉取或网页查），
   `precision: Integer` / `span: Boolean` / `time: Long` 等保持 SDK 原类型。

## 完成后

PR 标题：`feat(T22): show_apm_trend MCP tool with nested viewConfig + lossless DTO`。PR 描述附上：

- spec 链接
- 测试用例对应表
- MCP 工具总数变化（18 → 19）
- 遗留项：冒烟脚本 / Micrometer 看板
