# T38 — 统一资源发现与标识关联（add-unified-resource-discovery）

## 目标

为诊断 Agent 提供确定性的只读资源发现与标识关联链，避免在 APM/AOM/CES/LTS/CCE 之间猜 businessId、envId、
instanceId、inventoryId、ECS instance_id、clusterId、日志组/流。返回带 provenance 与歧义标注的规范化 ResourceContext。

## 范围

- `discover_resource_context`：接受已知锚点（region、IP、service/app 名、clusterId、namespace、pod/workload、
  APM business/env/instance/alarm 等任意受控组合），返回规范化 ResourceContext。
- `resolve_resource_candidates`：无法唯一映射时返回候选、来源、match type 和缺失的下一步标识，绝不静默选第一个。
- 输出字段至少覆盖 project/region/EPS、CCE cluster/namespace/workload/pod/node、ECS instance、APM business/app/env/
  instance/monitor item、AOM inventory、LTS group/stream，每个字段标注 source_tool/source_api、observed_at、
  exact/derived、ambiguity。
- 复用现有只读 discovery 工具和 adapter；禁止在 monitoring/mcp 层直接调用华为云 SDK。
- `search_apm_application` 按真实分页字段 `appTotalCount` 遍历全部页，杜绝目标落在后续页时的假阴性；设安全上限并防止页码不前进。
- 同名标识做统一收敛：USER_PROVIDED 与上游同值去重、异值标记冲突并保留 provenance。
- 无法完成的映射（CCE 节点→ECS、APM→LTS 等）建模为 missing capability，不用名称模糊匹配冒充确定关系。

## 不在范围

- 不新增写工具、不自动修复、不执行命令、不上传数据、不输出凭据、不引入 RAG/向量库。
- 不直接调用华为云 SDK 于 monitoring/mcp 层（必须走 adapter）。

## 验收标准

- 从 APM alarm（businessId/envId/instanceId/IP）得到可用于趋势查询的确定性上下文。
- 从 AOM CCE FailedScheduling（clusterId/namespace/pod）得到节点/ECS 候选，缺标识时明确下一步。
- 从 LTS 告警中的 group/stream/traceId 关联服务/APM 候选。
- 同名服务、跨 region、跨 EPS、多候选不误关联；同名标识同值去重、异值冲突不静默。
- 分页遍历全部结果页（目标在第 2 页仍能收敛），分页元数据异常安全终止。
- 返回值可直接供后续 APM/CES/AOM/LTS 查询工具使用，不需模型重新拼参数。
- `mvn clean verify` + OpenSpec strict validate 通过 + 真实只读 smoke test + 中文验收报告。
