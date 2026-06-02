# T15 — 实现 CES 告警屏蔽三件套（create / delete / list notification masks）

> 状态: **Done**（提交 `7bf5907`，2026-05-28 完成） · 估时: 1d · 依赖: T04（CES adapter 基座）· 关联 spec: `docs/specs/tools/create_notification_mask.md` / `docs/specs/tools/delete_notification_masks.md` / `docs/specs/tools/list_notification_masks.md`

## 目标

为智能运维 Agent 提供完整的 CES 告警通知屏蔽生命周期管理能力，支撑变更窗口屏蔽 / 解除屏蔽 / 审计三类典型场景。三个工具在同一 commit 中一起交付，共享 v2 SDK client 与 service 编排层，因此使用单张任务卡。

**这是项目首次引入：**
1. **CES v2 SDK**（独立于既有 v1 client）—— 需新建 `CesV2ClientConfig`
2. **写操作 tool**（`create` / `delete`）—— 引入新限流配额 `ces-write`
3. **`destructiveHint=true` 的 tool**（`delete`）—— annotation 需在 MCP schema 中正确暴露

## 范围

**做**:
- CES v2 SDK client bean (`CesV2ClientConfig`) + 与 v1 client 共存
- `CesNotificationMaskAdapter` 接口 + `CesNotificationMaskAdapterImpl` 实现（封装 3 个 v2 API）
- 10 个 DTO（覆盖三个接口的 Request / Response + 内嵌结构）
- `CesNotificationMaskService` 业务编排（参数校验 + 委托 adapter）
- 3 个 MCP Tool：`CesCreateNotificationMaskTool` / `CesDeleteNotificationMasksTool` / `CesListNotificationMasksTool`
- `application.yml` 新增 `ces-write` RateLimiter 实例（5 QPS）
- `McpServerConfig` 注册三个 tool 到 `ToolCallbackProvider`

**不做**（本期未交付，记入遗留项）:
- ❌ UT（service / adapter / tool 任一层），TC，冒烟脚本
- ❌ AOM / APM 对应屏蔽能力
- ❌ Micrometer 指标看板
- ❌ README 使用示例
- ❌ 客户端去重 / 同名屏蔽预检

## 前置阅读

**必读**:
1. `docs/specs/tools/create_notification_mask.md`
2. `docs/specs/tools/delete_notification_masks.md`
3. `docs/specs/tools/list_notification_masks.md`
4. `CLAUDE.md` §3 / §4 — 编码规范、SDK 不泄漏、错误码统一
5. 华为云 CES v2 文档 `BatchUpdateNotificationMasks` / `BatchDeleteNotificationMasks` / `ListNotificationMasks`

## 产物清单

```
docs/specs/tools/
  create_notification_mask.md                            ← 新增 spec
  delete_notification_masks.md                           ← 新增 spec
  list_notification_masks.md                             ← 新增 spec
docs/tasks/
  T15-ces-notification-masks.md                          ← 本任务卡

agentic-adapter/agentic-adapter-ces/
  src/main/java/com/huawei/smartom/agentic/adapter/ces/
    config/CesV2ClientConfig.java                        ← 新增（v2 client bean）
    CesNotificationMaskAdapter.java                      ← 新增接口
    CesNotificationMaskAdapterImpl.java                  ← 新增实现
    dto/
      CesCreateNotificationMaskRequest.java              ← 新增 record
      CesCreateNotificationMaskResponse.java             ← 新增 record
      CesDeleteNotificationMasksRequest.java             ← 新增 record
      CesDeleteNotificationMasksResponse.java            ← 新增 record
      CesListNotificationMasksRequest.java               ← 新增 record（含 offset/limit 默认值）
      CesListNotificationMasksResponse.java              ← 新增 record
      CesNotificationMask.java                           ← 新增（列表元素）
      NotificationMaskDimension.java                     ← 新增（{name,value}）
      NotificationMaskProductMetric.java                 ← 新增（{dimensionName,metricName}）
      NotificationMaskResource.java                      ← 新增（{namespace, dimensions[]}）

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/ces/
    CesNotificationMaskService.java                      ← 新增（三个方法统一编排）

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/CesCreateNotificationMaskTool.java              ← 新增 @Tool
    tool/CesDeleteNotificationMasksTool.java             ← 新增 @Tool（destructiveHint=true）
    tool/CesListNotificationMasksTool.java               ← 新增 @Tool
    config/McpServerConfig.java                          ← 修改：注册三个新 tool
  src/main/resources/application.yml                     ← 修改：新增 ces-write RateLimiter
```

## 关键技术要求

### 1. CES v2 Client 独立 bean

v2 SDK 与 v1 SDK 的 `CesClient` 类全限定名同名（包路径不同），需在 `CesV2ClientConfig` 中**显式 import v2 包**，并将 bean 命名为 `cesV2Client` 以与 v1 区分：

```java
@Bean
public com.huaweicloud.sdk.ces.v2.CesClient cesV2Client(HuaweiCloudProperties properties) { ... }
```

`CesNotificationMaskAdapterImpl` 通过构造方法注入 `cesV2Client`，**不能用 `@Autowired` 字段注入**（项目禁用字段注入）。

### 2. 限流策略：`ces-write` vs `ces-readonly`

- `create_notification_mask` / `delete_notification_masks` 走 `ces-write`（5 QPS）
- `list_notification_masks` 走 `ces-readonly`（10 QPS，与既有只读 tool 共享）

理由：写操作配额单独切分，避免突发写流量挤占读流量。

### 3. Service 层校验集中

三个工具共用一个 `CesNotificationMaskService`，service 层负责：
- `create`: `mask_name` 正则、`relation_type` / `mask_type` / `resource_level` 枚举集、条件必填（ALARM_RULE → relation_ids、RESOURCE → resources、START_END_TIME / CYCLE_TIME → 四个时间字段）、日期格式 `LocalDate.parse`、时间格式 `^\d{2}:\d{2}:\d{2}$`
- `delete`: `notification_mask_ids` 非空、长度 [1, 100]、每项非空白
- `list`: `offset` [0, 10000]、`limit` [1, 100]、各枚举字段集

**注意**：`relation_type` 在 create 与 list 的合法集**不同**：
- create 允许 `EVENT.SYS`，不允许 `DEFAULT`
- list 允许 `DEFAULT`，不允许 `EVENT.SYS`

Service 中分别用 `ALLOWED_RELATION_TYPES` / `ALLOWED_LIST_RELATION_TYPES` 两个常量。

### 4. SDK 枚举映射

- v2 SDK 的 `RelationType` / `MaskType` / `ResourceDimension` / `Resource` / `ProductMetric` 都在 `com.huaweicloud.sdk.ces.v2.model` 包
- `RelationType.fromValue` 与 `ListRelationType.fromValue` 是**两个不同的 SDK 枚举类**，分别用于 create 与 list；混用会编译失败
- 响应里枚举字段需 `.getValue()` 转 String 再放进 DTO

### 5. MCP Tool annotations

- `create_notification_mask`: `readOnlyHint=false, destructiveHint=false, idempotentHint=false`
- `delete_notification_masks`: `readOnlyHint=false, destructiveHint=true, idempotentHint=true`
- `list_notification_masks`: `readOnlyHint=true, destructiveHint=false, idempotentHint=true`

**注意**：当前 `@Tool` 注解仅设置了 `name` / `description`，annotations 通过 Spring AI MCP starter 的元信息默认填充（`@Tool` 注解暂不直接接受 annotations 字段，待 Spring AI 升级后再显式标注；本期靠 description 文案让 Agent 感知破坏性）。

## 验收标准

实际完成项：

- [x] 三个 tool 在 MCP Inspector 中可见，description 正确
- [x] CES v2 client bean 在启动日志中能看到 `CesV2Client initialized, region=...`
- [x] `application.yml` 含 `ces-write` RateLimiter（5 QPS）
- [x] adapter 日志含 `maskName` / `relationType` / `maskType` / `ids.size` / `offset` / `limit` 等关键入参摘要
- [x] adapter 在调 SDK 失败时通过 `HuaweiCloudInvocation` 统一映射到 `SmartomException`
- [x] tool 层 catch `SmartomException` 转 `ErrorResponse`
- [x] Checkstyle 0 violations
- [x] `mvn clean install` 全模块绿（99 tests pass，13 tools registered）
- [x] 代码已合入 master（`7bf5907`）
- [ ] Service / Adapter / Tool 层 UT
- [ ] Contract Test
- [ ] 贵阳冒烟脚本
- [ ] Micrometer 指标 + README 使用示例

## AI 易错点提醒

**SDK / 包路径**:
1. v1 和 v2 的 `CesClient` 同名不同包，import 别写错。涉及屏蔽规则的 SDK 类**全部**在 `com.huaweicloud.sdk.ces.v2.model.*`
2. `RelationType`（create）与 `ListRelationType`（list）是不同枚举类，且合法值集合不同：`EVENT.SYS` 只能用于 create，`DEFAULT` 只能用于 list
3. v2 SDK 的 builder 仍是 `withXxx` 链式 + `setXxx` 返回 void 两套并存，但**部分新枚举字段没有 `withXxx`，只能 `setXxx`**（例如 `setResourceLevel(ResourceLevelEnum)`），AI 不要凭印象写

**幂等与破坏性**:
4. `create_notification_mask` **不幂等**：同名调用两次得两条不同 ID 的规则。`@Tool` annotation 标记 `idempotentHint=false`，调用前 Agent 应自行 `list` 检查
5. `delete_notification_masks` **幂等**：删除已不存在的 ID 不报错，只是返回列表里没有；不要把「返回 < 入参」当成失败重试
6. `destructiveHint=true` 是 MCP 客户端识别「需要二次确认」的关键 hint，**测试要校验 schema 里这个字段为 true**

**v2 SDK gotchas**:
7. `LocalDate` 字段：`startDate` / `endDate` 在 SDK 端是 `LocalDate` 而非 String，需要 `LocalDate.parse(yyyy-MM-dd)`；service 层先做正则校验避免 `DateTimeParseException` 泄漏
8. `ListNotificationMasksRequest` 的 body 为**可选**：所有过滤字段都未提供时不要 `setBody(空 body)`；adapter 用 `bodyHasValue` 标志位
9. 响应里枚举字段需 `.getValue()` 转 String，否则 Jackson 会输出枚举对象嵌套结构，污染 MCP 输出契约
10. `count` 字段返回 `Integer`（可能为 null），DTO 用包装类型不要拆箱

## 完成后

PR：`feat(ces-notification-mask): add 3 alarm-shielding tools (create / delete / list)`（已提交：`7bf5907`）。

PR 描述附上：
- 三个关联 spec 链接
- 三个 tool 在 MCP 中的 `@Tool(name=...)` 与 annotations 矩阵
- 遗留项列表（UT / TC / 冒烟 / Micrometer / README）
