# T39 — 自动 OBS 诊断证据存储（remove-obs-upload-approval）

## Goal

复用现有华为云 OBS Java SDK adapter，将调查运行时接受的有界诊断证据自动存储到环境配置的 OBS
bucket/prefix，返回内容寻址的受控 Artifact 引用；取消逐包人工审批，但保留默认关闭和严格写入边界。

## 范围 / 不在范围

- 范围：扩展内部 OBS put DTO；支持可选 KMS key 的 SSE-KMS；新增 OBS-backed
  `BoundedEvidenceArtifactStore`；移除 put 的 approval 依赖；配置、Helm、测试和真实 OBS E2E。
- bucket、endpoint、prefix、serviceCode 均为环境级服务端配置，不得硬编码测试桶，不得由 MCP 调用方覆盖。
- 本次授权 E2E 运行参数为 bucket `obs-perf168w-public`、prefix `evidence`；这些值不得进入生产默认值。
- 不在范围：OBS list/delete/copy、bucket 管理、任意 object key、任意文件上传、证据正文日志、自动删除
  E2E 对象、生产默认启用。

## 输入

- `openspec/changes/remove-obs-upload-approval/` 下的 proposal、delta specs、design 与 tasks。
- 既有 `agentic-adapter-obs`、`ObsEvidenceService`、`CorrelatedEvidencePortAdapter` 与
  `BoundedEvidenceArtifactStore`。
- 基线 commit：`10721f8459896f42f250705392a67e16efe61b37`；实现开始前仅新建本 change 的 OpenSpec 文件。

## 产物清单

- `agentic-adapter-obs`：通用有界内容类型/摘要元数据映射、可选 KMS key、真实 E2E。
- `agentic-monitoring`：确定性 JSON 序列化、OBS Artifact store、无审批 put 编排与组合测试。
- `agentic-mcp` / Helm：默认关闭、环境化 bucket/endpoint/prefix/serviceCode 配置与启动校验。
- 审批控制面退出 OBS 上传链路；兼容期历史审批文件不删除。
- `docs/remove-obs-upload-approval-acceptance-report.md`。

## 验收标准

- 有界证据先成功写 OBS，再产生 `EvidenceRecord`；失败不得生成悬空引用。
- object key 为 `{prefix}/{serviceCode}/{investigationId}/{evidenceType}/{sha256}.json`，所有段均校验。
- 空 KMS key 时仍发送 SSE-KMS，仅省略 key ID，使用 OBS 默认主密钥。
- 无人工 approval；仍无 list/delete/copy 和调用方自选 bucket/prefix/key。
- 默认关闭；启用但 endpoint/bucket/prefix 缺失时失败关闭且不泄露凭据。
- 真实 E2E 通过环境变量传入目标和 AK/SK，完成 PUT/HEAD/GET 并保留验证对象。
- 聚焦测试、`mvn verify`、架构扫描和 `openspec validate remove-obs-upload-approval --strict` 通过。

## 安全边界

- AK/SK 只从 `HUAWEICLOUD_AK/HUAWEICLOUD_SK` 读取，不进入源码、配置、报告、Git 或日志。
- 日志仅记录结果、大小、耗时、稳定错误码和上游 request ID，不记录证据正文或任意异常正文。
- OBS 写权限应通过每套环境的 IAM 限制到配置 bucket/prefix。

## AI 易错点提醒

- OBS 使用 `com.huaweicloud:esdk-obs-java`，异常为 `com.obs.services.exception.ObsException`。
- `kmsKeyId` 为空不代表关闭加密；仍设置 `ServerEncryption.OBS_KMS`，只不调用 `setKmsKeyId`。
- 不要把 `obs-perf168w-public`、`evidence` 或 cn-north endpoint 写进生产默认配置。
- 所有文件编辑使用增量修改，保留用户工作；禁止输出或提交 AK/SK。
