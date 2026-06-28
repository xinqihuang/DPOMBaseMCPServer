## Why

DPOM Base MCP Server 需要一条从 Codehub 代码 push、经 CodeArts Pipeline 构建打包、推送至 SWR 镜像仓库、再由 Helm 部署到 CCE 的完整交付链路。没有这条链路，工具代码无法以可复现、可回滚的方式上线到"贵阳一"生产环境。本变更交付 CI/CD 与部署的工程基础设施（Dockerfile、CodeArts Pipeline、Helm Chart、构建脚本、部署文档）。

> 注：本变更为**存量回填**——工具已于早期 commit 交付，CI/CD 与部署产物随 T02 一并落地；此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增多阶段 `Dockerfile`：Stage 1 用 `maven:3.9-eclipse-temurin-21` 构建、Stage 2 用 `eclipse-temurin:21-jre-jammy` 运行，产出最小运行镜像；配套 `.dockerignore` 收敛构建上下文。
- 新增 `.cloudbuild/build.yml`（CodeArts Pipeline）：`PRE_BUILD`（checkout）→ `BUILD`（Maven package + 上传 jar 制品）→ `PACKAGE`（docker_build / docker_push 到 SWR）。
- 新增 Helm Chart `helm/dpom-mcp-server/`：`Chart.yaml` / `values.yaml` / `values-prod.yaml` / `templates/{deployment,service,configmap,_helpers.tpl,NOTES.txt}`，暴露业务端口 8080 与 management 端口 8081，liveness/readiness 探针指向 8081。
- 新增镜像构建脚本 `scripts/build-image.sh`（从 `pom.xml` 版本号去 `-SNAPSHOT` 拼 `yyyyMMddHHmmss`（UTC+8）时间戳生成 tag）与冒烟脚本占位 `scripts/smoke/.gitkeep`。
- 部署文档：`README.md` 新增部署一节，含 `helm upgrade --install` 命令示例。

## Capabilities

### New Capabilities

无（基础设施变更，不引入 MCP 工具能力）。

### Modified Capabilities

<!-- 无 -->

## Impact

- 新增/构建产物：`Dockerfile`、`.dockerignore`、`.cloudbuild/build.yml`、`helm/dpom-mcp-server/**`、`scripts/build-image.sh`、`scripts/smoke/.gitkeep`、`README.md`（部署章节）。
- 镜像仓库：`swr.cn-north-9.myhuaweicloud.com/powercloud/DPOMBaseMCPService`，tag 规则 `<版本>_<yyyyMMddHHmmss>`。
- 运行环境：CCE 集群（"贵阳一" cn-southwest-2），单环境；AK/SK 由集群级 Vault 注入到环境变量，不进 Helm values。
- 不涉及任何工具/adapter/service 代码改动；不引入 ArgoCD/GitOps 与多环境 promotion 流水线。
