## ADDED Requirements

### Requirement: 跨组件事故关联编排

系统 SHALL 提供只读编排工具 `correlate_incident`，在单次调用内接收一个时间窗 `(startTimeMillis, endTimeMillis)`（UTC 毫秒）与一组可选过滤条件（`cesNamespace` / `logCategory` / `logKeyword` / `apmTraceId` / `apmSource`），并发扇出四个分支 `ces_alarms` / `aom_logs` / `apm_traces` / `apm_topology`，组合下游 `CesAlarmService` / `AomLogService` / `ApmTraceService` 能力。该工具 MUST 为只读（`readOnlyHint=true`、`destructiveHint=false`、`idempotentHint=true`），不直连华为云 SDK，不做跨组件语义关联 / 因果推断，不做分页。

#### Scenario: 四分支并发返回
- **WHEN** Agent 传入合法时间窗与可触发全部分支的过滤条件
- **THEN** 系统 SHALL 并发执行四个分支并返回 `CorrelateIncidentResponse`
- **AND** 分支顺序固定为 `cesAlarms` → `aomLogs` → `apmTraces` → `apmTopology`

#### Scenario: 整体响应不因部分分支失败 / 跳过而失败
- **WHEN** 任一分支返回 `failure` 或 `skipped`
- **THEN** 整体响应仍 SHALL 为成功
- **AND** 由 Agent 自行判断每个分支状态

### Requirement: 时间窗输入校验

系统 SHALL 在 service 层校验时间窗：`request` 为 null、`startTimeMillis` 或 `endTimeMillis` 任一为 null、`endTimeMillis <= startTimeMillis` 时返回 `INVALID_PARAM`（整体失败，走标准 `ErrorResponse`，`retryable=false`），且 MUST NOT 创建 executor 或访问任何下游。

#### Scenario: request 为 null
- **WHEN** 调用 `request == null`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 时间戳缺失
- **WHEN** `startTimeMillis` 或 `endTimeMillis` 任一为 null
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

#### Scenario: 时间窗终点不大于起点
- **WHEN** `endTimeMillis <= startTimeMillis`
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`
- **AND** MUST NOT 创建 executor 或发起任何下游调用

### Requirement: 分支三态与跳过策略

系统 SHALL 用 `CorrelateBranchResult` 以 `skipped` / `data` / `error` 三个互斥字段表达每个分支状态。跳过判断 MUST 在编排层（各分支内部）完成，不下沉到下游 service：`logCategory` 为空时 `aom_logs` skipped；`apmTraceId` 与 `apmSource` 均为空时 `apm_traces` skipped；`apmTraceId` 为空时 `apm_topology` skipped。`ces_alarms` 分支 MUST NOT 因 `cesNamespace` 为空而跳过（空 namespace 表示不加过滤）。

#### Scenario: logCategory 缺失跳过 aom_logs
- **WHEN** `logCategory` 为空
- **THEN** `aom_logs` 分支 SHALL 返回 `skipped=true`，未访问上游

#### Scenario: APM 入参均缺失跳过 apm 分支
- **WHEN** `apmTraceId` 与 `apmSource` 均为空
- **THEN** `apm_traces` 分支 SHALL 返回 `skipped=true`
- **AND** `apm_topology` 分支 SHALL 返回 `skipped=true`

#### Scenario: 仅给 traceId 时 apm 两分支均执行
- **GIVEN** `apmTraceId` 给定但 `apmSource` 为空
- **WHEN** 调用工具
- **THEN** `apm_traces` 分支 SHALL 执行（不跳过）
- **AND** `apm_topology` 分支 SHALL 执行（不跳过）

#### Scenario: cesNamespace 为空不跳过
- **WHEN** `cesNamespace` 为空
- **THEN** `ces_alarms` 分支 SHALL 仍执行，仅不加 namespace 过滤

### Requirement: 分支独立的异常隔离

系统 SHALL 用虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()`，try-with-resources 释放）承载四个分支，单分支 `SmartomException` MUST 被捕获并转为 `CorrelateError`，且 MUST NOT 阻塞其他分支。系统 MUST NOT catch 通用 `Exception`：非 `SmartomException` 的 RuntimeException 经 `CompletableFuture` 包装为 `ExecutionException`，由聚合阶段兜底转 `INTERNAL` failure。`CorrelateError` MUST 携带 `errorCode` / `retryable` / `message` / `upstreamTraceId`（可空），并在分支失败时打 WARN 日志（含 `errorCode` + `upstreamTraceId`）。

#### Scenario: 单分支 SmartomException 不影响其他分支
- **WHEN** CES 下游抛 `UpstreamException(5xx)`
- **THEN** `ces_alarms` 分支 SHALL 返回 `failure`，`error.errorCode=UPSTREAM_ERROR`，`retryable=true`
- **AND** 其余分支 SHALL 不受影响

#### Scenario: 限流映射
- **WHEN** 某分支下游抛 `UpstreamException(429)`
- **THEN** 该分支 SHALL 返回 `failure`，`error.errorCode=UPSTREAM_THROTTLED`，`retryable=true`

#### Scenario: 鉴权失败映射
- **WHEN** 某分支下游抛 `UpstreamException(401/403)`
- **THEN** 该分支 SHALL 返回 `failure`，`error.errorCode=UPSTREAM_AUTH_FAILED`，`retryable=false`

#### Scenario: 非业务异常兜底为 INTERNAL
- **WHEN** 某分支抛出非 `SmartomException` 的 RuntimeException
- **THEN** 该异常 SHALL 经 `ExecutionException` 兜底转 `failure`，`error.errorCode=INTERNAL`，`retryable=false`

### Requirement: 下游 service 入参装配与时间格式

系统 SHALL 按各下游 service 契约装配入参，且 MUST 正确处理四分支不一致的时间参数格式：`ces_alarms` 分支 MUST 用 `String.valueOf(millis)` 将时间窗转为字符串传给 `CesListAlarmsRequest`（offset=0 / limit=100）；`aom_logs` 分支保持 UTC 毫秒传给 `AomQueryLogsRequest`（pageSize=100 / isDesc=true）；`apm_traces`（page=1 / pageSize=50）与 `apm_topology` 分支按 `apmTraceId` / `apmSource` 检索，不消费时间窗。系统 MUST NOT 编造下游 service 未提供的入参。

#### Scenario: CES 时间字段转字符串
- **WHEN** 装配 `CesListAlarmsRequest`
- **THEN** `startTimeMillis` / `endTimeMillis` SHALL 经 `String.valueOf(...)` 转为字符串传入

#### Scenario: AOM 时间窗保持毫秒
- **WHEN** 装配 `AomQueryLogsRequest`
- **THEN** 时间窗 SHALL 以 UTC 毫秒（long）原样传入，不转字符串

#### Scenario: APM 分支不传时间窗
- **WHEN** 装配 `ApmQueryTracesRequest` / `ApmGetTopologyRequest`
- **THEN** 系统 SHALL 仅按 `apmTraceId` / `apmSource` 装配，MUST NOT 编造时间窗等下游未声明的入参
