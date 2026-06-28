> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T09-list-ces-alarms.md`，状态 Done，提交 `4c346d6`）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-ces）

- [x] 1.1 `CesMetricsAdapter` 接口新增 `listAlarms` 方法
- [x] 1.2 `CesMetricsAdapterImpl` 实现 `ces.listAlarmHistories` SDK 包装（私有 `toListAlarmHistoriesSdkRequest` / `toListAlarmsResponseDto`），复用 `ces-readonly` 限流
- [x] 1.3 新增 DTO record：`CesListAlarmsRequest`（紧凑构造仅做默认值规范化 limit=100/start=0）/ `CesListAlarmsResponse` / `CesAlarmHistory`
- [x] 1.4 嵌套 `MetricInfoResp` 拍平为 `namespace` + `metricName`，三元判空处理 `metric` 为 null
- [x] 1.5 SDK 异常 → `ErrorCode` 映射（429/401-403/5xx/Timeout）

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `CesAlarmService.listAlarms`，委托 adapter
- [x] 2.2 参数校验：limit∈[1,100] / start≥0 / alarmStatus 枚举（小写）/ alarmLevel∈{1,2,3,4} / namespace 正则
- [x] 2.3 业务异常 `InvalidParamException` → `INVALID_PARAM`

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `CesAlarmTool`，`@Tool(name = "list_alarms")` 注册
- [x] 3.2 Tool 层 `catch (SmartomException)` 返回结构化 `ErrorResponse`
- [x] 3.3 `McpServerConfig` 注入 `CesAlarmTool` 到 `ToolCallbackProvider`

## 4. 验收

- [x] 4.1 MCP Inspector 能看到 `list_alarms`，description 正确
- [x] 4.2 复用 `ces-readonly` RateLimiter（adapter `RATE_LIMITER_NAME="ces-readonly"`）
- [x] 4.3 日志含入参摘要（`ces.listAlarmHistories start` INFO）
- [x] 4.4 Checkstyle 0 violations
- [x] 4.5 代码已合入 master（`4c346d6`）

## 5. 遗留项（本期未交付）

- [ ] 5.1 Service 层 UT（`CesAlarmServiceTest`：limit 越界 / status 大小写 / level 越界 / namespace 正则 / 默认值透传）
- [ ] 5.2 Adapter 层 UT（`CesMetricsAdapterImplTest` 新增：全字段对齐 / 嵌套 metric 拍平 / SDK 429 重试）
- [ ] 5.3 Contract Test（`ListAlarmHistoriesRequest` / `AlarmHistoryInfoResp` / `MetaDataForAlarmHistoryResp` 字段反射 + 样例 JSON 反序列化）
- [ ] 5.4 贵阳冒烟脚本 `scripts/smoke/smoke-list_alarms.sh`（正常拉取 / 大写 status 400 / limit=101 400）
- [ ] 5.5 Micrometer 指标在 actuator/prometheus 看到
- [ ] 5.6 README 含 tool 使用示例
