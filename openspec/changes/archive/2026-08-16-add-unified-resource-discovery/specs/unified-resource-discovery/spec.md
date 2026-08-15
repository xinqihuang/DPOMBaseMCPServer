## Purpose

为诊断 Agent 提供确定性的只读资源发现与标识关联：把已知锚点规范化为带来源（provenance）与歧义（ambiguity）标注的
ResourceContext，并在无法唯一映射时返回候选与缺失的下一步标识，杜绝跨 APM/AOM/CES/LTS/CCE 的标识猜测与名称误关联。

## ADDED Requirements

### Requirement: 只读资源发现入口
系统 SHALL 提供只读 MCP 工具 `discover_resource_context` 与 `resolve_resource_candidates`，接受受控锚点组合，返回规范化
ResourceContext 或候选列表；二者 MUST 为只读且不执行任何生产变更。

#### Scenario: 锚点驱动发现
- **WHEN** Agent 传入至少一个受控锚点
- **THEN** 系统 SHALL 调用现有只读 discovery 工具与 adapter 组装 ResourceContext

#### Scenario: 无锚点拒绝
- **WHEN** Agent 未传入任何锚点
- **THEN** 系统 SHALL 返回 INVALID_PARAM，不发起上游调用

#### Scenario: 锚点覆盖告警与日志标识
- **WHEN** Agent 传入 APM 告警锚点（businessId/envId/instanceId/monitorItemId/IP/alarmId/traceId）或 LTS 标识（logGroupId/logStreamId）
- **THEN** 系统 SHALL 原样保留这些锚点，供后续趋势/查询工具直接使用

### Requirement: ResourceContext 规范化与 provenance 分离
系统 SHALL 返回规范化 ResourceContext，每个标识的 sourceTool 与 sourceApi 语义分离；用户输入标记 USER_PROVIDED，不得冒充上游验证后的 EXACT。

#### Scenario: provenance 分离
- **WHEN** 系统返回某标识
- **THEN** sourceTool SHALL 为取值来源工具或 USER_PROVIDED，sourceApi SHALL 为真实 adapter/华为云 API 标识或 USER_PROVIDED
- **AND** 用户提供的锚点 SHALL 标记 USER_PROVIDED，不得标记为上游验证后的 EXACT

#### Scenario: appId 不冒充实例 id
- **WHEN** 上游返回组件 id（appId）
- **THEN** 系统 SHALL 将其标注为 apm_app_id，MUST NOT 映射为 apm_instance_id
- **AND** 无法获得权威实例 id 时 SHALL 返回 missing capability 或 candidate，不得伪造

### Requirement: 一致性校验与确定性收敛
系统 SHALL 对 businessId/envId/appName 组合做一致性校验；envId 提供时必须用于过滤/验证 search_apm_application 结果；instanceId/IP 作为受控锚点保留。

#### Scenario: envId 收敛
- **WHEN** businessId 与 envId 同时提供
- **THEN** 系统 SHALL 用 envId 过滤 APM 组件结果，匹配到唯一组件时返回其 appName/appId
- **AND** 匹配不到时 SHALL 标注一致性冲突，不静默选第一个

#### Scenario: 已提供字段不重复提示
- **WHEN** 系统返回候选的下一步提示
- **THEN** 提示 SHALL NOT 要求用户重复提供已作为锚点传入的字段

### Requirement: 候选解析不静默且不伪造置信度
系统 SHALL 在标识无法唯一映射时返回候选列表（含来源、MatchType、缺失的下一步标识），MUST NOT 静默选择第一个候选，MUST NOT 用无证据的数值置信度冒充评分。

#### Scenario: 多候选返回
- **WHEN** 同名服务或跨 region/跨 EPS 存在多个候选
- **THEN** 系统 SHALL 返回全部候选及 MatchType 与来源
- **AND** 系统 SHALL 指明消除歧义所需的下一步标识

### Requirement: 复用现有只读适配器
系统 SHALL 仅通过现有 adapter 的只读接口完成发现，禁止在 monitoring/mcp 层直接调用华为云 SDK。

#### Scenario: 不直接调用 SDK
- **WHEN** 执行资源发现
- **THEN** 所有上游调用 SHALL 经由现有 adapter 端口完成
- **AND** monitoring/mcp 层 SHALL 不 import 任何华为云 SDK 类

### Requirement: 缺失能力按请求目标动态建模
系统 SHALL 按请求目标与实际缺口动态生成 missing capability，不得固定返回无关缺口；CCE 节点→ECS、APM→LTS、LTS→APM 等无法完成的映射显式建模为 missing capability，不得用名称模糊匹配冒充确定关系。

#### Scenario: 缺口显式且相关
- **WHEN** 请求含 CCE 锚点但无节点/ECS 标识
- **THEN** 系统 SHALL 标注 node_name/ecs_instance_id 缺口
- **AND** 请求不含 CCE 锚点时 SHALL NOT 返回这些缺口

### Requirement: APM 组件发现遍历全部页
系统 SHALL 调用 `search_apm_application` 时逐页遍历全部结果页，MUST NOT 仅取第一页后本地过滤（否则目标落在后续页会假阴性）；终止边界为空页/短页/最大页数上限，`appTotalCount` 仅作提示、不参与终止判断；并 SHALL 设安全上限与防页码停滞机制。

#### Scenario: 目标在后续页
- **WHEN** 目标组件位于第 2 页及以后
- **THEN** 系统 SHALL 逐页拉取并正确收敛到该组件

#### Scenario: 总数偏小不截断
- **WHEN** 第一页 `appTotalCount` 偏小且该页恰好满 pageSize
- **THEN** 系统 SHALL NOT 因累计数达到 `appTotalCount` 而停止，SHALL 继续拉取后续页

#### Scenario: 分页元数据异常
- **WHEN** `appTotalCount` 为 null 或不一致、或某页为空/不足 pageSize
- **THEN** 系统 SHALL 安全终止（不超过 MAX_PAGES 上限），MUST NOT 无限循环或页码停滞

### Requirement: 同名标识冲突收敛
系统 SHALL 对同名 identifier 的 USER_PROVIDED 与上游返回值做统一收敛：同值去重为单一规范值，异值 MUST NOT 继续作为无冲突 EXACT 成功，SHALL 标记歧义并返回明确 conflict/missing/candidate 且保留 provenance。

#### Scenario: 同值去重
- **WHEN** USER_PROVIDED 与上游返回同名标识且值相同（envId/appName/monitorItemId/LTS group/stream）
- **THEN** 系统 SHALL 去重为单一规范标识

#### Scenario: 异值冲突
- **WHEN** USER_PROVIDED 与上游返回同名标识但值不同（如 LTS log_group_id）
- **THEN** 系统 SHALL 保留双方 provenance 并标记 ambiguous，且返回 conflict missing capability
