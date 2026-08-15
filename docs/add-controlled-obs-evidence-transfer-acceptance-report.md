# add-controlled-obs-evidence-transfer 验收报告

日期：2026-08-15
状态：完成（Change 12/12 task，含二轮/三轮评审整改，未归档，等待独立验收）

## 1. 结果总览

- `openspec validate add-controlled-obs-evidence-transfer --strict` → valid。
- `mvn clean verify` → BUILD SUCCESS：**391 tests，0 failures，0 errors，0 skipped**，checkstyle 0 violations。
- 分模块（fresh surefire 汇总）：common 36 · ces 26 · aom 39 · apm 32 · lts 12 · obs 11 · monitoring 119 · mcp 116。

## 2. OBS SDK 与 transport

- 固定版本：`com.huaweicloud:esdk-obs-java:3.26.6`（Maven Central 最新 release）。
- 主类 `com.obs.services.ObsClient`：put `putObject(PutObjectRequest)`、head `getObjectMetadata`、get `getObject`；
  凭据复用 `HUAWEICLOUD_AK/SK`。
- SSE-KMS：`SseKmsHeader` + `ServerEncryption.OBS_KMS` + `setKmsKeyId(dpom.obs.kms-key-id)`。
- `ObsException`（非 v3 SDK 异常）在 adapter 本地映射为 `UpstreamException`。

## 3. 受控 put/head/get 契约（一轮 5 项整改）

1. **审批只在可信控制面**：已删除 MCP 的 `approve_evidence_upload`；审批仅由不可被 LLM 调用的可信控制面
   经 `ObsEvidenceService.approveEvidence` 触发（绑定身份 + TTL），未批准上传抛 `UPLOAD_NOT_APPROVED`。
2. **独立 gate**：OBS 工具使用 `dpom.obs.transfer-tools-enabled`（独立于 `write-tools-enabled`/`action-enabled`），
   启用证据转移不会同时暴露 CES 写工具。
3. **对象 key 含内容 checksum**：`{prefix}/{serviceCode}/{investigationId}/{packageId}/{sha256}.zip`；
   head/get 由调用方提供 sha256（校验 64 hex）后服务端确定性重建同一 key。
4. **证据包解析与校验**：`DiagnosticEvidencePackageValidator` 要求合法 ZIP + `manifest.json`（schema/身份匹配），
   拒绝任意 ZIP、源码扩展名与凭据标记。
5. **审计 + get 限流 + 上限 + Base64 限长**：put/head/get/approve 的成功与失败均写结构化审计；
   get 经 Resilience4j `obs-transfer` 限流并做有界读取；Base64 解码前先按 `maxBytes*4/3+8` 限长。

## 4. 二轮评审整改（5 项）

1. **真实 PackageSerializer 跨仓契约测试**：`PackageSerializerContractTest` 用 DPOMAgent 真实产物结构
   （camelCase manifest + checksums.json + section 文件 + security/redaction-report.json）；校验器按 camelCase 读取。
2. **ZIP 校验 fail-closed**：拒绝重复 manifest、路径穿越；限制条目数与累计解压字节；完整扫描每个条目（无 64KiB 截断）、
   UTF-8 确定性；测试覆盖凭据越过 64KiB、zip bomb/超限、重复 manifest、路径穿越。
3. **全量失败审计**：`ObsEvidenceService.executeAudited` 捕获 `SmartomException` 与 `RuntimeException`（→INTERNAL），
   参数/审批/Base64/大小/checksum/证据包校验失败均写审计。
4. **get 有界读取 + 关闭流**：contentLength 预检 + `readNBytes(maxBytes+1)` 有界读取 + try-with-resources 关闭流；
   contentLength 缺失或误报时仍按实际读取量拒绝，绝不 `readAllBytes`。
5. **有效 MCP 暴露面测试**：`ObsMcpExposureTest` 仅开 OBS gate 时断言 put/head/get 存在，CES create/delete 与 approve 不存在。

## 5. 三轮评审整改（4 项）

1. **真实 fixture（非手工拼 ZIP）**：`PackageSerializerContractTest` 改为读取由 DPOMAgent 真实 `PackageSerializer`
   生成的固定 fixture（`agentic-monitoring/src/test/resources/obs-fixtures/dpomagent-package.zip`），按结构契约
   （camelCase manifest 逐字段 + 校验器接受）做 drift 校验，不依赖 ZIP 时间戳等非契约元数据。可重复生成流程见
   `scripts/regenerate-obs-contract-fixture.ps1` + `scripts/ObsContractFixtureGenerator.java`（编译 DPOMAgent
   agent-core → 调真实 PackageSerializer 序列化 → 写 fixture）。
2. **redaction-report 严格 JSON schema（不再整体豁免）**：`security/redaction-report.json` 仅允许
   `section → 非负整数计数`，拒绝文本值/额外类型，且仍执行凭据标记扫描；测试覆盖文本值、凭据 key（`access_key`）、
   藏 AK/SK/password 值三类拒绝。
3. **路径校验 + 条目 allow-list + 完整性**：拒绝反斜杠穿越、Windows 盘符（`C:`）、UNC（`\\server`）、重复非 manifest
   条目；规范化后条目集合严格等于 `manifest.json + checksums.json + manifest 声明的 payload 条目`，且每条目
   checksum/size 与实际内容一致（防止合法 manifest 包装任意 ZIP）。
4. **design.md camelCase 统一**：D7/D8 由 `schema_version/package_id/service_code` 统一为真实 camelCase 契约
   （`schemaVersion/packageId/service/release/commit`），并将 get 描述改为有界读取（max+1）。
5. **fixture 生成脚本收口（P2）**：`regenerate-obs-contract-fixture.ps1` 将 classpath/javac 输出写入系统临时目录并在
   finally 清理（不再写 `DPOMAgent/agent-core/cp.txt`，不污染 DPOMAgent 工作树）；工具解析优先
   JAVA_HOME/MAVEN_HOME/M2_HOME、其次 PATH，缺失时明确报错；脚本为纯 ASCII 避免编码问题。

## 6. 边界保持

- 默认 `dpom.obs.enabled=false`：`DisabledObsEvidenceAdapter` fail-closed 抛 `OBS_UNAVAILABLE`，不连真实 OBS。
- 无 list/delete/copy/bucket 管理、无任意 object key；禁止源码/凭据；禁止自动生产执行。

## 7. 真实 OBS E2E 状态

- 真实 OBS E2E 需 `dpom.obs.enabled=true` + 真实 bucket/endpoint/KMS/凭据，默认不执行（未配置）。
- 本机未连接真实 OBS；`ObsEvidenceAdapterImpl` 用 mock `ObsClient` 做单元验证。

## 8. 主要修改文件

- `agentic-common`：`ErrorCode` 新增 `OBS_UNAVAILABLE`、`UPLOAD_NOT_APPROVED`。
- 新增 `agentic-adapter-obs`：`ObsEvidenceAdapter`/`ObsEvidenceAdapterImpl`（get 限流 + 有界读取）/
  `DisabledObsEvidenceAdapter`/`ObsClientConfig`/`ObsProperties` + 4 DTO。
- `agentic-monitoring`：`ObsEvidenceService`、`ObsApprovalStore`、`DiagnosticEvidencePackageValidator`（严格结构校验）；
  `PackageSerializerContractTest`（真实 fixture 契约）+ `obs-fixtures/dpomagent-package.zip`。
- `agentic-mcp`：`ObsEvidenceTool`（put/head/get，独立 gate）；`ObsMcpExposureTest`。
- `scripts/`：`ObsContractFixtureGenerator.java` + `regenerate-obs-contract-fixture.ps1`（fixture 可重复生成/校验）。
- 配置：`application.yml` 新增 `dpom.obs.*` 与 `obs-transfer` 限流实例。

## 9. 已知风险

- approval 为进程内存储，重启丢失；持久化留待后续 Change，结构化审计兜底。
- 凭据/源码扫描为模式匹配（非语义解析），存在绕过可能；已按 spec 落契约测试，升级需同步。
- `esdk-obs-java` 传递依赖较重，仅真实实现类引用；默认测试不触碰。
- 真实 OBS E2E 未执行；正式部署需离线校验 SDK 版本与 KMS 配置后复验。

## 10. 验收命令复现

- `openspec validate add-controlled-obs-evidence-transfer --strict`
- `mvn clean verify`（JDK 21 + Maven 3.9.16）
- fixture 再生成：`pwsh scripts/regenerate-obs-contract-fixture.ps1`
