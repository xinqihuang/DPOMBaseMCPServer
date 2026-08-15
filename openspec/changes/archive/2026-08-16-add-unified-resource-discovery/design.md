# Design: unified resource discovery

## Context

诊断 Agent 跨 APM/AOM/CES/LTS/CCE 定位资源时反复猜测标识。本 Change 新增只读编排层，把已知锚点规范化为带 provenance/ambiguity
的 ResourceContext，并在无法唯一映射时返回候选与缺失下一步，杜绝名称误关联。

## Goals / Non-Goals

**Goals:**
- `discover_resource_context`（确定性上下文）与 `resolve_resource_candidates`（候选解析）两个只读 MCP 工具。
- 输出覆盖 project/region/EPS、CCE cluster/namespace/workload/pod/node、ECS instance、APM business/app/env/instance/monitor item、
  AOM inventory、LTS group/stream，均带 provenance 与 ambiguity。
- 复用现有 adapter 只读接口，不在 monitoring/mcp 层直调 SDK。

**Non-Goals:**
- 不新增写工具、不自动修复、不执行命令、不上传数据、不引入 RAG。
- 不新增 SDK 依赖；无法完成的映射显式建模为 missing capability。

## Decisions

### D1 工具面
`discover_resource_context`：入参为受控锚点（region/service/appName/clusterId/namespace/podName/workloadName/businessId/envId/
instanceId/ipAddress/logGroupName/logStreamName/traceId/alarmId 等，全部 optional，至少其一）；返回 ResourceContext。
`resolve_resource_candidates`：入参同上；当任一标识无法唯一确定时返回 candidates[]（含 matchType/source/nextStep），
绝不静默取第一个。

### D2 ResourceContext 结构与 provenance
`ResourceIdentifier`（name/value/sourceTool/sourceApi/observedAt/kind/ambiguous）。`sourceTool` 与 `sourceApi` 语义分离：
前者是取值来源工具或 `USER_PROVIDED`（用户/Agent 提供的锚点），后者是真实 adapter/华为云 API 标识
（如 `ApmSearchApplication`、`LtsListLogStreams`）。用户输入标记 `USER_PROVIDED`，不得冒充上游验证后的 EXACT。
`apm_app_id`（组件 id）绝不映射为 `apm_instance_id`（实例 id）——二者不是同一概念。

### D3 复用现有 adapter
只读编排层调用现有 adapter 端口：APM 业务/应用/监控项发现（listBusiness/searchApplication/showEnvMonitorItems）、
LTS 日志组/流（listLogGroups/listLogStreams）、AOM/CES 指标发现（按需）。禁止 import 华为云 SDK。

### D4 映射策略、候选与动态缺口
- 同名服务/跨 region/跨 EPS 多候选：返回候选 + `MatchType`（EXACT_MATCH / NAME_MATCH / UNSCORED），不伪造数值置信度；
  消除歧义所需 nextStep 不得要求用户重复提供已有字段。
- CCE 节点→ECS、APM→LTS、LTS→APM 等无现有工具完成的映射：按请求目标与真实缺口动态生成 missing capability，
  不用名称模糊匹配冒充确定关系。
- businessId/envId/appName 组合做一致性校验：envId 提供时必须用于过滤/验证 search_apm_application 结果；
  instanceId/IP 作为 USER_PROVIDED 保留（现有发现工具不返回实例 id，无法验证时标注缺口而非伪造）。

### D5 分页遍历
`search_apm_application` 固定 pageSize=100，从第 1 页起逐页拼接全部结果；可靠终止边界只有三个：本页为空、
本页不足 pageSize（末页）、最多 `MAX_PAGES=20` 页（页码每轮严格 +1），任一分支都保证退出，分页元数据异常也不会死循环。
`appTotalCount` 仅作提示、不参与终止判断——它可能偏小，满页时不能仅凭累计数达到该值就停止，否则会漏掉后续页的目标。

### D6 同名标识收敛
同名 identifier 的 USER_PROVIDED 与上游返回值统一收敛：同值去重为单一规范值（优先上游验证过的来源），
异值标记 `ambiguous=true` 并追加 conflict missing capability，保留双方 provenance，绝不静默视为无冲突 EXACT。
覆盖 envId/appName/monitorItemId 与 LTS group/stream。

### D7 边界
纯只读；production 默认注册；不执行生产写、不自动修复、不输出凭据；日志不打印 AK/SK。

## Risks / Trade-offs

- [发现链不完整] → 现有 adapter 只能覆盖 APM/LTS/AOM/CES 的已实现发现；CCE 节点清单等缺口显式标注 missing capability。
- [多候选歧义] → 返回候选而非猜测；用 next_step 引导补锚点，收敛歧义。
- [分页量过大] → MAX_PAGES=20 与 pageSize=100 限制单次最多 2000 个组件，超出部分以 missing/候选呈现而非无限拉取。
