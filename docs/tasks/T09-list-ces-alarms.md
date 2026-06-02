# T09 — 实现 list_alarms（CES 告警历史查询）

> 状态: **Done**（提交 `4c346d6`，2026-06-02 回填文档） · 估时: 0.5d · 依赖: T04（CES adapter 基座）· 关联 spec: `docs/specs/tools/list_alarms.md`

## 目标

在 CES adapter 上新增 `ListAlarmHistories` 能力，提供 MCP tool `list_alarms` 让 Agent 查询华为云 CES 告警历史。覆盖 adapter / service / tool 三层，错误码统一映射，与现有 `ces-readonly` 限流配额复用。

## 范围

**做**:
- `CesMetricsAdapter` 接口新增 `listAlarms`，`CesMetricsAdapterImpl` 实现 `ces.listAlarmHistories` SDK 包装
- 新增 DTO：`CesListAlarmsRequest` / `CesListAlarmsResponse` / `CesAlarmHistory`
- 新增 `CesAlarmService`：偏移分页校验 + status / level / namespace 枚举与正则校验
- MCP tool `CesAlarmTool`，`@Tool(name = "list_alarms")` 注册
- `McpServerConfig` 注入 `CesAlarmTool` 到 `ToolCallbackProvider`

**不做**（防止任务蔓延）:
- ❌ Tool / Service / Adapter 层 UT、Contract Test、冒烟脚本（spec §6 列出，本期未交付，进遗留项）
- ❌ 告警规则定义查询（`ListAlarms` 接口）
- ❌ 告警 ACK / 静默 / 恢复（写操作）

## 前置阅读

**必读**:
1. `docs/specs/tools/list_alarms.md` — 完整 spec
2. `CLAUDE.md` §3 / §4 — 编码规范、SDK 不泄漏、错误码统一
3. CES API：https://support.huaweicloud.com/intl/en-us/api-ces/

## 实际产物清单

```
docs/specs/tools/
  list_alarms.md                                      ← 本任务回填（v1.0）
docs/tasks/
  T09-list-ces-alarms.md                              ← 本任务卡

agentic-adapter/agentic-adapter-ces/
  src/main/java/com/huawei/smartom/agentic/adapter/ces/
    CesMetricsAdapter.java                            ← 加 listAlarms 方法
    CesMetricsAdapterImpl.java                        ← 加实现 + 私有 toListAlarmHistoriesSdkRequest / toListAlarmsResponseDto
    dto/
      CesListAlarmsRequest.java                       ← 新增 record
      CesListAlarmsResponse.java                      ← 新增 record
      CesAlarmHistory.java                            ← 新增 record

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/ces/
    CesAlarmService.java                              ← 新增（参数校验 + 委托 adapter）

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/CesAlarmTool.java                            ← 新增（@Tool 注册）
    config/McpServerConfig.java                       ← 注入 CesAlarmTool
```

## 关键技术要求

### 1. DTO 设计

`CesListAlarmsRequest` 紧凑构造仅做默认值规范化（`limit=100`、`start=0`），**业务校验放 Service 层**，避免抛 `IllegalArgumentException` 绕过 `ErrorCode` 映射。

`CesAlarmHistory` 字段子集来自 `AlarmHistoryInfoResp` 文档常用项；嵌套的 `metric` 在 adapter 层拍平为 `namespace` + `metricName`，避免泄漏 SDK 嵌套结构。

### 2. Service 层校验

- `limit ∈ [1, 100]`（CES 上限 100）
- `start >= 0`
- `alarmStatus` ∈ `{ok, alarm, insufficient_data, invalid}`（**小写**，与 SDK 枚举一致）
- `alarmLevel` ∈ `{1, 2, 3, 4}`
- `namespace` 匹配 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`

### 3. Adapter SDK 映射要点

```java
new ListAlarmHistoriesRequest()
    .withLimit(String.valueOf(request.limit()))     // SDK 收字符串
    .withStart(String.valueOf(request.start()));    // SDK 收字符串
sdk.setAlarmStatus(ListAlarmHistoriesRequest.AlarmStatusEnum.fromValue(request.alarmStatus()));
sdk.setAlarmLevel(ListAlarmHistoriesRequest.AlarmLevelEnum.fromValue(request.alarmLevel()));
```

`AlarmLevelEnum.fromValue(Integer)` 接 `Integer`；`AlarmStatusEnum.fromValue(String)` 接小写字符串。

### 4. Tool 层错误处理

复用现有模式：`try { service.listAlarms(req); } catch (SmartomException e) { LOG.warn(...); return ErrorResponse.of(...); }`，确保 MCP 客户端拿到结构化错误而非原始异常栈。

## 验收标准

实际完成项（spec §7 mapping）：

- [x] MCP Inspector 能看到 `list_alarms`，description 正确
- [x] 复用 `ces-readonly` RateLimiter（adapter `RATE_LIMITER_NAME="ces-readonly"`）
- [x] 日志含入参摘要（`ces.listAlarmHistories start` INFO）
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（`4c346d6`）
- [ ] Tool / Service / Adapter UT（后续任务补）
- [ ] Contract Test（后续任务补）
- [ ] 贵阳冒烟脚本（后续任务补）
- [ ] Micrometer 指标 + README 示例（后续任务补）

## AI 易错点提醒

**spec §4 已列出**：
1. SDK `limit` / `start` / `from` / `to` 全是字符串，要 `String.valueOf(...)`
2. `AlarmStatusEnum` 枚举是小写下划线（`ok` / `alarm` / `insufficient_data` / `invalid`）
3. `AlarmLevelEnum.fromValue` 接 `Integer`
4. 告警关联的 namespace / metricName 嵌套在 `MetricInfoResp` 下
5. `MetaDataForAlarmHistoryResp` 没有 marker，仅 `total`——偏移分页

**额外**：
6. **Tool 名是 `list_alarms` 而不是 `list_ces_alarms`**——`@Tool(name=...)` 是真实契约名，spec / 任务卡里若写错，Agent 拼装 prompt 会失败
7. **CES 该接口同时支持 marker 和 offset，但 SDK 暴露的是 offset 风格**（`start` 是数字字符串）——不要按 `list_ces_metrics` 的 marker 习惯做
8. **响应里 `metric` 字段可能为 null**（部分告警类型无关联指标），adapter 必须三元判空

## 完成后

PR：`feat(T09): add list_alarms CES alarm history tool`（已提交：`4c346d6`）。
