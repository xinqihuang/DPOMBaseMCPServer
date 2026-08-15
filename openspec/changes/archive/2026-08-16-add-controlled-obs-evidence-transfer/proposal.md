# Add controlled OBS evidence transfer

## Why

DPOMAgent 生产侧在证据不足以本地定根因时，需要把版本化、限量、脱敏的 Diagnostic Evidence Package
经受控 OBS 通道交给研发侧做最终 RCA。DPOMBaseMCPServer 作为生产区域证据网关，应提供「只针对证据包」的
受控 OBS put/head/get，而不是一个通用 OBS 管理工具——bucket/prefix/region/凭据仅服务端配置，对象名服务端生成，
上传必须显式 approval，并施加大小、checksum、加密与审计约束，禁止源码/凭据/任意 key、list/delete 与自动生产执行。

## What Changes

- 新增 `agentic-adapter-obs` 子模块：`ObsEvidenceAdapter` 端口（put/head/get 三个方法）+ `DisabledObsEvidenceAdapter`
  （默认 fail-closed）+ `ObsEvidenceAdapterImpl`（真实 OBS SDK）+ `ObsClientConfig` + `ObsProperties` + 内部 DTO；
  引入 `com.huaweicloud:esdk-obs-java:3.26.6` 依赖。
- 新增 `agentic-monitoring` 的 `ObsEvidenceService`（校验、对象名生成、approval 校验、大小/checksum/加密/审计编排）
  与 `ObsApprovalStore`（显式审批的进程内记录 + 过期语义）。
- 新增 `agentic-mcp` 的 `ObsEvidenceTool`（put_evidence_package / head_evidence_package / get_evidence_package），
  使用独立 gate `dpom.obs.transfer-tools-enabled`（不复用 write-tools-enabled / action-enabled）。审批不暴露为 MCP 工具，
  由不可被 LLM 调用的可信控制面经 `ObsEvidenceService.approveEvidence` 触发。
- `agentic-common` 新增错误码 `OBS_UNAVAILABLE` 与 `UPLOAD_NOT_APPROVED`（均 retryable=false）。
- 新增 `dpom.obs.*` 配置：enabled / bucket / prefix / endpoint / kms-key-id / max-bytes / approval-ttl-seconds。

## Capabilities

### New Capabilities
- `obs-evidence-transfer`: 针对 Diagnostic Evidence Package 的受控 OBS put/head/get——服务端配置、服务端对象名、
  显式 approval、大小/checksum/加密/审计、禁止任意 key/list/delete/源码凭据/自动生产执行的完整契约。

### Modified Capabilities
（无——OBS 证据转移使用独立 gate `dpom.obs.transfer-tools-enabled`，不修改既有 `runtime-tool-safety` 的写工具边界。）

## Impact

- 模块：新增 `agentic-adapter-obs`（挂到 `agentic-adapter` 聚合父）；`agentic-monitoring`、`agentic-mcp`、`agentic-common` 各新增类。
- 依赖：`com.huaweicloud:esdk-obs-java:3.26.6`（OBS 专用 SDK，凭据走 `HUAWEICLOUD_AK/SK`）。
- 配置：新增 `dpom.obs.*`（默认 `enabled=false`，不连接真实 OBS）。
- 测试：adapter 用 fake/in-memory 实现与 mock ObsClient，默认不连接真实 OBS；service/tool 全 UT；真实 OBS E2E 显式开关默认跳过。

