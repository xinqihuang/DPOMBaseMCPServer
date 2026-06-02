# T12 — 实现 correlate_incident 跨组件事故关联编排

> 状态: **Done**（提交 `4c346d6`，2026-05-28 落地） · 估时: 1d · 依赖: T06（AOM adapter）/ T08-T10（CES alarms + AOM logs + APM traces 三个底座 service）· 关联 spec: `docs/specs/tools/correlate_incident.md`

## 目标

提供一个 MCP 编排型工具，使 Agent 在一次调用内并发拉取 CES 告警 / AOM 日志 / APM trace / APM 拓扑四个分支的数据，每个分支独立返回 `success` / `failure` / `skipped` 三态，避免 Agent 自己串行组合多个 tool。

## 范围

**做**:
- `CorrelateIncidentRequest` / `CorrelateIncidentResponse` / `CorrelateBranchResult` / `CorrelateError` 四个 DTO
- 业务层 `CorrelateIncidentService`：时间窗校验 + 用 `Executors.newVirtualThreadPerTaskExecutor()` 扇出 4 个 `CompletableFuture` + `allOf().join()` 聚合
- 跳过策略：`logCategory` 空 → aom_logs skipped；`apmTraceId && apmSource` 均空 → apm_traces skipped；`apmTraceId` 空 → apm_topology skipped
- 单分支 `SmartomException` catch → `CorrelateError`，**不阻塞其他分支**
- MCP tool 注册 `CorrelateIncidentTool` + `@Tool(name="correlate_incident")` description
- 注册到 `ToolCallbackProvider`

**不做**（防止任务蔓延，记入 spec §6 / §7 遗留）:
- ❌ Service 层 UT（编排 mock 矩阵：4 分支 × 3 状态 × 2 异常路径）
- ❌ Tool 层 UT（输入校验 + ErrorResponse 转换）
- ❌ 类型契约测试（本层不直连 SDK，无强 schema）
- ❌ 贵阳冒烟脚本
- ❌ Micrometer 自定义分支级指标（只有整体 tool 级）
- ❌ README 使用示例

## 前置阅读

**必读**:
1. `docs/specs/tools/correlate_incident.md` — 完整 spec
2. `CLAUDE.md` §4.4 — 虚拟线程并发模型
3. `CLAUDE.md` §3.4 — 异常 catch 规则（只 catch SmartomException，不 catch Exception）

**强烈推荐**:
4. `docs/specs/tools/query_logs.md` — AOM 日志分支调用的 service spec
5. `docs/specs/tools/list_ces_alarms.md` 等下游 service spec（如已存在）

## 实际产物清单

```
agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/correlate/
    CorrelateIncidentRequest.java                  ← record，7 字段（时间窗 + 5 过滤项）
    CorrelateIncidentResponse.java                 ← record，4 分支结果
    CorrelateBranchResult.java                     ← record，三态 (skipped/data/error)
                                                     + 静态工厂 ofSkipped/success/failure
    CorrelateError.java                            ← record，errorCode + retryable + message + upstreamTraceId
    CorrelateIncidentService.java                  ← 校验 + 虚拟线程扇出 + 分支独立 try/catch

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/tool/
    CorrelateIncidentTool.java                     ← @Tool 注册 + ErrorResponse 兜底
  src/main/java/com/huawei/smartom/agentic/mcp/config/
    McpServerConfig.java                           ← 注册 CorrelateIncidentTool 到 ToolCallbackProvider
```

未交付（遗留项，见验收清单）：测试代码 / 冒烟脚本 / README 示例。

## 关键技术要求

### 1. 虚拟线程扇出

按 CLAUDE.md §4.4，每次调用 try-with-resources 创建独立 executor：

```java
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    CompletableFuture<CorrelateBranchResult> alarmsFuture =
            CompletableFuture.supplyAsync(() -> runCesAlarms(request), executor);
    // ... logs / traces / topology
    CompletableFuture.allOf(alarmsFuture, ...).join();
    return new CorrelateIncidentResponse(
            waitFor(alarmsFuture, "ces_alarms"), ...);
}
```

### 2. 分支独立的 try/catch

`runBranch(label, supplier)` 用 SmartomException 单点 catch：

```java
private CorrelateBranchResult runBranch(String label, Supplier<Object> call) {
    try {
        return CorrelateBranchResult.success(call.get());
    }
    catch (SmartomException e) {
        LOG.warn("correlate_incident branch {} failed, errorCode={}, upstreamTraceId={}",
                label, e.getErrorCode(), e.getUpstreamTraceId());
        return CorrelateBranchResult.failure(new CorrelateError(
                e.getErrorCode(), e.getErrorCode().isRetryable(),
                e.getMessage(), e.getUpstreamTraceId()));
    }
}
```

**不要 catch Exception**，否则 RuntimeException 会被吃掉、违反 §3.4。RuntimeException 会通过 `CompletableFuture` 包装成 `ExecutionException`，由 `waitFor` 兜底转 `INTERNAL`。

### 3. 跳过策略

跳过逻辑在 `runAomLogs` / `runApmTraces` / `runApmTopology` 内部判断 → `CorrelateBranchResult.ofSkipped()`，**不放在 service 层**，因为 `cesAlarms` 分支不跳过（namespace 为空 = 不加过滤而非跳过）。

### 4. MCP tool description

提示 Agent："each branch is independent: a failing branch does not affect the others, and any branch whose required input is missing is reported as 'skipped' rather than failing the whole call." 这是让 Agent 正确解读分支三态的关键。

## 验收标准

实际完成（spec §7 mapping）:

- [x] `CorrelateIncidentTool` 注册成功，MCP Inspector 可见
- [x] `CorrelateIncidentService` 时间窗 4 项校验（null / null / `<=` / request null）
- [x] 4 分支并发扇出，单分支失败不阻塞其他分支
- [x] 虚拟线程符合 CLAUDE.md §4.4，try-with-resources 释放 executor
- [x] 分支级 WARN 日志含 errorCode + upstreamTraceId
- [x] 代码已合入 master（`4c346d6`）
- [x] Checkstyle 0 violations
- [ ] Service 层 UT-01~08（后续任务补 `CorrelateIncidentServiceTest`）
- [ ] Tool 层 UT（输入校验 + ErrorResponse 转换；后续补）
- [ ] 贵阳冒烟脚本 3 条（后续补）
- [ ] Micrometer `mcp_tool_invocation{tool="correlate_incident"}`（后续补）
- [ ] README 使用示例（后续补）

## AI 易错点提醒

**spec §4 已列出**：
1. CES 时间字段是字符串，要 `String.valueOf(millis)`
2. AOM / APM 跳过判断在 service 编排层，不下沉到下游
3. `allOf().join()` 不会抛业务异常，业务异常已在 runBranch 内被吞并转 CorrelateError

**编排层特有**：
4. **try-with-resources 关闭 executor**：JDK 21 虚拟线程 executor 实现了 `AutoCloseable`，离开作用域会 await 所有任务结束，**不需要手动 shutdown**
5. **Lambda 参数名 ≥ 2 字符**：Checkstyle `LambdaParameterName` 规则（与 T14 同款坑）
6. **不要 catch Exception**：参考 CLAUDE.md §3.4
7. **`CorrelateBranchResult.data` 是 `Object`**：Jackson 序列化时按运行期类型展开。下游 service 返回的是各自 DTO（`CesListAlarmsResponse` / `AomQueryLogsResponse` / `ApmQueryTracesResponse` / `ApmGetTopologyResponse`），字段名由 Jackson 默认策略决定
8. **大括号风格**：`}` 与 `catch`/`else` 必须换行（CLAUDE.md §3.1），整个文件 4 处 try/catch 都得遵守

## 完成后

PR：`feat(correlate): add correlate_incident cross-component orchestration tool`（已落入 `4c346d6`）。

PR 描述包含：
- 4 分支扇出示意图
- 三态 `skipped`/`success`/`failure` 决策表
- 遗留项清单（测试 / 冒烟 / README）
