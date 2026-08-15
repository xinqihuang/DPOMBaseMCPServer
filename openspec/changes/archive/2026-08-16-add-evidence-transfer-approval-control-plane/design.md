# Design: evidence transfer approval control plane

## Context

DPOMBaseMCPServer 现有 OBS 证据转移的 approval 仅为进程内内存 Map（ObsApprovalStore），无外部控制面、无认证、无持久化。
本 Change 新增一个**非 MCP 的、强认证的、持久化的内部 REST 控制面**，供生产侧 DPOMAgent 批准/撤销精确 OBS 上传，并把 put
侧改为原子消费审批；LLM 触达的 MCP 面不含 approve。

## Goals / Non-Goals

**Goals:**
- 内部 REST 控制面（approve/revoke），默认关闭、独立 gate，不注册 MCP。
- 强认证 fail-closed：HMAC 签名（timestamp+nonce+method+path+body hash），防重放、恒时比较，服务端密钥，禁止请求携带 AK/SK。
- 审批持久化并绑定 serviceCode/investigationId/packageId/sha256 + approverRef/reason/expiry；重启不丢；过期/撤销/身份或 checksum 不符拒绝。
- put 成功后原子消费（并发单赢家、失败回滚）。
- approve/revoke/consume 结构化审计 + 低基数指标；限制 body、字段白名单、稳定错误码。

**Non-Goals:**
- 不做通用 OBS 管理（list/delete/copy/bucket/任意 key）。
- 不做多实例分布式锁/共享存储（保持单实例 Java Web 边界）。
- 不自动执行生产动作、不执行任意命令。
- 不引入第三方依赖（不新增 Redis/DB/外部鉴权服务）。

## Decisions

### D1 REST 表面与独立 gate
`ApprovalControlPlaneController` 为 `@RestController`（不实现 `McpTool`、不标注 `@Tool`，故不被 MCP 工具回调收集）。
路由：`POST /internal/approvals`（批准）、`DELETE /internal/approvals/{serviceCode}/{investigationId}/{packageId}/{sha256}`
（撤销）。用 `@ConditionalOnProperty(name="dpom.approval.enabled", havingValue="true")` 独立 gate，默认关闭（bean 不存在 → 路由 404 fail-closed）。

### D2 HMAC 认证（可轮换）
请求头：`X-Approval-Timestamp`（Unix 秒）、`X-Approval-Nonce`（随机）、`X-Approval-Signature`（hex）。
签名串 = `timestamp + "\n" + nonce + "\n" + METHOD + "\n" + path + "\n" + sha256Hex(body)`。
`ApprovalSignatureVerifier` 用 `HmacSHA256` 重算并用 `MessageDigest.isEqual` 恒时比较，支持主密钥 + 上一代密钥
（`hmac-secret` + `hmac-previous-secret`）平滑轮换，主密钥强度 < 32 字符 fail-closed；`ApprovalNonceCache`（有界 + 持久化）
记录已见 nonce 防重放，nonce 有效终点覆盖签名实际窗口（timestamp + tolerance 而非 now + tolerance），重启后窗口内仍拒绝重放；
timestamp 需落在窗口内；密钥仅来自服务端配置，为空 fail-closed。

### D3 禁止请求携带 AK/SK
认证只接受 HMAC 头，不接受任何客户端密钥对。请求若含 `Authorization`、`X-Access-Key`、`X-Secret-Key` 头，或 body 出现
`accessKey`/`secretKey` 字段，直接拒绝（APPROVAL_AUTH_FAILED）。

### D4 持久化审批存储（fail-closed）
`ApprovalRecord`（serviceCode/investigationId/packageId/sha256/approverRef/reason/expiresAt/createdAt）。`PersistentApprovalStore`
以进程内 `ConcurrentHashMap` 为真相源，键 = `serviceCode|investigationId|packageId|sha256`；每次变更全量序列化为 JSON 写临时文件
再 `ATOMIC_MOVE` 到 `dpom.approval.store-file`；持久化失败回滚内存并抛 `APPROVAL_STORAGE_ERROR`（不 fail-open）；启动时文件
损坏/不可读则 fail-closed 拒绝启动。日志不含绝对路径、异常 message/stack 或密钥。

### D5 生命周期
`approve`（upsert，覆盖同键旧审批）、`revoke`（移除，不存在即 APPROVAL_NOT_FOUND）、`consume`（原子移除并返回记录，过期视为
不存在并清理）、`restore`（上传失败回滚，`putIfAbsent` 不覆盖更新审批）。所有读取都校验 sha256 与过期时间。

### D6 原子消费（put 侧）
`ObsEvidenceService.putEvidence` 在校验通过后先 `approvalService.consume(...)`（原子移除，并发单赢家）；消费失败抛
UPLOAD_NOT_APPROVED；随后上传，上传失败则 `approvalService.restore(...)` 回滚以便重试；成功即保持已消费（一次有效）。

### D7 审计与指标（净化）
`ApprovalService` 对 approve/revoke/consume 的成功失败统一写结构化审计（eventType/result/errorCode/身份/sha256），绝不记录
secret/signature/body/reason；审计前净化未验证 identity/sha（非法值以 INVALID 占位，防日志注入）。Micrometer 计数器
`dpom.approval.<action>.success|.failure` 与 `dpom.approval.auth.failure`，无高基数标签。

### D8 错误码与边界（稳定消息）
`ErrorCode` 新增 `APPROVAL_AUTH_FAILED`（401）、`APPROVAL_NOT_FOUND`（404）、`APPROVAL_STORAGE_ERROR`（503）；字段校验复用
`INVALID_PARAM`。认证失败统一 401 稳定消息（不区分失败原因）；未分类异常统一 500 稳定消息（不回显内部异常）。body 上限
`max-body-bytes`（默认 4096），字段白名单，nonce 格式/长度受限；不暴露 list/delete/copy、不执行命令、不自动生产执行。

## Risks / Trade-offs

- [单实例持久化] → 文件 + 原子 rename，仅单实例；多实例需共享存储（留待后续 Change）。
- [HMAC 密钥轮换] → 服务端配置 + 滚动重启；密钥不回显、不进日志。
- [回滚窗口] → 上传失败 restore 后极短窗口内可能被重放，但 sha256 幂等键 + 审批重放审计兜底。
- [进程内 nonce 缓存] → 重启后丢失，配合 timestamp 窗口仍能兜底重放风险。
