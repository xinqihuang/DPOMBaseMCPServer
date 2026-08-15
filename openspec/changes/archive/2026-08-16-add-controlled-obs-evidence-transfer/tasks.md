# Tasks

## 1. common 错误码

- [x] 1.1 测试先行：`ErrorCode` 新增 `OBS_UNAVAILABLE` 与 `UPLOAD_NOT_APPROVED`（retryable=false，含 hint）

## 2. agentic-adapter-obs 模块

- [x] 2.1 测试先行：新建 `agentic-adapter-obs` 子模块（pom + 挂聚合父），定义 `ObsEvidenceAdapter` 端口与内部 DTO
- [x] 2.2 测试先行：`DisabledObsEvidenceAdapter`（默认 fail-closed，抛 OBS_UNAVAILABLE，不触碰 OBS SDK）
- [x] 2.3 测试先行：`ObsEvidenceAdapterImpl`（真实 OBS SDK put/head/get + SSE-KMS + ObsException→UpstreamException 映射，mock ObsClient 验证）
- [x] 2.4 `ObsClientConfig` + `ObsProperties`（dpom.obs.* 服务端配置，enabled=true 才建 ObsClient）

## 3. monitoring 编排

- [x] 3.1 测试先行：`ObsApprovalStore`（可信控制面显式 approval 记录 + 身份绑定 + TTL 过期，非 MCP）
- [x] 3.2 测试先行：`ObsEvidenceService`（对象 key 含 checksum、Base64 解码前限长、approval/大小/checksum 校验、证据包结构校验、结构化审计）
- [x] 3.3 测试先行：`DiagnosticEvidencePackageValidator`（合法 ZIP + manifest + 拒绝任意 ZIP/源码/凭据）

## 4. mcp 工具与门控

- [x] 4.1 测试先行：`ObsEvidenceTool`（put/head/get，独立 gate `dpom.obs.transfer-tools-enabled`，不暴露 approve）
- [x] 4.2 测试：默认不注册 OBS 工具；仅 `transfer-tools-enabled` 开启才注册，且不影响 CES 写工具

## 5. 配置与文档

- [x] 5.1 `application.yml` 新增 `dpom.obs.*` 默认配置（enabled=false）
- [x] 5.2 真实 OBS E2E：显式环境开关默认跳过，未配置报 NOT_EXECUTED；get 限流 + 最大读取上限
- [x] 5.3 输出 docs/add-controlled-obs-evidence-transfer-acceptance-report.md 并跑完整验收

