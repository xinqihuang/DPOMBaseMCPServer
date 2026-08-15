# add-unified-resource-discovery 验收报告

日期：2026-08-16
状态：已归档（2026-08-16-add-unified-resource-discovery）

## 1. 结果总览

- `openspec validate add-unified-resource-discovery --strict` → valid。
- `mvn clean verify` → BUILD SUCCESS：**454 tests，0 failures，0 errors，0 skipped**，checkstyle 0 违规。
  - agentic-monitoring：155 tests（含 ResourceDiscoveryServiceTest **21 用例**全绿）。
  - agentic-mcp：142 tests。
- 真实只读 MCP SSE smoke 通过（`discover_resource_context` 经 MCP 协议实际调用，见 §5）。

## 2. 实现

- **DTO**（`agentic-monitoring/.../discovery/`）：`ResourceIdentifier`（name/value/sourceTool/sourceApi/observedAt/kind/ambiguous）、
  `ResourceContext`、`ResourceCandidate`、`MissingCapability`、`DiscoveryRequest`、`IdentifierKind`、`MatchType`。
- **编排**：`ResourceDiscoveryService` 复用现有 `ApmDiscoveryService`（searchApplication / getEnvMonitorItems）与
  `LtsDiscoveryService`（listLogStreams），把受控锚点规范化为带 provenance 的上下文；多候选返回候选而非静默；
  无法完成的映射（CCE 节点→ECS、APM→LTS、LTS→APM、无权威 APM 实例映射）显式建模为 missing capability，绝不伪造。
- **MCP 工具**（`agentic-mcp/.../tool/UnifiedResourceDiscoveryTool`）：
  `discover_resource_context`（确定性上下文）与 `resolve_resource_candidates`（候选解析），均只读，用
  `ToolCallSupport` 统一错误映射。
- 全程未在 monitoring/mcp 层直接调用华为云 SDK，仅通过现有 adapter 端口。

## 3. 分页策略（P1）

- `search_apm_application` 固定 pageSize=100，从第 1 页起逐页拼接全部结果。
- 可靠终止边界只有三个：本页为空；本页不足 pageSize（末页）；最多 `MAX_PAGES=20` 页（页码每轮严格 +1，防停滞）。
  `appTotalCount` 仅作提示、不参与终止判断——它可能偏小，满页时不能仅凭累计数达到该值就停止，否则漏掉后续页目标。
- 回归测试：目标在第 2 页仍收敛、第一页 total 偏小且满 100 条时第二页目标仍被发现、无匹配返回 missing、
  多页多匹配返回全部候选、元数据异常安全终止、安全上限最多 20 页。

## 4. 同名标识冲突语义（P2）

同名 identifier 的 USER_PROVIDED 与上游返回值统一收敛：
- **同值**：去重为单一规范值，优先上游验证过的来源（sourceApi 为真实 API 标识）。
- **异值**：双方均保留 provenance 并标记 `ambiguous=true`，同时追加 conflict missing capability，
  绝不静默视为无冲突 EXACT。
- 覆盖字段（回归测试现状）：envId / appName / monitorItemId 覆盖同值去重；LTS log_group_id 覆盖异值冲突；
  LTS log_stream_id 同时覆盖同值去重与异值冲突。appName 锚点统一为 `apm_app_name`，与上游同名对齐以便收敛。

## 5. 真实 smoke test

`discover_resource_context(businessId=111092, region=cn-north-9)` 经 MCP SSE 协议实际调用，真实返回：

- identifiers：`region=cn-north-9`（USER_PROVIDED）、`apm_business_id=111092`（USER_PROVIDED），均为 EXACT、ambiguous=false。
- missingCapabilities：`lts_log_group_id`（APM→LTS 日志组/流的确定性映射缺口），动态生成。
- candidates：`search_apm_application` 经分页遍历返回 **236 个组件候选**（DPFtpService / DPModelProxyService / …），
  matchType=NAME_MATCH、sourceTool=search_apm_application、sourceApi=ApmSearchApplication、
  nextStep=「提供 env_id 以收敛到唯一组件」。
- **说明**：候选数由上一版约 100 增至 236，正是分页遍历全部页的结果（此前仅取第 1 页）。此结果**不是确定性发现成功**——
  它如实报告多候选歧义，需补充 env_id（或精确 app_name）才能收敛；未静默取第一个，也未用数值置信度冒充。

## 6. 主要修改文件

- `agentic-monitoring/.../discovery/`：7 个 DTO/枚举 + ResourceDiscoveryService（分页 + 冲突收敛）+ ResourceDiscoveryServiceTest（21 用例）。
- `agentic-mcp/.../tool/UnifiedResourceDiscoveryTool.java` + UnifiedResourceDiscoveryToolTest。
- `openspec/changes/add-unified-resource-discovery/`：proposal/design/spec/tasks。
- `docs/tasks/T38-unified-resource-discovery.md`。

## 7. 已知缺口

- CCE 节点→ECS instance_id、CCE 节点清单、APM→LTS、LTS→APM、无权威 APM 实例 id 的映射需额外输入或新 adapter 能力，
  当前显式建模为 missing capability。
- APM business 下组件数量大（111092 下约 236 个组件），多候选场景需 Agent 补充 env_id 或精确 app_name 收敛。

## 8. 验收命令复现

- `openspec validate add-unified-resource-discovery --strict`
- `mvn clean verify`（JDK 21 + Maven 3.9.16）
- 真实只读 smoke：经 MCP SSE 协议调用 `discover_resource_context(businessId=111092, region=cn-north-9)`
