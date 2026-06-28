## Context

存量基础设施回填。原始任务卡：`docs/tasks/T16-lts-adapter-base.md`（状态 In Progress→Done，用户已确认开放项后编码）。本变更属 infra / 横切层，无对外 capability spec，因此把全部重契约（SDK 类 / 方法 / 版本、字段映射、错误码映射、限流 / 重试 / 超时 / 可观测、时间参数格式不一致、AI 易错点）沉淀到本设计文档。

依赖：T03（`agentic-common`：错误码 / 异常 / 限流重试 / 健康检查 / `HuaweiCloudClientFactory`）。后置：T17–T18 在此基座上各自落地 MCP tool。

上游 API 复核结论：用户原始 3 个链接经 SDK 仓库 `LtsMeta` 复核后，实际只需封装 2 个 SDK 方法 —— `ListLogs`（用户列表 #2 与 #3 共指的同一 API）与 `ListLogContext`（#1）。

## Goals / Non-Goals

**Goals:**
- 为 LTS 新建独立 adapter 子模块 `agentic-adapter-lts`，封装 2 个只读日志查询能力（`listLogs` / `listLogContext`）。
- 复用 `agentic-common` 的 `HuaweiCloudClientFactory`、`HuaweiCloudInvocation`（限流 / 重试 / 异常映射），不复制 SDK builder 模板。
- SDK Request/Response 类型不外泄：通过自定义 DTO record 做边界隔离（`grep -r "com.huaweicloud.sdk.lts" agentic-monitoring agentic-mcp` 应无结果）。
- 单元测试覆盖 Bean 装配、2 方法成功路径、4 类 SDK 异常映射。

**Non-Goals:**
- `agentic-monitoring` 业务服务（后续任务）。
- MCP Tool 注册（T17–T18 各自做）。
- Contract Test（SDK 反射 / JSON 反序列化测试，本期未交付）。
- 部署后冒烟脚本、健康检查里加 LTS connectivity probe。
- 写操作（创建 / 删除日志组）、跨 region / 跨账号。
- 结构化日志查询（`/struct-content/query`，与本期 `/content/query` 不同）。
- `is_analysis_query=true` 时 SQL 语法的客户端预校验（上游自行解析）。

## Decisions

### SDK 映射

- **SDK 客户端类**：`com.huaweicloud.sdk.lts.v2.LtsClient`
- **Region 类**：`com.huaweicloud.sdk.lts.v2.region.LtsRegion`，用 `LtsRegion.valueOf(properties.getLtsRegion())`。
- **SDK 方法**：
  - `LtsClient.listLogs(ListLogsRequest) → ListLogsResponse` —— `POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/content/query`，body=`QueryLtsLogParams`。
  - `LtsClient.listLogContext(ListLogContextRequest) → ListLogContextResponse` —— `POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/context`，body=`ListLogContextRequestBody`。
- **SDK 版本**：与 CES / AOM / APM 共用根 pom `huaweicloud-sdk.version` = **3.1.177**（Maven Central 已发布，编译期验证通过）。`QueryLtsLogParams` / `ListLogContextRequestBody` / `LogContents` 等核心类字段名与类型在 3.1.177 与复核所用 3.1.194 之间稳定，**不单独引入版本属性、不 override**。
- **凭据**：LTS 是项目级服务（URI 含 `{project_id}`，但 `LtsMeta` 未注册 path requestField），SDK 从 `BasicCredentials.withProjectId(...)` 自动注入 path 变量。因此 `LtsClient` **必须**用 `HuaweiCloudClientFactory.credentialsWithProjectId(properties)`；只用 AK/SK 的 `credentials(...)` 会让请求带空 projectId 导致 404。

### 字段映射表

**`LtsListLogsRequest`**（对外 DTO → SDK `QueryLtsLogParams` body + 2 path 参数）：

| DTO 字段 | SDK 对应 | 类型 | 说明 |
|---|---|---|---|
| `logGroupId` | path `log_group_id` | `String` | 必填 |
| `logStreamId` | path `log_stream_id` | `String` | 必填 |
| `startTimeMillis` | body `start_time` | `Long` | SDK 字段为 String，`toSdk` 里用 `String.valueOf(long)`；对上层暴露 Long 与 CES/AOM 对齐 |
| `endTimeMillis` | body `end_time` | `Long` | 同上 |
| `labels` | body `labels` | `Map<String,String>` | 标签过滤，可为 null |
| `keywords` | body `keywords` | `String` | 关键字过滤，可为 null |
| `query` | body `query` | `String` | SQL 查询，可为 null |
| `isAnalysisQuery` | body `is_analysis_query` | `Boolean` | true 走 SQL 分析模式（响应走 `analysisLogs`），可为 null |
| `isCount` | body `is_count` | `Boolean` | true 只返回 count，可为 null |
| `limit` | body `limit` | `Integer` | 单页大小，可为 null |
| `isDesc` | body `is_desc` | `Boolean` | 排序方向，可为 null |
| `highlight` | body `highlight` | `Boolean` | 关键字高亮，可为 null |
| `isIterative` | body `is_iterative` | `Boolean` | 迭代查询，可为 null |
| `searchType` | body `search_type` | `String` | 仅 `forwards` / `backwards`；首次查询传 null（上游默认 `init`）。不做严格枚举（ADR-004） |
| `lineNum` | body `line_num` | `String` | 游标分页：上次结果尾行行号 |
| `cursorTime` | body `__time__` | `String` | 游标分页：与 `lineNum` 配对的时间戳。DTO 命名 `cursorTime`，转 SDK 时映射到 `__time__`（`body.setTime(...)`） |
| `scrollId` | body `scroll_id` | `String` | scroll API 分页 |

**`LtsListLogsResponse`**：`count`(Integer) / `logs`(List<LtsLogEntry>) / `isQueryComplete`(Boolean) / `analysisLogs`(List<Object>)。

**`LtsLogEntry`**（SDK `LogContents`）：`content`(String) / `lineNum`(line_num, String) / `labels`(Map<String,String>)。SDK 仅这 3 个字段，无 `host_name` / `host_ip` / `log_id`。

**`LtsListLogContextRequest`**（→ `ListLogContextRequestBody` + 2 path 参数）：`logGroupId` / `logStreamId`（path）/ `lineNum`(line_num, String) / `cursorTime`(__time__, String) / `backwardsSize`(backwards_size, Integer) / `forwardsSize`(forwards_size, Integer) / `scrollId`(scroll_id, String)。

**`LtsListLogContextResponse`**：`logs`(List<LtsLogEntry>) / `totalCount`(total_count, Integer) / `backwardsCount`(backwards_count, Integer) / `forwardsCount`(forwards_count, Integer) / `isQueryComplete`(Boolean)。

### 错误码 → retryable

经 `HuaweiCloudInvocation.execute(rateLimiterName, retryName, apiName, supplier)` 统一通道映射，retry 名 `huaweicloud-retryable`：

| 上游 | SDK 异常 | ErrorCode | retryable | 重试 |
|---|---|---|---|---|
| 429 | `ClientRequestException` | `UPSTREAM_THROTTLED` | true | 3 次指数退避 |
| 401 | `ClientRequestException` | `UPSTREAM_AUTH_FAILED` | false | 不重试 |
| 5xx | `ServerResponseException` | `UPSTREAM_ERROR` | true | 3 次指数退避 |
| Timeout | `RequestTimeoutException` | `TIMEOUT` | true | 3 次指数退避 |

失败响应透传 `upstreamTraceId`（华为云 `X-Request-Id`，可空）。

### 限流 / 重试 / 超时 / 可观测

- 限流域 `lts-readonly`：`limit-for-period: 10` / `limit-refresh-period: 1s` / `timeout-duration: 0`，与 `ces-readonly` / `aom-readonly` 一致。
- 重试域 `huaweicloud-retryable`：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 做 3 次指数退避。
- 超时：复用 `HuaweiCloudClientFactory.defaultHttpConfig()` 的传输层超时。
- 可观测：INFO 日志包含 `logGroupId` / `logStreamId` / `startTimeMillis` / `endTimeMillis` / `keywords` / `query` 摘要；API 标识 `lts.listLogs` / `lts.listLogContext`。

### 时间参数格式（不一致，重点）

LTS 的时间参数格式与其它华为云服务**不一致**，是 AI 高频出错点，集中说明：

- **上游 SDK 侧**：`QueryLtsLogParams.startTime` / `endTime` 在 SDK 里是 **`String`**（非 Long），Javadoc 标注语义为 **UTC 毫秒级**。即 SDK 期望的是"毫秒数的字符串形式"，而非 ISO8601。
- **adapter 对外 DTO 侧**：`startTimeMillis` / `endTimeMillis` 暴露为 **`Long`（UTC 毫秒）**，与 CES / AOM 对齐；转 SDK 时 `String.valueOf(longValue)`。**不要**为"匹配 SDK"把 DTO 也写成 String。
- **游标时间**：`__time__` 在响应 / 请求里是时间戳**字符串**，DTO 命名为 `cursorTime`（String），与 `lineNum` 配对作为游标分页值。
- 对比：CES v2 alarm history 用 `OffsetDateTime`；APM `ListAlarmData` 用 String（未固定格式）；LTS 用 "UTC 毫秒字符串" + Long(对外)。三者格式互不相同，迁移代码时不要照搬。

### AI 易错点（沉淀自任务卡）

1. `start_time` / `end_time` 在 SDK 是 String 不是 Long；DTO 对外 Long，转换用 `String.valueOf(...)`。
2. `__time__` JSON 键带前后双下划线，但 SDK Java 字段名只叫 `time`（`getTime()` / `setTime(...)`）；DTO 命名 `cursorTime`。
3. 分页是行号游标（`line_num` + `__time__` 配对），不是 offset；`scroll_id` 与 `line_num` 是两套独立分页模式，不要混用。
4. `search_type` 仅取 `forwards` / `backwards`，首次查询传 null（上游默认 `init`）；DTO 用 String 而非枚举，转 SDK 用 `QueryLtsLogParams.SearchTypeEnum.fromValue(value)`（对未知值返回新实例不抛异常）。
5. `is_analysis_query=true` 时响应走 `analysisLogs`（`List<Object>`）而非 `logs`；DTO 保 `List<Object>`，不强类型化。
6. `LogContents`（→ `LtsLogEntry`）只有 `content` / `line_num` / `labels` 三个字段，不要造 `host_name` / `host_ip` / `log_id` 假字段。
7. `LtsClient` 必须用 `credentialsWithProjectId`，否则 projectId 为空导致 404。
8. DTO 全用 `record`，不加 Lombok / 自定义 getter。
9. LTS SDK 共用 `${huaweicloud-sdk.version}` = 3.1.177，不单独引版本属性。
10. 响应 `labels` SDK 类型确认为 `Map<String,String>`，DTO 直接同型。
11. `ltsRegion` 默认 `cn-north-9`，经 `HUAWEICLOUD_LTS_REGION` 覆盖。

## Risks / Trade-offs

- **时间格式三服务不一致**：LTS（UTC 毫秒字符串）/ APM（String 不定格式）/ CES（OffsetDateTime）各异，跨服务复制代码极易引入隐性 bug。缓解：DTO 层统一对外语义（LTS 对外 Long 毫秒），转换集中在 `toSdk*` 一处。
- **`analysisLogs` 弱类型**：SDK 故意保留 `List<Object>`，adapter 不反序列化为强类型，解释权交上层 service。trade-off：类型安全 vs 通用性，本期选通用。
- **SDK 版本跨小版本字段稳定性假设**：3.1.177 与复核所用 3.1.194 字段一致为人工核对结论；若后续升级需重新核对 `QueryLtsLogParams` / `LogContents` 字段。
- **遗留项**（本期未交付，列入 tasks.md）：Contract Test / 冒烟脚本 / 健康检查 LTS probe / monitoring + tool / 结构化日志查询（`/struct-content/query`）。
