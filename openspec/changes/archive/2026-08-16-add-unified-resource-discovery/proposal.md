## Why

诊断 Agent 在 APM/AOM/CES/LTS/CCE 之间定位资源时，必须反复猜测并手工拼接 businessId、envId、instanceId、inventoryId、ECS instance_id、clusterId、日志组/流等标识；同名服务、跨 region、跨 EPS、多候选时极易误关联。需要一个确定性的只读资源发现与标识关联入口，把已知锚点规范化为带来源与歧义标注的 ResourceContext。

## What Changes

- 新增只读 MCP 工具 `discover_resource_context`：接受受控锚点组合，返回规范化 ResourceContext。
- 新增只读 MCP 工具 `resolve_resource_candidates`：无法唯一映射时返回候选 + 来源 + match type + 缺失的下一步标识，绝不静默选第一个。
- 每个字段标注 source_tool/source_api、observed_at、exact/derived、ambiguity；不臆造映射。
- `search_apm_application` 按真实分页字段遍历全部页，杜绝目标落在后续页时的假阴性；设安全上限并防止页码不前进。
- 同名标识做统一收敛：USER_PROVIDED 与上游同值去重为单一规范值，异值标记冲突并保留 provenance，绝不静默视为无冲突 EXACT。
- 复用现有只读 discovery 工具与 adapter，不在 monitoring/mcp 层直接调用华为云 SDK。
- 把无法完成的映射（CCE 节点→ECS、APM→LTS 等）建模为 missing capability。

## Capabilities

### New Capabilities

- `unified-resource-discovery`: 规范化资源发现与标识关联，返回带 provenance/ambiguity 的 ResourceContext 与候选解析。

## Impact

- 模块：agentic-monitoring（DiscoveryService + ResourceContext DTO）、agentic-mcp（discover_resource_context / resolve_resource_candidates 工具）。
- 复用现有 adapter 只读接口（APM business/application/monitor-item、LTS group/stream、AOM metric、CES metric），不新增 SDK 依赖。
- 边界：纯只读，无生产写、无自动修复、无命令执行、无 RAG、不输出凭据。
