## Context

存量基础设施回填。原始任务卡：`docs/tasks/T02-cicd-deploy.md`（状态 Ready，估时 0.5d，依赖 T01，后置 T05 完成后即可部署）。目标是打通 Codehub → CodeArts Pipeline → SWR → CCE 的交付链路。本文承载架构决策、技术选型、依赖方向，以及任务卡中放不下的镜像/Helm/Pipeline 具体契约。本变更为纯基础设施，不构建 MCP 工具，故不产出 delta spec。

## Goals / Non-Goals

**Goals:**
- 提供可复现的多阶段镜像构建（layer cache 友好，运行镜像最小化）。
- 提供 CodeArts Pipeline 定义，实现 push → build → 推 SWR 的自动化。
- 提供 Helm Chart，使部署到 CCE 可声明、可回滚、可 dry-run。
- 镜像 tag 可追溯（版本号 + 构建时间戳）。

**Non-Goals:**
- ArgoCD / GitOps 自动同步（本期不引入）。
- 多环境 promotion 流水线（本期只有"贵阳一"一个环境）。
- 在 Helm values 中落 AK/SK（由集群级 Vault 注入）。
- 冒烟脚本正文（占位 `.gitkeep`，T05 起填充）、Micrometer 看板、README 完整使用示例属遗留项。

## Decisions

### 镜像构建（Dockerfile）

- 多阶段：
  - Stage 1 `build`：`maven:3.9-eclipse-temurin-21`，工作目录 `/src`。先按模块复制 `pom.xml`（根 + `agentic-common` / `agentic-adapter-ces` / `agentic-adapter-aom` / `agentic-adapter-apm` / `agentic-monitoring` / `agentic-mcp`），执行 `mvn -B -e -ntp dependency:go-offline`，再 `COPY . .` 后 `mvn -B -e -ntp -DskipTests package`。先复制 pom 再复制源码，是为了让依赖下载层命中 layer cache。
  - Stage 2 `runtime`：`eclipse-temurin:21-jre-jammy`（或华为云内部基线镜像），工作目录 `/app`，从 build 阶段拷 `agentic-mcp/target/*.jar` 为 `app.jar`。
- 暴露端口 `EXPOSE 8080 8081`。
- 启动：`ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]`。
- `.dockerignore` 必须包含 `target/`、`.git/`、`*.iml`、`.idea/`，避免上下文膨胀。

### CodeArts Pipeline（.cloudbuild/build.yml）

- `version: 2.0`，阶段：
  - `PRE_BUILD`：`checkout`（scm=codehub，url=仓库地址）。
  - `BUILD`：`maven`（jdk-version=openjdk-21，`mvn -B clean package -DskipTests=false`）+ `upload_artifact`（`agentic-mcp/target/*.jar`）。
  - `PACKAGE`：`docker_build` + `docker_push`，image=`swr.cn-north-9.myhuaweicloud.com/powercloud/DPOMBaseMCPService`，tag=`${VERSION}_$(date +%Y%m%d%H%M%S)`。
- CodeArts Pipeline 的具体语法以团队现有项目为准；任务卡的 YAML 仅为起点，需参考仓库内已有项目调整，不凭空发明步骤。

### 镜像 tag 规则

- 格式：`<版本>_<构建时间>`，例：`2.0.0_20260520171836`。
- 完整镜像 URL 例：`swr.cn-north-9.myhuaweicloud.com/powercloud/DPOMBaseMCPService:2.0.0_20260520171836`。
- 版本号来源：`pom.xml` 的 `<version>`，去掉 `-SNAPSHOT`。`0.0.1-SNAPSHOT` 开发期 CI 构建即去 `-SNAPSHOT` 拼时间戳；release 阶段才用正式版本号 `2.0.0`。
- 构建时间格式：`yyyyMMddHHmmss`，时区 UTC+8。

### Helm Chart 关键配置

- `values.yaml`：
  - `image.repository=swr.cn-north-9.myhuaweicloud.com/powercloud/DPOMBaseMCPService`，`image.tag=""`（由 `helm upgrade --set image.tag=...` 注入），`image.pullPolicy=IfNotPresent`。
  - `replicaCount: 2`。
  - `resources.requests`：cpu `2` / memory `4Gi`；`resources.limits`：cpu `4` / memory `8Gi`。
  - `env.HUAWEICLOUD_REGION: cn-southwest-2`。
  - `service.type=ClusterIP`，`service.port=8080`，`service.managementPort=8081`。
  - 探针：liveness `/actuator/health/liveness`@8081（initialDelay 30s / period 10s）；readiness `/actuator/health/readiness`@8081（initialDelay 20s / period 5s）。
- `templates/deployment.yaml`：标准 Spring Boot Deployment——暴露 8080（业务）+ 8081（management），liveness/readiness 均指向 management 端口，env 段引用 Vault secret。

### 凭据注入（Vault）

- AK/SK 不落 Helm values；从 Vault 注入到环境变量。
- 注入方式（Vault Agent Injector 或 External Secrets Operator）由集群级配置决定；Helm 仅声明引用方式。具体 annotation **因团队基础设施而异，不凭空发明**，需 SRE 确认。

### 依赖方向

- 构建依赖：CI 镜像构建消费 Maven 多模块工程（根 `pom.xml` 聚合 `agentic-*` 模块），最终可部署制品为 `agentic-mcp` 的可执行 jar。
- 部署依赖：Helm Chart 不依赖任何工具/adapter 源码，仅依赖运行镜像与集群级 Vault/网络配置；与工具能力解耦，T05 完成后即可部署。

## Risks / Trade-offs

- **CodeArts YAML 语法漂移**：不同团队/项目的 Pipeline schema 有差异，任务卡 YAML 仅供参考；落地时以现有项目为准，否则触发后可能构建失败。
- **Vault 注入耦合集群**：annotation 写错会导致 Pod 无凭据启动失败；交由 SRE 确认，避免硬编码。
- **单环境**：本期仅"贵阳一"，无 promotion；多环境推广需后续引入，当前 `values-prod.yaml` 承载生产差异化覆盖。
- **layer cache 失效风险**：模块 `pom.xml` 复制顺序或模块短名（`agentic-*`）写错会使 `dependency:go-offline` 缓存层失效，构建变慢但不影响正确性。
- **遗留**：冒烟脚本正文、Micrometer 看板、README 完整示例本期未交付，列入 tasks 末尾未勾选遗留项。
