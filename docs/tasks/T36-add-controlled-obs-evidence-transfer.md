# T36 — 受控 OBS 证据转移（add-controlled-obs-evidence-transfer）

## Goal

为 Diagnostic Evidence Package 提供受控 OBS put/head/get：服务端配置目标、服务端生成对象名、显式 approval 后才上传，
附大小/checksum/SSE-KMS/审计；不是通用 OBS 管理工具，禁止任意 key/list/delete/源码凭据/自动生产执行。

## 范围 / 不在范围

- 范围：`agentic-adapter-obs` 端口 + 禁用/真实实现 + 配置 + DTO；`ObsEvidenceService` + `ObsApprovalStore` + 
  `DiagnosticEvidencePackageValidator`；`ObsEvidenceTool`（put/head/get，独立 gate `dpom.obs.transfer-tools-enabled`）；
  审批由可信控制面触发（非 MCP）；`ErrorCode` 新增两个错误码。
- 不在范围：通用 OBS 管理（list/delete/copy/bucket 管理）、approval 持久化、自动上传、真实 OBS SDK E2E（默认跳过）。

## 输入

- openspec/config.yaml、AGENTS.md、docs/architecture.md、openspec/specs/runtime-tool-safety/spec.md。
- 既有 adapter/service/tool 模式（CesMetricsAdapter/Impl、CesMetricsService、CesCreateNotificationMaskTool）。

## 产物清单

- `agentic-adapter-obs/`（pom + ObsEvidenceAdapter/Impl/DisabledObsEvidenceAdapter/ObsClientConfig/ObsProperties + dto/*）。
- `agentic-monitoring`：ObsEvidenceService、ObsApprovalStore。
- `agentic-mcp`：ObsEvidenceTool。
- `agentic-common`：ErrorCode 增补。
- 测试：各 UT + WriteToolRegistrationTest 扩展 + 真实 OBS E2E（默认跳过）。

## 验收标准

- 默认 `enabled=false`：fail-closed 禁用适配器，不连接真实 OBS。
- put/head/get 仅服务端配置 bucket/prefix/endpoint/凭据；对象名服务端生成；无任意 key/list/delete。
- 上传需显式 approval（身份绑定 + TTL），未批准抛 UPLOAD_NOT_APPROVED。
- 大小/checksum/SSE-KMS/审计全部生效；禁止源码/凭据。
- OBS 工具默认不注册，双开关满足才注册。
- `mvn clean verify` + `openspec validate --strict` 通过。

## AI 易错点提醒

- OBS SDK 异常是 `com.obs.services.exception.ObsException`，不要用 v3 的 SdkExceptionMapper。
- SSE-KMS 用 `PutObjectRequest.setSseKmsHeader` + `ServerEncryption.OBS_KMS` + `setKmsKeyId`。
- 对象名拼接前必须白名单校验 serviceCode/investigationId/packageId，禁止 `/`、`..` 路径穿越。
- 日志不打印 AK/SK/token/证据正文；审计不含证据正文。

