# T13 — 实现 query_logs（AOM 日志查询）

> 状态: **Done**（提交 `4c346d6`，2026-05-28 落地） · 估时: 0.5d · 依赖: T06（AOM adapter 基座）· 关联 spec: `docs/specs/tools/query_logs.md`

## 目标

在已有的 `AomMetricsAdapterImpl` 上扩展 `queryLogs(...)` 能力，并配套业务层 service + MCP tool 注册，让 Agent 能按 `(category, time window, key_word)` 检索 AOM 应用 / 节点 / 自定义日志。

## 范围

**做**:
- `AomQueryLogsRequest` / `AomQueryLogsResponse` 两个 record DTO
- `AomMetricsAdapter` 接口扩展 `queryLogs(...)`；实现类 `AomMetricsAdapterImpl#queryLogs` 包装华为云 SDK `listLogItems` 调用，限流 `aom-readonly` + retry `huaweicloud-retryable`
- `AomLogService` 业务编排：5 项入参校验（category 白名单 / time 非空 / endTime > startTime / pageSize ∈ [1, 1000]）
- `AomLogTool` MCP 注册，`@Tool(name="query_logs")`
- 注册到 `ToolCallbackProvider`
- 响应 `result` 字段以 String 透传（不在 adapter 层解析）

**不做**（防止任务蔓延，记入 spec §6 / §7 遗留）:
- ❌ Service / Adapter / Tool 层 UT
- ❌ 类型契约测试
- ❌ 贵阳冒烟脚本
- ❌ Micrometer 指标
- ❌ README 使用示例
- ❌ 日志组 / 日志结构定义管理（写操作，不在 MVP 范围）
- ❌ 翻页 / lineNum 游标

## 前置阅读

**必读**:
1. `docs/specs/tools/query_logs.md` — 完整 spec
2. `CLAUDE.md` §3 / §4.1 — 编码规范 + SDK 不泄漏到 adapter 之外
3. `AomMetricsAdapterImpl` 现有 `listMetrics` / `queryMetricData` 方法 — 复用限流 + retry 模式

**强烈推荐**:
4. 华为云 AOM API `ListLogItems`（`type=querylogs`）文档

## 实际产物清单

```
agentic-adapter/agentic-adapter-aom/
  src/main/java/com/huawei/smartom/agentic/adapter/aom/
    AomMetricsAdapter.java                          ← 新增 queryLogs 方法签名
    AomMetricsAdapterImpl.java                      ← 新增 queryLogs + toListLogItemsSdkRequest 私有方法
    dto/
      AomQueryLogsRequest.java                      ← record，6 字段 + 紧凑构造默认值
      AomQueryLogsResponse.java                     ← record，3 字段（result / errorCode / errorMessage）

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/aom/
    AomLogService.java                              ← 业务校验 + 委托 adapter

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/tool/
    AomLogTool.java                                 ← @Tool(name="query_logs") + ErrorResponse 兜底
  src/main/java/com/huawei/smartom/agentic/mcp/config/
    McpServerConfig.java                            ← 注册 AomLogTool
```

未交付（遗留项，见验收清单）：测试代码 / 冒烟脚本 / README 示例。

## 关键技术要求

### 1. SDK 字段拼写陷阱

```java
QueryBodyParam body = new QueryBodyParam()
        .withCategory(request.category())
        .withStartTime(request.startTime())
        .withEndTime(request.endTime())
        .withPageSizeSize(String.valueOf(request.pageSize()))   // 注意双 Size
        .withIsDesc(request.isDesc());
```

- `withPageSizeSize` 字段名带双 `Size`，是上游 API 命名，**不是笔误**
- pageSize 走字符串：`String.valueOf(pageSize)`，不是 `Integer.toString` 也不是直接传 int
- `withType("querylogs")` 必传

### 2. result 字段透传

```java
return new AomQueryLogsResponse(
        sdkResponse.getResult(),         // 原始 JSON 字符串，不解析
        sdkResponse.getErrorCode(),
        sdkResponse.getErrorMessage());
```

不要在 adapter 层 `ObjectMapper.readTree(...)` — AOM `result` 内部 schema 由上游控制，强解析会随版本升级断裂。

### 3. 紧凑构造默认值

`AomQueryLogsRequest` 紧凑构造**只设默认**、**不做业务校验**（校验在 service 层）：

```java
public AomQueryLogsRequest {
    if (pageSize == null) {
        pageSize = 100;
    }
    if (isDesc == null) {
        isDesc = Boolean.TRUE;
    }
}
```

校验失败要走 `InvalidParamException` → `ErrorCode.INVALID_PARAM`，构造抛 IAE 会泄漏到错误码外（CLAUDE.md §3.4 + T05 同款约束）。

### 4. Service 层 5 项校验

```java
private static final Set<String> ALLOWED_CATEGORIES =
        Set.of("app_log", "node_log", "custom_log");
private static final int PAGE_SIZE_MIN = 1;
private static final int PAGE_SIZE_MAX = 1000;

private void validate(AomQueryLogsRequest request) {
    // category 白名单
    // startTime / endTime 非空
    // endTime > startTime
    // pageSize ∈ [1, 1000]
}
```

### 5. MCP Tool description 重点

> Returns the raw upstream 'result' JSON string for downstream parsing.

明确告诉 Agent：**返回的是 JSON 字符串而非结构化对象**，避免 Agent 误把 `result` 当对象解构。

## 验收标准

实际完成（spec §7 mapping）:

- [x] `AomLogTool` 注册成功，MCP Inspector 可见 `query_logs`
- [x] `AomLogService` 5 项校验完整
- [x] `AomMetricsAdapterImpl#queryLogs` 限流 `aom-readonly` + retry `huaweicloud-retryable`
- [x] `result` 字段 String 透传，未解析
- [x] 日志含 category / startTime / endTime / pageSize / 兜底 errorCode + upstreamTraceId
- [x] 代码已合入 master（`4c346d6`）
- [x] Checkstyle 0 violations
- [ ] Service 层 UT-S1~6（后续补 `AomLogServiceTest`）
- [ ] Adapter 层 UT-A1~5（后续在 `AomMetricsAdapterImplTest` 新增方法）
- [ ] Tool 层 UT-T1~2（后续补 `AomLogToolTest`）
- [ ] 类型契约测试 TC-01/02（后续补）
- [ ] 贵阳冒烟脚本 3 条（后续补）
- [ ] Micrometer 指标 + README 示例（后续补）

## AI 易错点提醒

**spec §4 已列出**:
1. `withPageSizeSize` 字段名双 `Size` 不是笔误
2. `withType("querylogs")` 必传
3. `result` 是 String 不是 object，不要解析
4. `keyWord`（驼峰，首字母小写）不是 `keyword`

**实现层特有**:
5. **紧凑构造只设默认不抛异常**：校验失败必须走 InvalidParamException
6. **复用 AomMetricsAdapterImpl 而非新建 AomLogAdapterImpl**：日志接口虽然语义独立但底层用同一个 AomClient + 同一个 aom-readonly 限流域，合并到现有类减少注入复杂度
7. **不 catch Exception**：参考 CLAUDE.md §3.4，Tool 层只 catch SmartomException 转 ErrorResponse
8. **MCP `@ToolParam(required = false)`**：可选参数必须显式标 false，否则 schema 标记 required

## 完成后

PR：`feat(aom): add query_logs MCP tool for AOM log search`（已落入 `4c346d6`）。

PR 描述包含：
- spec 链接
- adapter 字段映射表（重点：`withPageSizeSize` / `result` 透传）
- 遗留项清单（测试 / 冒烟 / README）
