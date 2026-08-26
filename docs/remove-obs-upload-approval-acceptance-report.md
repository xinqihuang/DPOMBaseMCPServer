# 自动 OBS 证据存储验收报告

## 结论

`remove-obs-upload-approval` 已完成：逐证据包审批控制面和运行时依赖已移除；自动诊断证据存储默认关闭，
OBS endpoint、bucket、prefix、service code 与可选 KMS key 均由部署环境提供。验证桶未进入生产代码或部署默认值。

## 自动化验收

- 基线提交：`10721f8459896f42f250705392a67e16efe61b37`
- 全仓命令：`D:\apache-maven-3.9.16\bin\mvn.cmd clean verify`
- 结果：13 个 reactor project 全部成功，Checkstyle 0 violation。
- Surefire：114 个测试报告，465 tests，0 failures，0 errors，1 skipped。
- 唯一 skip 是默认关闭的真实 OBS E2E；它已在独立授权运行中显式启用并通过。
- OpenSpec：`openspec validate remove-obs-upload-approval --strict` 通过。
- `git diff --check` 通过。

## 真实 OBS 验证

真实测试只通过进程环境注入 `RUN_OBS_E2E`、AK/SK、endpoint、bucket 和 prefix；测试结束后清除这些变量。
未将凭证、测试 endpoint 或测试 bucket 写入代码和部署默认值。

- 测试：`ObsEvidenceAdapterRealObsE2ETest`
- 结果：1 test，0 failures，0 errors，0 skipped。
- 校验：PUT 成功；HEAD/GET 的 54 字节、ETag、对象正文和 SHA-256 用户元数据一致。
- 保留对象：
  `obs://obs-perf168w-public/evidence/verification/1787717254522-d270796a1193c22472a910c29e3134c3167a0598cab3a671136e4cabe6d6e133.json`

## 安全与配置扫描

- 当前 `agentic-mcp/src` 与 `agentic-monitoring/src` 中审批 controller、route、property、service 匹配数：0。
- 生产 Java、application 配置和 Helm defaults 中测试桶匹配数：0。
- Git diff 通用硬编码 AK/SK 模式匹配数：0。
- AK/SK 保持只由 `HUAWEICLOUD_AK` / `HUAWEICLOUD_SK` 注入。
