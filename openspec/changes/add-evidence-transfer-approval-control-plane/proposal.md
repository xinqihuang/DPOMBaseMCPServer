## Why

当前 OBS 证据转移的显式 approval 仅存在于进程内（`ObsApprovalStore` 内存 Map），无外部控制面、无认证、无持久化；MCP 面已删除 approve 工具。生产侧 DPOMAgent 需要一个**非 MCP 的、强认证的、持久化的可信控制面**来批准精确 OBS 上传，同时保证 LLM 绝不能自批准（LLM 触达的 MCP 工具面不含 approve）。

## What Changes

- 新增独立内部 REST 控制面（`POST /internal/approvals` 批准、`DELETE /internal/approvals/...` 撤销），不注册 MCP，默认关闭、独立 gate。
- 新增 HMAC 签名认证：`timestamp + nonce + method + path + body hash`，防重放、恒时比较；服务端配置密钥，禁止请求携带 AK/SK；日志不记录 secret/signature/body。
- 审批持久化并绑定 `serviceCode/investigationId/packageId/sha256` 与 `approverRef/reason/expiry`；重启不丢；过期/撤销/身份或 checksum 不符拒绝。
- put 成功后原子消费（并发仅一次有效）；上传失败回滚审批以便重试。
- approve/revoke/consume 成功失败结构化审计 + 低基数指标。
- 限制请求体大小、字段白名单、稳定错误码。

## Capabilities

### New Capabilities

- `approval-control-plane`: 可信控制面 REST API + HMAC 认证 + 持久化审批存储 + 原子消费 + 结构化审计与低基数指标。

### Modified Capabilities

- `obs-evidence-transfer`: `Explicit approval from trusted control plane` 需求变更 —— 审批绑定 sha256、持久化、put 侧原子消费（并发单赢家、失败回滚）。

## Impact

- 模块：`agentic-common`（ErrorCode 新增稳定错误码）、`agentic-monitoring`（ApprovalProperties/ApprovalRecord/ApprovalStore/PersistentApprovalStore/ApprovalService）、`agentic-mcp`（ApprovalControlPlaneController/ApprovalSignatureVerifier/ApprovalNonceCache）。
- 配置：`application.yml` 新增 `dpom.approval.*`。
- 依赖：不新增第三方依赖（JDK HMAC + Jackson + Micrometer 均已在基线内）。
- 安全：生产侧 DPOMAgent 须持有 `dpom.approval.hmac-secret`（服务端配置，可轮换），LLM 侧无任何 approve 入口。
