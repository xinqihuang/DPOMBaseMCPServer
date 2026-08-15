# Tasks

## 1. common 错误码

- [x] 1.1 `ErrorCode` 新增 `APPROVAL_AUTH_FAILED`、`APPROVAL_NOT_FOUND`（retryable=false，含 hint）

## 2. monitoring 审批域

- [x] 2.1 `ApprovalProperties`（`dpom.approval.*`：enabled/hmac-secret/timestamp-tolerance-seconds/approval-ttl-seconds/store-file/max-body-bytes/nonce-cache-size）
- [x] 2.2 `ApprovalRecord` 记录（身份 + sha256 + approverRef/reason/expiresAt/createdAt）
- [x] 2.3 `ApprovalStore` 接口 + `PersistentApprovalStore`（文件 + 原子落盘，approve/revoke/consume/restore/isApproved）
- [x] 2.4 `ApprovalService`（approve/revoke/consume/restore + 校验 + 结构化审计 + 低基数指标）
- [x] 2.5 `ObsEvidenceService.putEvidence` 改为原子消费 + 失败回滚

## 3. mcp 控制面

- [x] 3.1 `ApprovalNonceCache`（有界 + TTL 防重放）
- [x] 3.2 `ApprovalSignatureVerifier`（HMAC-SHA256 + 恒时比较 + timestamp 窗口 + 拒绝 AK/SK）
- [x] 3.3 `ApprovalControlPlaneController`（POST/DELETE，`@ConditionalOnProperty(dpom.approval.enabled)`，非 MCP）

## 4. 配置与测试

- [x] 4.1 `application.yml` 新增 `dpom.approval.*`（enabled=false）
- [x] 4.2 测试：认证伪造/过期/重放、并发单赢家、重启持久化、MCP 面无 approve、日志不泄密、字段白名单/body 上限
- [x] 4.3 输出 docs/add-evidence-transfer-approval-control-plane-acceptance-report.md 并跑完整验收
