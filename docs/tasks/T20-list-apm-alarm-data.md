# T20 — 实现 list_apm_alarm_data tool

> 状态: **Done** · 估时: 0.6d · 依赖: T03（common 基础）· 关联 spec: `docs/specs/tools/list_apm_alarm_data.md` · 后置: T21（`list_alarm_notify`，复用本任务建立的 ApmAlarmAdapter + ApmAlarmService 骨架）

## 目标

为智能运维 Agent 提供 `list_apm_alarm_data` 工具，按 14 个上游过滤条件查询华为云 APM 告警记录。
按 T19 §4.1 准则，DTO **无损覆盖** SDK `AlarmDataVO` 全部 27 个字段。

## 范围

**做**:

1. 新 adapter：`ApmAlarmAdapter` 接口 + `ApmAlarmAdapterImpl`（与现有 `ApmTraceAdapter` 同模块、并列；T20 仅含 `listAlarmData` 方法）
2. 新 service：`ApmAlarmService`（仅含 `listAlarmData`，T21 后再追加 `listAlarmNotify`）
3. 新 tool：`ApmAlarmDataTool`，暴露全部 14 个上游过滤参数 + 2 个分页参数 + 1 个 businessId 头部参数（共 17 个 @ToolParam）
4. 新 DTO（3 个 record）：
   - `ApmAlarmDataRequest`（15 字段：14 body + business_id header）
   - `ApmAlarmDataResponse`（`alarms: List<ApmAlarm>` + `totalCount`）
   - `ApmAlarm`（**27 字段，AlarmDataVO 无损投影**）
5. `McpServerConfig` 注册新工具（紧跟 `apmTopologyTool` 之后）
6. 测试：`ApmAlarmDataToolTest`（UT-T1~T3）、`ApmAlarmServiceTest`（UT-S1~S5）、`ApmAlarmAdapterImplTest`（UT-A1~A2 + 4 个 SDK 异常映射）、`ApmListAlarmDataContractTest`（TC-01）
7. 样本 JSON：`sdk-samples/apm/list-alarm-data-response.json`
8. 更新 `docs/tasks/README.md` 加 T20 行

**不做**（防蔓延）:

- ❌ `list_alarm_notify`（T21）
- ❌ 改 `ApmTraceAdapter` / `ApmTraceService`
- ❌ 新增 RateLimiter（复用 `apm-readonly`）
- ❌ 改既有 `huaweicloud.apm-business-id` 配置语义
- ❌ 冒烟脚本 / Micrometer 看板 / README 示例

## 前置阅读

1. `docs/specs/tools/list_apm_alarm_data.md` — 完整 spec
2. `CLAUDE.md` §4.1（T19 PR-0 重写版）— **DTO 必须无损覆盖 SDK**
3. `agentic-adapter/agentic-adapter-apm/src/main/java/.../ApmTraceAdapterImpl.java` — APM 调用样板（x-business-id 头注入、apmRegion 配置）
4. `agentic-adapter/agentic-adapter-apm/src/test/java/.../contract/ApmShowSpanSearchContractTest.java` — T19 PR-3 契约测试范式

## 产物清单

```
docs/specs/tools/list_apm_alarm_data.md                        ← 已生成
docs/tasks/T20-list-apm-alarm-data.md                          ← 本任务卡
docs/tasks/README.md                                           ← 修改: 加 T20 行

agentic-adapter/agentic-adapter-apm/
  src/main/java/com/huawei/smartom/agentic/adapter/apm/
    ApmAlarmAdapter.java                                       ← 新增: interface
    ApmAlarmAdapterImpl.java                                   ← 新增: impl（仅 listAlarmData）
    dto/
      ApmAlarm.java                                            ← 新增: 27 字段
      ApmAlarmDataRequest.java                                 ← 新增: 15 字段
      ApmAlarmDataResponse.java                                ← 新增
  src/test/java/com/huawei/smartom/agentic/adapter/apm/
    ApmAlarmAdapterImplTest.java                               ← 新增
    contract/ApmListAlarmDataContractTest.java                 ← 新增
  src/test/resources/sdk-samples/apm/
    list-alarm-data-response.json                              ← 新增

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/apm/
    ApmAlarmService.java                                       ← 新增（仅 listAlarmData）
  src/test/java/com/huawei/smartom/agentic/monitoring/apm/
    ApmAlarmServiceTest.java                                   ← 新增

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/ApmAlarmDataTool.java                                 ← 新增
    config/McpServerConfig.java                                ← 修改: 注册 ApmAlarmDataTool
  src/test/java/com/huawei/smartom/agentic/mcp/
    tool/ApmAlarmDataToolTest.java                             ← 新增
```

## 关键技术要求

### 1. 17 个 @ToolParam（全部暴露）

用户已确认：把 14 个上游过滤参数 + 2 个分页 + 1 个 businessId 头部全部暴露给 Agent。
Tool 方法签名约 17 个参数。Spring AI 1.0.4 能处理这个规模（其他大参 tool 如
`query_lts_logs` 也是 17 个），description 中务必清晰区分两个 `business_id`：

- `business_id`（header） — APM 业务 id，必填（或来自配置默认值）
- `business_id_filter`（body） — 过滤条件，可选

### 2. Adapter 拆分 + x-business-id 复用

```java
@Component
public class ApmAlarmAdapterImpl implements ApmAlarmAdapter {
    private final ApmClient apmClient;
    private final HuaweiCloudInvocation invocation;
    private final HuaweiCloudProperties properties;

    @Override
    public ApmAlarmDataResponse listAlarmData(ApmAlarmDataRequest request) {
        Long businessId = request.businessId() == null
                ? properties.getApmBusinessId()
                : request.businessId();
        AlarmDataListRequest body = new AlarmDataListRequest()
                .withPage(request.page())
                .withPageSize(request.pageSize())
                // ... 13 个其他 body 字段 ...
        ListAlarmDataRequest sdkReq = new ListAlarmDataRequest().withBody(body);
        if (businessId != null) {
            sdkReq.setXBusinessId(businessId);
        }
        ListAlarmDataResponse sdkResp = invocation.execute(
                "apm-readonly", "huaweicloud-retryable", "apm.listAlarmData",
                () -> apmClient.listAlarmData(sdkReq));
        return toResponseDto(sdkResp);
    }
}
```

### 3. DTO 无损（T19 §4.1）

`ApmAlarm` **必须**含 27 个字段：

```
id (Long), gmtCreate (String), regionAlarmEventId (Long),
businessName, appName, versionNumber (Integer), alarmRuleType,
gmtModify (String), processUnit, region,
instanceId (Long), ipAddress, instanceName,
envId (Long), businessId (Long), templateId (Long),
alarmRuleId (Long), monitorItemId (Long),
collectorId (Integer), collectorName,
alarmRuleName, alarmRuleExpression,
alarmFirstTime (String), alarmLastTime (String),
alarmLevel, restrainKey, status
```

字段类型对齐 SDK：`collector_id` / `version_number` 是 `Integer`，时间是 `String`
（不是 `OffsetDateTime`，与 CES v2 不同）。

### 4. Service 校验

```java
private void validate(ApmAlarmDataRequest r) {
    Long effective = r.businessId() != null ? r.businessId() : properties.getApmBusinessId();
    if (effective == null) {
        throw new InvalidParamException("business_id is required (no apm-business-id default configured)");
    }
    if (r.page() != null && r.page() < 1) {
        throw new InvalidParamException("page must be >= 1");
    }
    if (r.pageSize() != null && (r.pageSize() < 1 || r.pageSize() > 100)) {
        throw new InvalidParamException("page_size must be in [1, 100]");
    }
}
```

注：T20 没有像 T21 那样需要校验 `alarm_data_id`，因为 list_apm_alarm_data 不要求该字段。

### 5. 契约测试（参考 T19 PR-3 范式）

参照 `ApmShowSpanSearchContractTest`：
- 加载 `sdk-samples/apm/list-alarm-data-response.json` 反序列化为 SDK `ListAlarmDataResponse`
- mock client 返回它
- 调用 `adapter.listAlarmData(request)`
- 逐字段断言 27 个 DTO 字段（删任一字段会编译失败）

### 6. McpServerConfig 注册

构造参数加 `ApmAlarmDataTool apmAlarmDataTool`，`toolObjects(...)` 列表追加，Javadoc 同步。

## 验收标准

- [ ] `mvn -pl agentic-adapter-apm,agentic-monitoring,agentic-mcp -am test` 全绿
- [ ] spec 状态翻 Approved
- [ ] T20 状态 Done，README.md 加 T20 行
- [ ] MCP Inspector 看到 `list_apm_alarm_data`
- [ ] Checkstyle 0
- [ ] DTO 无损（27 字段），契约测试通过

## AI 易错点提醒

1. **两个 `business_id`**：
   - HTTP header `x-business-id` (Long) — 租户级业务 id
   - body 中 `business_id` (Long) — 过滤条件
   DTO 字段建议 `businessId` (header) + `businessIdFilter` (body)，避免歧义。
2. **时间字段是 `String` 不是 `OffsetDateTime`**（不像 CES v2 alarm history）。DTO 保 String。
3. **`envList` 是 `List<Long>`**，不要错写 String 或 Integer。
4. **`collector_id` 是 `Integer`，但 `instance_id` / `business_id` 等其他 id 是 `Long`** —— 对齐 SDK 类型。
5. **不要为 T20 单独建 ApmClient Bean**，复用现有的 `apmClient`。
6. **`ApmAlarmService` 只放 listAlarmData 方法**；T21 会追加 listAlarmNotify，不在本 PR 范围。
7. **`AlarmDataListRequest` 在 SDK 中名字带 List**——是"列表查询的请求体"，不是"List 类型的请求"。
8. **响应顶层 `total_count` 字段**（snake_case）映射到 DTO `totalCount`。

## 完成后

PR 标题：`feat(T20): list_apm_alarm_data MCP tool with lossless ApmAlarm DTO`。
PR 描述附上：
- spec 链接
- 测试用例对应表
- 注册后的 MCP 工具总数（16 → 17）
- 遗留项：T21（list_alarm_notify）/ 冒烟脚本 / Micrometer 看板
