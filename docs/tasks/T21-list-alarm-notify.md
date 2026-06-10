# T21 — 实现 list_alarm_notify tool

> 状态: **Done** · 估时: 0.3d · 依赖: T20（`ApmAlarmAdapter` + `ApmAlarmService` 骨架已存在）· 关联 spec: `docs/specs/tools/list_alarm_notify.md`

## 目标

为智能运维 Agent 提供 `list_alarm_notify` 工具，按 `alarm_data_id`（来自 T20 `list_apm_alarm_data` 响应）拉取该告警的通知投递记录。

按 T19 §4.1 准则，DTO **无损覆盖** SDK `FrontAlarmNotifyResult` 全部 8 个字段。

## 范围

**做**:

1. 在 T20 建好的 `ApmAlarmAdapter` 上**追加** `listAlarmNotify` 方法
2. 在 T20 建好的 `ApmAlarmService` 上**追加** `listAlarmNotify` 方法
3. 新 tool：`ApmAlarmNotifyTool`，暴露 5 个参数（businessId / alarmDataId / page / pageSize / region）
4. 新 DTO（3 个 record）：
   - `ApmAlarmNotifyRequest`（5 字段）
   - `ApmAlarmNotifyResponse`（`notifications: List<ApmAlarmNotification>` + `totalCount`）
   - `ApmAlarmNotification`（**8 字段，FrontAlarmNotifyResult 无损投影**）
5. `McpServerConfig` 注册新工具（紧跟 `apmAlarmDataTool` 之后）
6. 测试：`ApmAlarmNotifyToolTest`（UT-T1~T3）、扩展 `ApmAlarmServiceTest`（UT-S1~S5 for notify）、扩展 `ApmAlarmAdapterImplTest`（add listAlarmNotify cases）、`ApmListAlarmNotifyContractTest`（TC-01）
7. 样本 JSON：`sdk-samples/apm/list-alarm-notify-response.json`
8. 更新 `docs/tasks/README.md` 加 T21 行

**不做**:

- ❌ 通知模板查询 / 重发通知（写操作）
- ❌ 改 T20 已交付的 `listAlarmData` 路径
- ❌ 新增 RateLimiter（复用 `apm-readonly`）

## 前置阅读

1. `docs/specs/tools/list_alarm_notify.md` — 完整 spec
2. `docs/tasks/T20-list-apm-alarm-data.md` — 上游任务卡（adapter / service 骨架来源）
3. `CLAUDE.md` §4.1 — DTO 无损投影准则
4. T20 中产生的 `ApmAlarmAdapterImpl.java` / `ApmAlarmService.java` —— 必读，本 PR 在其上扩展

## 产物清单

```
docs/specs/tools/list_alarm_notify.md                          ← 已生成
docs/tasks/T21-list-alarm-notify.md                            ← 本任务卡
docs/tasks/README.md                                           ← 修改: 加 T21 行

agentic-adapter/agentic-adapter-apm/
  src/main/java/com/huawei/smartom/agentic/adapter/apm/
    ApmAlarmAdapter.java                                       ← 修改: 加 listAlarmNotify 方法签名
    ApmAlarmAdapterImpl.java                                   ← 修改: 实现 listAlarmNotify
    dto/
      ApmAlarmNotification.java                                ← 新增: 8 字段
      ApmAlarmNotifyRequest.java                               ← 新增
      ApmAlarmNotifyResponse.java                              ← 新增
  src/test/java/com/huawei/smartom/agentic/adapter/apm/
    ApmAlarmAdapterImplTest.java                               ← 修改: 加 listAlarmNotify 用例
    contract/ApmListAlarmNotifyContractTest.java               ← 新增
  src/test/resources/sdk-samples/apm/
    list-alarm-notify-response.json                            ← 新增

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/apm/
    ApmAlarmService.java                                       ← 修改: 加 listAlarmNotify 方法
  src/test/java/com/huawei/smartom/agentic/monitoring/apm/
    ApmAlarmServiceTest.java                                   ← 修改: 加 listAlarmNotify 用例

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/ApmAlarmNotifyTool.java                               ← 新增
    config/McpServerConfig.java                                ← 修改: 注册 ApmAlarmNotifyTool
  src/test/java/com/huawei/smartom/agentic/mcp/
    tool/ApmAlarmNotifyToolTest.java                           ← 新增
```

## 关键技术要求

### 1. Adapter 扩展（T20 骨架已就绪）

`ApmAlarmAdapter` 接口加方法：

```java
ApmAlarmNotifyResponse listAlarmNotify(ApmAlarmNotifyRequest request);
```

实现：

```java
@Override
public ApmAlarmNotifyResponse listAlarmNotify(ApmAlarmNotifyRequest request) {
    Long businessId = request.businessId() == null
            ? properties.getApmBusinessId()
            : request.businessId();
    AlarmNotifyListRequest body = new AlarmNotifyListRequest()
            .withPage(request.page())
            .withPageSize(request.pageSize())
            .withAlarmDataId(request.alarmDataId())
            .withRegion(request.region());
    ListAlarmNotifyRequest sdkReq = new ListAlarmNotifyRequest().withBody(body);
    if (businessId != null) {
        sdkReq.setXBusinessId(businessId);
    }
    ListAlarmNotifyResponse sdkResp = invocation.execute(
            "apm-readonly", "huaweicloud-retryable", "apm.listAlarmNotify",
            () -> apmClient.listAlarmNotify(sdkReq));
    return toNotifyResponseDto(sdkResp);
}
```

### 2. DTO 无损（8 字段）

`ApmAlarmNotification`：

```
id (Long), gmtCreate (String), notifyType (String),
alarmRuleId (Long), templateId (Long),
alarmDataEventId (Long), notifyStatus (Boolean), alarmContent (String)
```

### 3. Service 校验

```java
public ApmAlarmNotifyResponse listAlarmNotify(ApmAlarmNotifyRequest r) {
    Validations.requireNonNull(r, "request");
    if (r.alarmDataId() == null) {
        throw new InvalidParamException("alarm_data_id is required");
    }
    if (r.alarmDataId() <= 0) {
        throw new InvalidParamException("alarm_data_id must be > 0");
    }
    Long effective = r.businessId() != null ? r.businessId() : properties.getApmBusinessId();
    if (effective == null) {
        throw new InvalidParamException("business_id is required");
    }
    if (r.page() != null && r.page() < 1) {
        throw new InvalidParamException("page must be >= 1");
    }
    if (r.pageSize() != null && (r.pageSize() < 1 || r.pageSize() > 100)) {
        throw new InvalidParamException("page_size must be in [1, 100]");
    }
    return adapter.listAlarmNotify(r);
}
```

### 4. 契约测试

参照 `ApmShowSpanSearchContractTest` + T20 的 `ApmListAlarmDataContractTest`：
- mock client 返回反序列化样本
- 调用 `adapter.listAlarmNotify(request)`
- 逐字段断言 8 个 DTO 字段

## 验收标准

- [ ] `mvn -pl agentic-adapter-apm,agentic-monitoring,agentic-mcp -am test` 全绿
- [ ] spec 状态翻 Approved
- [ ] T21 状态 Done，README.md 加 T21 行
- [ ] MCP Inspector 看到 `list_alarm_notify`
- [ ] Checkstyle 0
- [ ] DTO 无损（8 字段），契约测试通过
- [ ] **不破坏 T20 的 listAlarmData 路径**

## AI 易错点提醒

1. **类型不一致**：SDK 中 `alarm_data_id` 是 `Integer`，但 `AlarmDataVO.id` 是 `Long`。
   Agent 把 T20 响应的 `id`（Long）传过来时如果超 `Integer.MAX_VALUE` 会 NPE 或截断。
   DTO `alarmDataId` 保持 `Integer`（对齐 SDK），spec 注明限制；**不在 adapter 里
   做 long→int 安全转换之外的修正**。
2. **复用 T20 的 `ApmAlarmAdapter`/`ApmAlarmService`**——加方法，不新建类。
3. **Tool 名是 `list_alarm_notify`（无 apm_ 前缀）**——用户决策，与 SDK 方法名 `ListAlarmNotify` 直接对应；不要写成 `list_apm_alarm_notify`。
4. **`notify_status` 是 Boolean** —— `true` 送达，`false` 失败。DTO 字段类型 `Boolean`（可空，因为上游可能未返回）。
5. **`alarm_content` 是 String** —— 不要尝试再二次解析成 JSON。
6. **`AlarmNotifyListRequest` (body) 只有 4 字段**（page / pageSize / alarmDataId / region），比 `AlarmDataListRequest` 简单很多——不要错引或 mix 字段。

## 完成后

PR 标题：`feat(T21): list_alarm_notify MCP tool on the T20 alarm adapter`。PR 描述附上：
- spec 链接
- 测试用例对应表
- MCP 工具总数变化（17 → 18，跟随 T20）
- 遗留项：冒烟脚本 / Micrometer 看板
