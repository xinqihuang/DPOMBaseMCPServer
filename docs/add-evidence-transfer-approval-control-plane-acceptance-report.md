# add-evidence-transfer-approval-control-plane 验收报告

日期：2026-08-16
状态：完成（Change 12/12 task，含安全加固整改，未归档，等待独立验收）

## 1. 结果总览

- `openspec validate add-evidence-transfer-approval-control-plane --strict` → valid。
- `mvn clean verify` → BUILD SUCCESS：**429 tests，0 failures，0 errors，0 skipped**，checkstyle 0 violations。
- 分模块（fresh surefire 汇总）：common 36 · ces 26 · aom 39 · apm 32 · lts 12 · obs 11 · monitoring 134 · mcp 139。

## 2. 架构

- **内部 REST 控制面（非 MCP）**：`ApprovalControlPlaneController`（`POST /internal/approvals` 批准、
  `DELETE /internal/approvals/{serviceCode}/{investigationId}/{packageId}/{sha256}` 撤销）。不实现 `McpTool`、
  不标注 `@Tool`，`@ConditionalOnProperty(dpom.approval.enabled=true)` 独立 gate，默认关闭（bean 不存在 → 404 fail-closed）。
- **HMAC 强认证（可轮换）**：`ApprovalSignatureVerifier` 校验 `timestamp + nonce + method + path + body hash`，
  `HmacSHA256` + `MessageDigest.isEqual` 恒时比较；主密钥 + 上一代密钥（`hmac-secret` + `hmac-previous-secret`）平滑轮换；
  `ApprovalNonceCache` 有界 + 持久化防重放；密钥仅服务端配置，主密钥 < 32 字符 fail-closed；请求带 `Authorization`/
  `X-Access-Key`/`X-Secret-Key` 头直接拒绝。
- **持久化审批（fail-closed）**：`PersistentApprovalStore`（文件 + 临时文件 + `ATOMIC_MOVE`），绑定
  serviceCode/investigationId/packageId/sha256 + approverRef/reason/expiresAt/createdAt；持久化失败回滚内存并抛
  `APPROVAL_STORAGE_ERROR`；启动文件损坏/不可读 fail-closed。
- **原子消费**：`ApprovalService.consume` 原子移除（并发单赢家）；`ObsEvidenceService.putEvidence` 校验后先消费，失败回滚。
- **审计 + 指标（净化）**：approve/revoke/consume/restore 成功失败结构化审计，未验证 identity/sha 以 INVALID 占位；Micrometer
  低基数计数器 `dpom.approval.<action>{result=success|failure}` 与 `dpom.approval.auth.failure`。

## 3. 需求落实

1. **非 MCP 控制面 / 默认关闭 / 独立 gate**：控制器非 McpTool、独立 gate；`controlPlaneNotAnMcpTool` 断言。
2. **强认证 fail-closed**：HMAC + 防重放 + 恒时比较 + 服务端密钥 + 拒绝 AK/SK；`ApprovalSignatureVerifierTest`（伪造/篡改/
   过期/重放/空密钥/弱密钥/轮换）。
3. **持久化 + 绑定**：文件落盘、重启不丢、过期/撤销/checksum 不符拒绝。
4. **原子消费 / 并发单赢家**：`consumeOnceThenNull` + `concurrentConsumeSingleWinner` + 失败回滚。
5. **审计 + 指标**：`ApprovalAuditLogLeakTest` 验证日志不含 body/secret/signature 且非法身份被净化。
6. **body 限长 / 字段白名单 / 稳定错误码**：`max-body-bytes` + `FAIL_ON_UNKNOWN_PROPERTIES`；错误码
   `APPROVAL_AUTH_FAILED`(401)/`APPROVAL_NOT_FOUND`(404)/`APPROVAL_STORAGE_ERROR`(503)/`INVALID_PARAM`(400)。
7. **无通用 OBS / 无自动生产 / 无任意 shell**：仅 approve/revoke 两端点。
8. **单实例 Java Web 边界**：文件 + 原子落盘，不引入 Redis/DB/分布式锁。

## 4. 安全加固（P1/P2 整改）

1. **防重放修正**：nonce 有效终点改为 `(timestamp + tolerance)` 覆盖签名实际有效窗口（不再用 now + tolerance）；
   nonce 持久化到 `dpom.approval.nonce-store-file`，重启窗口内仍拒绝重放。测试：`nonceValidUntilCoversSignatureWindow`
   （未来时间戳）与 `nonceSurvivesRestart`。
2. **缓存有界 + 格式限制**：达到 `nonce-cache-size` 且均未过期时 fail-closed 拒绝；nonce 限 `[a-zA-Z0-9_-]{16,128}`。
   测试：`fullCapacityFailClosed`、`invalidNonceRejected`。
3. **持久化 fail-closed**：approve/revoke/consume/restore 持久化失败回滚内存并抛 `APPROVAL_STORAGE_ERROR`；启动文件损坏/不可读
   fail-closed 拒绝启动（不再静默空库）。测试：`diskFailureRollsBackApproval`、`corruptedStoreFileFailsClosed`。
4. **HMAC 可轮换 + 密钥强度**：主密钥 + 上一代密钥（`hmac-previous-secret`）；主密钥 < 32 字符 fail-closed。
   测试：`previousKeyAccepted`、`oldKeyRejectedAfterRotation`、`weakSecretRejected`。
5. **稳定错误消息 / 不回显内部异常 / 净化审计 / store 日志脱敏**：认证失败统一 401 `authentication failed`；未分类异常统一 500
   `internal error`；审计前净化未验证 identity/sha；store/nonce 日志不含绝对路径、异常 message/stack 或密钥。
   测试：`authFailureMappedToUnauthorized`/`genericExceptionMappedTo500`（统一消息）、`invalidIdentitySanitizedInAudit`。

## 5. 主要修改文件

- `agentic-common`：`ErrorCode` 新增 `APPROVAL_AUTH_FAILED`、`APPROVAL_NOT_FOUND`、`APPROVAL_STORAGE_ERROR`。
- `agentic-monitoring/approval`（新）：`ApprovalProperties`/`ApprovalConfig`/`ApprovalRecord`/`ApprovalStore`/
  `PersistentApprovalStore`（fail-closed）/`ApprovalService`（净化审计）+ 3 个测试。
- `agentic-monitoring/obs`：`ObsEvidenceService` 原子消费 + 回滚 + 净化审计；删除 `ObsApprovalStore`。
- `agentic-mcp/approval`（新）：`ApprovalNonceCache`（有界持久化）/`ApprovalSignatureVerifier`（轮换+强度）/
  `ApprovalControlPlaneController`（稳定消息）+ 3 个 DTO + 3 个测试。
- `agentic-mcp`：`application.yml` 新增 `dpom.approval.*`（含 previous-secret / nonce-store-file）。
- `agentic-monitoring/pom.xml`：新增 `micrometer-core`。

## 6. 已知风险

- 单实例文件持久化：多实例需共享存储（留待后续 Change）。
- HMAC 密钥轮换：服务端配置 + 滚动重启；密钥不回显、不进日志。
- 上传失败回滚后极短窗口可能被重放：sha256 幂等键 + 审批重放审计兜底。
- nonce 持久化逐请求落盘：控制面低频，性能可接受；高并发场景可换批处理/共享存储。

## 7. 验收命令复现

- `openspec validate add-evidence-transfer-approval-control-plane --strict`
- `mvn clean verify`（JDK 21 + Maven 3.9.16）
