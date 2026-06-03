# T17 — 实现 query_lts_logs tool（LTS 日志搜索）

> 状态: **Done**（已实现并合入 master） · 估时: 0.5d · 依赖: T16（agentic-adapter-lts 已就绪）· 关联 spec: `docs/specs/tools/query_lts_logs.md` · 后置: T18（query_lts_log_context）

## 目标

按 spec 实现 monitoring service 与 MCP tool 两层，让 Agent 能直接调用 `query_lts_logs` 检索华为云 LTS 日志，并把 T16 adapter 的能力暴露给 MCP。

## 范围

**做**:

- 新增 monitoring service：`agentic-monitoring/.../monitoring/lts/LtsLogService.java`，做 spec §3.2 的 8 条校验后委托给 `LtsLogAdapter.listLogs(...)`
- 新增 MCP tool：`agentic-mcp/.../mcp/tool/LtsLogTool.java`，@Tool 注册，捕获 `SmartomException` 转 `ErrorResponse`
- `McpServerConfig` 注册 `LtsLogTool` 到 `ToolCallbackProvider`
- 单元测试：`LtsLogServiceTest`（UT-S1 ~ UT-S8，8 条）+ `LtsLogToolTest`（UT-T1 ~ UT-T3，3 条）
- 复用 T16 adapter 与 `LtsListLogsRequest` / `LtsListLogsResponse`

**不做**（防止任务蔓延，列为遗留）:

- ❌ `query_lts_log_context` tool（T18）
- ❌ `list_log_groups` / `list_log_streams` 等发现性工具
- ❌ Contract Test、冒烟脚本、Micrometer 看板、README 使用示例
- ❌ LTS 健康检查
- ❌ 结构化日志查询（`/struct-content/query` 路径）
- ❌ 客户端 SQL 语法校验

## 前置阅读

**必读**:

1. `docs/specs/tools/query_lts_logs.md` — 完整 spec
2. `CLAUDE.md` — 编码规范、SDK 边界、错误码、日志风格
3. `docs/tasks/T16-lts-adapter-base.md` — 上游 adapter 任务卡（已 Done）

**强烈推荐**（参照风格）:

4. `agentic-monitoring/.../monitoring/ces/CesMetricDataService.java` —— service 校验骨架（看 `Validations.requireNonBlank` / `from < to` 写法）
5. `agentic-mcp/.../mcp/tool/CesMetricDataTool.java` —— Tool 注册骨架（看 try/catch SmartomException 模式）
6. `agentic-mcp/.../mcp/tool/AomLogTool.java` —— AOM 日志 tool（同样是日志检索语义，参考其参数粒度）
7. `agentic-mcp/.../mcp/config/McpServerConfig.java` —— 看新增 tool 如何挂到 `toolCallbackProvider(...)`

## 产物清单

```
docs/specs/tools/
  query_lts_logs.md                                ← spec，已生成

docs/tasks/
  T17-query-lts-logs.md                            ← 本任务卡

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/lts/
    LtsLogService.java                             ← 新增：校验 + 委托
  src/test/java/com/huawei/smartom/agentic/monitoring/lts/
    LtsLogServiceTest.java                         ← 新增：UT-S1~S8

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/LtsLogTool.java                           ← 新增：@Tool 注册
    config/McpServerConfig.java                    ← 修改：注册 LtsLogTool
  src/test/java/com/huawei/smartom/agentic/mcp/
    tool/LtsLogToolTest.java                       ← 新增：UT-T1~T3
```

## 关键技术要求

### 1. LtsLogService

```java
@Service
public class LtsLogService {

    private static final Set<String> ALLOWED_SEARCH_TYPES = Set.of("forwards", "backwards");
    private static final int LIMIT_MIN = 1;
    private static final int LIMIT_MAX = 5000;

    private final LtsLogAdapter adapter;

    public LtsLogService(LtsLogAdapter adapter) {
        this.adapter = adapter;
    }

    public LtsListLogsResponse queryLogs(LtsListLogsRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.listLogs(request);
    }

    private void validate(LtsListLogsRequest r) {
        Validations.requireNonBlank(r.logGroupId(), "log_group_id");
        Validations.requireNonBlank(r.logStreamId(), "log_stream_id");

        if (r.startTimeMillis() != null && r.endTimeMillis() != null
                && r.startTimeMillis() >= r.endTimeMillis()) {
            throw new InvalidParamException(...);
        }
        if (r.limit() != null && (r.limit() < LIMIT_MIN || r.limit() > LIMIT_MAX)) {
            throw new InvalidParamException(...);
        }
        if (r.searchType() != null && !ALLOWED_SEARCH_TYPES.contains(r.searchType())) {
            throw new InvalidParamException(...);
        }
        // line_num / cursor_time 必须成对
        if ((r.lineNum() == null) != (r.cursorTime() == null)) {
            throw new InvalidParamException(
                    "line_num and cursor_time must be provided together");
        }
    }
}
```

按 ADR-004：`search_type` 因为枚举可能扩展，仍以 String + Set<String> 校验，**不**做严格枚举。

### 2. LtsLogTool

参数较多（17 个），但都是基础类型 / Map / 字符串，Spring AI 能直接绑定。Tool 层无业务校验，纯透传：

```java
@Component
public class LtsLogTool {

    private final LtsLogService service;

    @Tool(
        name = "query_lts_logs",
        description = """ ... 见 spec §3.1 ... """)
    public Object queryLtsLogs(
            @ToolParam(description = "LTS log group id (required).") String logGroupId,
            @ToolParam(description = "LTS log stream id (required).") String logStreamId,
            @ToolParam(description = "Start time, UTC millis.", required = false) Long startTimeMillis,
            @ToolParam(description = "End time, UTC millis, must be > start_time_millis.", required = false) Long endTimeMillis,
            @ToolParam(description = "Label filter, AND of key=value.", required = false) Map<String, String> labels,
            @ToolParam(description = "Free-text keyword filter.", required = false) String keywords,
            @ToolParam(description = "SQL or expression query (set is_analysis_query=true for analysis mode).", required = false) String query,
            @ToolParam(description = "Set true to run SQL analysis; result returned via analysis_logs.", required = false) Boolean isAnalysisQuery,
            @ToolParam(description = "If true, only return matched row count.", required = false) Boolean isCount,
            @ToolParam(description = "Page size in [1, 5000].", required = false) Integer limit,
            @ToolParam(description = "Sort direction: true for desc.", required = false) Boolean isDesc,
            @ToolParam(description = "Highlight matched keywords.", required = false) Boolean highlight,
            @ToolParam(description = "Iterative query mode.", required = false) Boolean isIterative,
            @ToolParam(description = "Pagination direction: forwards | backwards (null for first page).", required = false) String searchType,
            @ToolParam(description = "Cursor: line_num from previous page tail; pair with cursor_time.", required = false) String lineNum,
            @ToolParam(description = "Cursor: __time__ from previous page tail; pair with line_num.", required = false) String cursorTime,
            @ToolParam(description = "Scroll-API id for an alternative pagination mode.", required = false) String scrollId) {

        LtsListLogsRequest req = new LtsListLogsRequest(
                logGroupId, logStreamId, startTimeMillis, endTimeMillis,
                labels, keywords, query, isAnalysisQuery, isCount, limit,
                isDesc, highlight, isIterative, searchType,
                lineNum, cursorTime, scrollId);
        try {
            return service.queryLogs(req);
        } catch (SmartomException e) {
            LOG.warn("query_lts_logs failed, errorCode={}, upstreamTraceId={}",
                    e.getErrorCode(), e.getUpstreamTraceId());
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
    }
}
```

### 3. McpServerConfig 增量

把 `LtsLogTool` 加到构造参数与 `toolObjects(...)` 列表。

### 4. 单元测试矩阵

`LtsLogToolTest`：

| ID | 用例 |
|---|---|
| UT-T1 | success passthrough — `ArgumentCaptor` 校验 17 个字段对齐到 `LtsListLogsRequest` |
| UT-T2 | service `InvalidParamException` → `ErrorResponse` / `INVALID_PARAM` |
| UT-T3 | service `UpstreamException(UPSTREAM_THROTTLED, "throttled", "req-x", null)` → `ErrorResponse`，trace id 透传 |

`LtsLogServiceTest`（mock `LtsLogAdapter`）：UT-S1 ~ UT-S8，覆盖 spec §3.2 全部校验规则；每条用例用最小入参构造 `LtsListLogsRequest`，关键路径用 `verify(adapter, never()).listLogs(any())` 断言短路。

## 验收标准

- [ ] `LtsLogServiceTest` 8 条 UT 通过
- [ ] `LtsLogToolTest` 3 条 UT 通过
- [ ] `mvn -pl agentic-mcp -am test` 全绿（不破坏既有 57 条 tool 测试）
- [ ] `McpServerConfig.toolCallbackProvider` 中能看到 `LtsLogTool` 实例
- [ ] Checkstyle 0 violations
- [ ] SDK 类型不泄漏：`grep com.huaweicloud.sdk.lts agentic-monitoring agentic-mcp` 无结果

## AI 易错点提醒

1. **Tool 层参数顺序与 DTO record 完全一致**——`LtsListLogsRequest` 是 17 字段的 record，构造时按位置传，错一个就全错。建议先在 Tool 入口写好 `new LtsListLogsRequest(arg1, arg2, ..., arg17)`，再逐一对照 spec §3.2 表格。
2. **`searchType` 校验在 service 层用 `Set<String>`，不要在 Tool 层做枚举解析**。按 ADR-004 这是 lenient catalog 而非严格枚举；service 拒绝未知值即可。
3. **`line_num` 与 `cursor_time` 必须成对**：spec §3.2 校验规则 #5。单边给会让 SDK 行为难预测，应在 service 主动拦下来。
4. **`limit` 上限 5000**：与 CES / AOM 的 1000 不同，LTS 上游允许更大单页。**不要**照搬 CES 校验里的 1000。
5. **Tool 层不要 try-catch IllegalArgumentException**：T17 没有像 `CesMetricDataTool` 那样的 enum 解析路径；所有校验都在 service 层抛 `InvalidParamException`，Tool 只 catch `SmartomException`。
6. **`Map<String, String> labels` 作为 `@ToolParam`**：Spring AI 1.0.4 可以直接绑定 Map，但 LLM 输入 JSON 必须是 `{"key": "value"}` 而非数组。description 里给出示例。
7. **是否给 `query` 字段加 SQL 提示**：spec 已说明，Tool description 必须明确"`query` 是 SQL 或表达式，结合 `is_analysis_query=true` 启用 SQL 分析模式"，否则 LLM 容易把 `query` 当成 keyword 误传。
8. **不要再做 `__time__` 命名转换**：DTO 字段名已经是 `cursorTime`，T16 adapter 已负责映射到 SDK 的 `__time__`；Tool / service 全程用 `cursorTime`。
9. **更新 `docs/tasks/README.md`**：把 T17 加入主表。

## 完成后

PR：`feat(T17): query_lts_logs monitoring service + MCP tool`。

PR 描述附上：
- 测试用例对应表（UT-S1~S8 + UT-T1~T3）
- 注册到 `McpServerConfig` 后 `toolCallbackProvider` 完整 tool 列表
- 遗留项：T18 (log_context) / 冒烟脚本 / Micrometer 看板 / README 示例
