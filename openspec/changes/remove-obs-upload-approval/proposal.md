## Why

诊断智能体已经能够从华为云采集证据，但采集结果没有进入 OBS，导致调查报告缺少可重建的受控 Artifact。
当前逐包人工审批又阻断了自动诊断链路，因此需要在保持服务端固定目标和严格数据边界的前提下取消该卡点。

## What Changes

- **BREAKING**：OBS 证据上传不再要求逐包人工 approval，可信控制面不再承担 OBS 上传批准/撤销职责。
- 将诊断证据的 `BoundedEvidenceArtifactStore` 接入现有华为云 OBS SDK adapter，上传成功后返回稳定的
  `sourceRef`、SHA-256、对象大小和 OBS 对象身份。
- OBS bucket、endpoint 与 prefix 仅从服务端配置读取，调用方不能提供任意 bucket、prefix 或 object key；
  `obs-perf168w-public/evidence` 仅作为本次真实 E2E 的运行参数，不进入部署默认值或业务代码。
- 保留默认关闭、大小限制、checksum、SSE-KMS、限流、异常映射、无 list/delete/copy 和敏感日志保护。
- 增加真实 OBS 显式 E2E 验证，凭据仅从 `HUAWEICLOUD_AK/HUAWEICLOUD_SK` 环境变量读取；验证对象写入
  `evidence/` 前缀并通过 HEAD/GET 校验内容、摘要、ETag 与大小。

## Capabilities

### New Capabilities

- `diagnostic-evidence-obs-storage`: 将有界诊断证据序列化并通过现有 OBS SDK adapter 固化为可引用 Artifact。

### Modified Capabilities

- `obs-evidence-transfer`: 移除逐包 approval 前置条件，同时保持服务端固定目标、校验、加密和失败关闭边界。
- `approval-control-plane`: 移除仅用于 OBS 上传的 approve/revoke 行为及其上传消费耦合。

## Impact

- 受影响模块：`agentic-adapter-obs`、`agentic-monitoring`、`agentic-mcp` 组合配置与 Helm 配置。
- 受影响接口：OBS put 编排不再返回 `UPLOAD_NOT_APPROVED`；现有 approval 控制面将退出 OBS 上传路径。
- 外部系统：各环境自行配置华为云 OBS bucket、endpoint 与 prefix；本次 E2E 使用
  `obs-perf168w-public/evidence`。
- 安全：AK/SK 不进入源码、OpenSpec、配置文件、测试报告、命令输出或日志，仅在验证进程环境中使用。
