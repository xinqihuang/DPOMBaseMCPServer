# T18 — 实现 query_lts_log_context tool（LTS 日志上下文）

> 状态: **Done**（已实现并合入 master） · 估时: 0.3d · 依赖: T16（agentic-adapter-lts.listLogContext 已就绪）/ T17（service+tool 风格已沉淀）· 关联 spec: `docs/specs/tools/query_lts_log_context.md` · 后置: 无（LTS 适配本期收尾）

## 目标

按 spec 实现 monitoring service 与 MCP tool 两层，让 Agent 在 `query_lts_logs` 命中目标
日志后能直接调用 `query_lts_log_context` 拉取上下文。

## 范围

**做**:

- 新增 monitoring service：`agentic-monitoring/.../monitoring/lts/LtsLogContextService.java`，
  做 spec §3.2 的 7 条校验后委托给 `LtsLogAdapter.listLogContext(...)`
- 新增 MCP tool：`agentic-mcp/.../mcp/tool/LtsLogContextTool.java`，@Tool 注册，
  捕获 `SmartomException` 转 `ErrorResponse`
- `McpServerConfig` 注册 `LtsLogContextTool` 到 `ToolCallbackProvider`
- 单元测试：`LtsLogContextServiceTest`（UT-S1 ~ UT-S9，9 条）+
  `LtsLogContextToolTest`（UT-T1 ~ UT-T3，3 条）
- 复用 T16 adapter 与 `LtsListLogContextRequest` / `LtsListLogContextResponse`
- 更新 `docs/tasks/README.md` 加入 T18 行

**不做**（防止任务蔓延，列为遗留）:

- ❌ 新增 RateLimiter 实例（复用 `lts-readonly`）
- ❌ Contract Test、冒烟脚本、Micrometer 看板、README 使用示例
- ❌ LTS 健康检查
- ❌ 跨流上下文 / 多目标合并
- ❌ 自动 pre-fetch：让 `query_lts_logs` 直接附带前后 N 条上下文（属另一类 tool 设计）

## 前置阅读

**必读**:

1. `docs/specs/tools/query_lts_log_context.md` — 完整 spec
2. `docs/tasks/T17-query-lts-logs.md` — 上一个 LTS tool 的结构与风格参照（**强烈推荐照抄骨架**）
3. `CLAUDE.md` — 编码规范

**强烈推荐**（参照风格）:

4. `agentic-monitoring/.../monitoring/lts/LtsLogService.java` — service 校验骨架
5. `agentic-mcp/.../mcp/tool/LtsLogTool.java` — Tool 注册骨架
6. `agentic-mcp/.../mcp/config/McpServerConfig.java` — 看 T17 是如何挂到 `toolCallbackProvider(...)`，T18 加一行即可

## 产物清单

```
docs/specs/tools/
  query_lts_log_context.md                          ← spec，已生成

docs/tasks/
  T18-query-lts-log-context.md                      ← 本任务卡
  README.md                                         ← 修改：加入 T18 行

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/lts/
    LtsLogContextService.java                       ← 新增：校验 + 委托
  src/test/java/com/huawei/smartom/agentic/monitoring/lts/
    LtsLogContextServiceTest.java                   ← 新增：UT-S1~S9

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/LtsLogContextTool.java                     ← 新增：@Tool 注册
    config/McpServerConfig.java                     ← 修改：注册 LtsLogContextTool
  src/test/java/com/huawei/smartom/agentic/mcp/
    tool/LtsLogContextToolTest.java                 ← 新增：UT-T1~T3
```

## 关键技术要求

### 1. LtsLogContextService

```java
@Service
public class LtsLogContextService {

    private static final int SIZE_MIN = 0;
    private static final int SIZE_MAX = 500;

    private final LtsLogAdapter adapter;

    public LtsLogContextService(LtsLogAdapter adapter) {
        this.adapter = adapter;
    }

    public LtsListLogContextResponse queryContext(LtsListLogContextRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.listLogContext(request);
    }

    private void validate(LtsListLogContextRequest r) {
        Validations.requireNonBlank(r.logGroupId(), "log_group_id");
        Validations.requireNonBlank(r.logStreamId(), "log_stream_id");

        boolean hasScroll = !Validations.isBlank(r.scrollId());
        boolean lineMissing = Validations.isBlank(r.lineNum());
        boolean cursorMissing = Validations.isBlank(r.cursorTime());

        if (!hasScroll) {
            if (lineMissing || cursorMissing) {
                throw new InvalidParamException(
                        "either (line_num + cursor_time) or scroll_id is required");
            }
        }
        else {
            // scroll 模式下 line_num / cursor_time 任意；但单边给会让 SDK 行为不确定
            if (lineMissing != cursorMissing) {
                throw new InvalidParamException(
                        "line_num and cursor_time must be provided together");
            }
        }

        validateSize(r.backwardsSize(), "backwards_size");
        validateSize(r.forwardsSize(), "forwards_size");

        // 零零组合无意义
        if (Integer.valueOf(0).equals(r.backwardsSize())
                && Integer.valueOf(0).equals(r.forwardsSize())) {
            throw new InvalidParamException(
                    "at least one of backwards_size / forwards_size must be > 0");
        }
    }

    private void validateSize(Integer value, String field) {
        if (value != null && (value < SIZE_MIN || value > SIZE_MAX)) {
            throw new InvalidParamException(
                    field + " must be in [" + SIZE_MIN + ", " + SIZE_MAX + "], got: " + value);
        }
    }
}
```

注意：当 `scroll_id` 给定时，spec §3.2 允许同时给或同时不给 `line_num` / `cursor_time`，
单边给视为非法。

### 2. LtsLogContextTool

```java
@Component
public class LtsLogContextTool {

    private final LtsLogContextService service;

    @Tool(
        name = "query_lts_log_context",
        description = """ ... 见 spec §3.1 ... """)
    public Object queryLtsLogContext(
            @ToolParam(description = "LTS log group id (required).") String logGroupId,
            @ToolParam(description = "LTS log stream id (required).") String logStreamId,
            @ToolParam(description = "Target log line_num (required for first call; pair with cursor_time).",
                       required = false) String lineNum,
            @ToolParam(description = "Target log __time__ (required for first call; pair with line_num).",
                       required = false) String cursorTime,
            @ToolParam(description = "Number of entries to fetch before the target, in [0, 500]. SDK default 100.",
                       required = false) Integer backwardsSize,
            @ToolParam(description = "Number of entries to fetch after the target, in [0, 500]. SDK default 100.",
                       required = false) Integer forwardsSize,
            @ToolParam(description = "Scroll id from a previous response; provide alone to fetch the next page.",
                       required = false) String scrollId) {

        LtsListLogContextRequest req = new LtsListLogContextRequest(
                logGroupId, logStreamId, lineNum, cursorTime,
                backwardsSize, forwardsSize, scrollId);
        try {
            return service.queryContext(req);
        }
        catch (SmartomException e) {
            LOG.warn("query_lts_log_context failed, errorCode={}, upstreamTraceId={}",
                    e.getErrorCode(), e.getUpstreamTraceId());
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
    }
}
```

### 3. McpServerConfig 增量

把 `LtsLogContextTool` 加到构造参数与 `toolObjects(...)` 列表，紧跟 `LtsLogTool` 之后。

### 4. 单元测试矩阵

`LtsLogContextToolTest` 3 条 + `LtsLogContextServiceTest` 9 条（按 spec §6 表格）。

## 验收标准

- [ ] `LtsLogContextServiceTest` 9 条 UT 通过
- [ ] `LtsLogContextToolTest` 3 条 UT 通过
- [ ] 全仓 `mvn test` 全绿（不破坏既有测试）
- [ ] `McpServerConfig.toolCallbackProvider` 中能看到 `LtsLogContextTool` 实例
- [ ] Checkstyle 0 violations
- [ ] SDK 类型不泄漏：`grep com.huaweicloud.sdk.lts agentic-monitoring agentic-mcp` 无结果
- [ ] `docs/tasks/README.md` 列表加入 T18 行

## AI 易错点提醒

1. **Tool 参数和 DTO record 顺序对齐**：`LtsListLogContextRequest` 7 字段，构造时按位置传，错一个就全错。
2. **`line_num` / `cursor_time` 成对规则**：spec §3.2 校验规则 #2/#3 / `validate()` 中已写明；
   特别留意 **scroll 模式下两者可同时为空、不可只给一个**。
3. **`scroll_id` 与首次模式的边界**：scroll 模式（`scroll_id` 非空）下 `line_num` + `cursor_time`
   是"任意"而非"必填"——上游会用 scroll_id 锁定上次会话。不要把首次模式的必填规则照搬到 scroll 模式。
4. **`backwards_size` / `forwards_size` 都为 0 时拒绝**：spec §3.2 校验规则 #5；
   注意 null（未传，走 SDK 默认 100）和 0（显式禁用）的区别。
5. **`scroll_id` 当前 SDK 响应里没有单独字段返回**——上游可能把后续游标编码在 `line_num` 内
   或通过 HTTP 头返回。spec §3.3 已说明，本期 Agent 翻页直接复用响应尾行 `line_num` + `cursor_time`，
   **不要**为了"补 scroll_id 输出字段"去改 T16 的 DTO。
6. **不需要新加 RateLimiter**：复用 `lts-readonly`；不要在 `application.yml` 加新条目。
7. **Tool description 提示 Agent 用法顺序**：必须说明 "use this after query_lts_logs"，
   否则 LLM 可能直接试图用 `query_lts_log_context` 做关键字搜索。
8. **更新 `docs/tasks/README.md`**：把 T18 加入主表，状态 Done。

## 完成后

PR：`feat(T18): query_lts_log_context monitoring service + MCP tool`。

PR 描述附上：
- 测试用例对应表（UT-S1~S9 + UT-T1~T3）
- 注册到 `McpServerConfig` 后的 tool 总数（应为 16，含 helloWorld）
- 遗留项：冒烟脚本、Micrometer 看板、README 示例、LTS 健康探针
