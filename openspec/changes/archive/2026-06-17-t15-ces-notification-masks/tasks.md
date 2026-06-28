> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T15-ces-notification-masks.md`，状态 Done，commit `7bf5907`）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. CES v2 Client（agentic-adapter-ces）

- [x] 1.1 新增 `CesV2ClientConfig`，提供 `cesV2Client` bean（显式 import `com.huaweicloud.sdk.ces.v2.CesClient`），与 v1 `cesClient` 共存
- [x] 1.2 `CesNotificationMaskAdapterImpl` 构造方法注入 `cesV2Client`（禁用字段注入）

## 2. Adapter 层（agentic-adapter-ces）

- [x] 2.1 新增 `CesNotificationMaskAdapter` 接口 + `CesNotificationMaskAdapterImpl`（封装 create / delete / list 三个 v2 API）
- [x] 2.2 新增 10 个 DTO record：`CesCreateNotificationMaskRequest` / `Response`、`CesDeleteNotificationMasksRequest` / `Response`、`CesListNotificationMasksRequest`（含 offset/limit 默认值） / `Response`、`CesNotificationMask`、`NotificationMaskDimension`、`NotificationMaskProductMetric`、`NotificationMaskResource`
- [x] 2.3 SDK 枚举 `.getValue()` 转 String 入 DTO；`count` 用包装类型；delete 响应 null 列表兜底空 List
- [x] 2.4 list adapter 用 `bodyHasValue` 标志位决定是否 `setBody`
- [x] 2.5 SDK 异常经 `HuaweiCloudInvocation` → `SmartomException`（429/401/403/5xx/Timeout 映射）

## 3. Service 层（agentic-monitoring）

- [x] 3.1 新增 `CesNotificationMaskService`，三个方法统一编排（参数校验 + 委托 adapter）
- [x] 3.2 create 校验：`mask_name` 正则、枚举集、条件必填、`LocalDate.parse`、时间正则
- [x] 3.3 delete 校验：`notification_mask_ids` 非空、[1,100]、每项非空白
- [x] 3.4 list 校验：`offset` [0,10000]、`limit` [1,100]、各枚举集
- [x] 3.5 `ALLOWED_RELATION_TYPES`（create，含 `EVENT.SYS`）与 `ALLOWED_LIST_RELATION_TYPES`（list，含 `DEFAULT`）两个常量集

## 4. MCP 工具层（agentic-mcp）

- [x] 4.1 新增 `CesCreateNotificationMaskTool`，`@Tool(name="create_notification_mask")`
- [x] 4.2 新增 `CesDeleteNotificationMasksTool`，`@Tool(name="delete_notification_masks")`（destructiveHint 语义靠 description）
- [x] 4.3 新增 `CesListNotificationMasksTool`，`@Tool(name="list_notification_masks")`
- [x] 4.4 三个 tool catch `SmartomException` 转 `ErrorResponse`
- [x] 4.5 `McpServerConfig` 注册三个新 tool 到 `ToolCallbackProvider`

## 5. 配置（agentic-mcp）

- [x] 5.1 `application.yml` 新增 `ces-write` RateLimiter（5 QPS）；create / delete 走 `ces-write`，list 复用 `ces-readonly`（10 QPS）

## 6. 验收

- [x] 6.1 三个 tool 在 MCP Inspector 可见，description 正确；`CesV2Client initialized` 启动日志可见
- [x] 6.2 Checkstyle 0 violations；`mvn clean install` 全模块绿（99 tests pass，13 tools registered）
- [x] 6.3 代码已合入 master（`7bf5907`）

## 7. 遗留项（本期未交付）

- [ ] 7.1 Service / Adapter / Tool 层 UT
- [ ] 7.2 Contract Test
- [ ] 7.3 贵阳冒烟脚本
- [ ] 7.4 AOM / APM 对应屏蔽能力
- [ ] 7.5 Micrometer 指标看板
- [ ] 7.6 README 使用示例
- [ ] 7.7 客户端去重 / 同名屏蔽预检
