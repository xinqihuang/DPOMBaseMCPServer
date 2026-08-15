# Design: controlled OBS evidence transfer

## Context

DPOMBaseMCPServer 现为只读监控 MCP Server（CES/AOM/APM/LTS），写工具由 `action-enabled` profile +
`dpom.mcp.write-tools-enabled=true` 双开关门控。本 Change 新增 OBS 证据包受控转移：只针对 Diagnostic Evidence
Package 的 put/head/get，服务端配置目标、服务端生成对象名、显式 approval 后上传，附大小/checksum/SSE-KMS/审计。

## Goals / Non-Goals

**Goals:**
- 新增 `agentic-adapter-obs`：`ObsEvidenceAdapter` 端口 + 禁用实现 + 真实 OBS SDK 实现 + 配置 + 内部 DTO。
- 新增 monitoring `ObsEvidenceService`（校验/对象名/approval/大小/checksum/加密/审计）与 `ObsApprovalStore`。
- 新增 mcp `ObsEvidenceTool`（put/head/get），使用独立 gate `dpom.obs.transfer-tools-enabled`（不复用写工具门控）。
- 默认 `enabled=false`：fail-closed 禁用适配器，不连接真实 OBS。

**Non-Goals:**
- 不做通用 OBS 管理（list/delete/copy/bucket 管理/任意 key）。
- 不持久化 approval（进程内 `ObsApprovalStore`，持久化留待后续 Change）。
- 不自动执行上传：上传必须显式 approve 后由受控动作触发。

## Decisions

### D1 OBS SDK 用 esdk-obs-java 3.26.6
`com.huaweicloud:esdk-obs-java:3.26.6`，主类 `com.obs.services.ObsClient`。构造 `new ObsClient(ak, sk, endpoint)`；
put 走 `putObject(PutObjectRequest)`，head 走 `getObjectMetadata(bucket, key)`，get 走 `getObject(bucket, key)`。
OBS 异常为 `com.obs.services.exception.ObsException`（非 v3 SDK 异常），由 adapter 本地映射为 `UpstreamException`。

### D2 SSE-KMS 服务端加密
`PutObjectRequest.setSseKmsHeader(SseKmsHeader)`，`SseKmsHeader.setEncryption(ServerEncryption.OBS_KMS)` +
`setKmsKeyId(dpom.obs.kms-key-id)`。kms-key-id 仅服务端配置。

### D3 对象名服务端生成（含内容 checksum）
objectKey = `{prefix}/{serviceCode}/{investigationId}/{packageId}/{sha256}.zip`。prefix 仅来自服务端配置，
serviceCode/investigationId/packageId 经白名单校验（不允许路径穿越），sha256 经 64 位 hex 格式校验后进入 key。
put 由内容计算 sha256；head/get 由调用方提供 sha256 后服务端确定性重建同一 key。

### D4 显式 approval 门（仅可信控制面）
`ObsApprovalStore`（进程内 ConcurrentHashMap，带 TTL）记录 `approve(...)`；`putEvidence` 校验「同 identity 存在未过期 approval」，
不接受调用方传 approval 布尔。审批**不暴露为 MCP 工具**——只由不可被 LLM 调用的可信控制面经 `ObsEvidenceService.approveEvidence` 触发。

### D5 错误码
`agentic-common.ErrorCode` 新增 `OBS_UNAVAILABLE`（未启用/无真实适配器）与 `UPLOAD_NOT_APPROVED`（未批准），均
`retryable=false`。大小/checksum/格式违规用现有 `INVALID_PARAM`。OBS SDK 异常按状态码映射到现有 UPSTREAM_*。

### D6 装配门控（独立 gate）
`ObsEvidenceAdapterImpl` 与 `ObsClientConfig` 用 `@ConditionalOnProperty(name="dpom.obs.enabled", havingValue="true")`；
`DisabledObsEvidenceAdapter` 用 `matchIfMissing=true`。`ObsEvidenceTool` 用独立 gate
`@ConditionalOnProperty(prefix="dpom.obs", name="transfer-tools-enabled", havingValue="true")`，
绝不复用 `write-tools-enabled` / `action-enabled`，避免同时暴露 CES 写工具。

### D7 证据包结构校验
`DiagnosticEvidencePackageValidator`：合法 ZIP + 唯一 `manifest.json`（camelCase：schemaVersion/packageId/service/release/commit
校验，与 DPOMAgent PackageSerializer 产物一致）+ `checksums.json`；条目集合严格等于 manifest + checksums + manifest 声明的
payload 条目，每条目 checksum/size 与实际内容一致；拒绝重复条目、路径穿越（含反斜杠 / Windows 盘符 / UNC）、源码扩展名与凭据
标记；`security/redaction-report.json` 不豁免凭据扫描，做严格 JSON schema（仅 section→非负整数计数）。

### D8 结构化审计 + 读取限流 + 有界读取 + Base64 限长
put/head/get/approve 的成功与失败都写结构化审计（eventType/result/errorCode/身份/时间戳，含 RuntimeException→INTERNAL）。
get 经 Resilience4j `obs-transfer` 限流，并做有界读取（contentLength 预检 + readNBytes(maxBytes+1)），contentLength 缺失或
误报时仍按实际读取量拒绝，绝不 readAllBytes。Base64 解码前先按 `maxBytes*4/3+8` 限长。

## Risks / Trade-offs

- [OBS SDK 异常类型与 v3 不同] → adapter 本地映射 ObsException，不复用 v3 SdkExceptionMapper。
- [esdk-obs-java 传递依赖较重] → 仅真实实现类引用；禁用适配器与默认测试不触碰 SDK。
- [approval 进程内存储重启丢失] → 显式标注非持久化，持久化留待后续 Change；审计日志兜底。
- [真实 OBS E2E 需凭据] → 默认跳过；显式环境开关 + 未配置时 NOT_EXECUTED。

