# T02 — CI/CD 与部署

> 状态: Ready · 估时: 0.5d · 依赖: T01 · 后置: T05 完成后即可部署

## 目标

打通从 Codehub 代码 push 到 SWR 镜像、再到 CCE 部署的完整链路。

## 范围

**做**:
- `.cloudbuild/build.yml`（CodeArts Pipeline）
- `Dockerfile`（多阶段构建，最小镜像）
- Helm Chart：`helm/dpom-mcp-server/`
- 镜像构建脚本（含版本号 + 时间戳 tag 生成）
- 部署到 CCE 的命令文档（手动 / 半自动）

**不做**:
- ArgoCD / GitOps 自动同步（本期不引入）
- 多环境 promotion 流水线（本期只有"贵阳一"一个环境）

## 产物清单

```
.cloudbuild/
  build.yml                          ← CodeArts Pipeline 定义
Dockerfile
.dockerignore
helm/
  dpom-mcp-server/
    Chart.yaml
    values.yaml
    values-prod.yaml
    templates/
      deployment.yaml
      service.yaml
      configmap.yaml
      _helpers.tpl
      NOTES.txt
scripts/
  build-image.sh                     ← 本地构建镜像辅助脚本
  smoke/
    .gitkeep                         ← 冒烟脚本占位，T05 起会有内容
```

## 关键技术要求

### Dockerfile

多阶段：
1. Stage 1：`maven:3.9-eclipse-temurin-21` 构建
2. Stage 2：`eclipse-temurin:21-jre-jammy`（或华为云内部镜像基线）运行

```dockerfile
# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY agentic-common/pom.xml agentic-common/
COPY agentic-adapter-ces/pom.xml agentic-adapter-ces/
COPY agentic-adapter-aom/pom.xml agentic-adapter-aom/
COPY agentic-adapter-apm/pom.xml agentic-adapter-apm/
COPY agentic-monitoring/pom.xml agentic-monitoring/
COPY agentic-mcp/pom.xml agentic-mcp/
RUN mvn -B -e -ntp dependency:go-offline
COPY . .
RUN mvn -B -e -ntp -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /src/agentic-mcp/target/*.jar app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
```

### .cloudbuild/build.yml

CodeArts Pipeline YAML（确认你团队现有项目的格式后微调）。核心阶段：

```yaml
version: 2.0
steps:
  PRE_BUILD:
    - checkout:
        inputs:
          scm: codehub
          url: <your-repo-url>
  BUILD:
    - maven:
        inputs:
          jdk-version: openjdk-21
          command: mvn -B clean package -DskipTests=false
    - upload_artifact:
        inputs:
          path: agentic-mcp/target/*.jar
  PACKAGE:
    - docker_build:
        inputs:
          dockerfile: Dockerfile
          image: swr.cn-north-9.myhuaweicloud.com/powercloud/DPOMBaseMCPService
          tag: ${VERSION}_$(date +%Y%m%d%H%M%S)
    - docker_push:
        inputs:
          image: swr.cn-north-9.myhuaweicloud.com/powercloud/DPOMBaseMCPService
          tag: ${VERSION}_$(date +%Y%m%d%H%M%S)
```

**确认要点**：CodeArts Pipeline 的具体语法以你团队现有项目为准——本任务卡的目的是给 AI 一个起点，AI 应该参考仓库内已有项目（如果有）的 `.cloudbuild/build.yml` 调整。

### 镜像 tag 规则

格式：`<版本>_<构建时间>`

举例：`2.0.0_20260520171836`

完整镜像 URL 示例：`swr.cn-north-9.myhuaweicloud.com/powercloud/DPOMBaseMCPService:2.0.0_20260520171836`

版本号来源：`pom.xml` 的 `<version>` 字段（去掉 `-SNAPSHOT`）。
构建时间格式：`yyyyMMddHHmmss`，UTC+8。

### Helm Chart 关键配置

`values.yaml`：

```yaml
image:
  repository: swr.cn-north-9.myhuaweicloud.com/powercloud/DPOMBaseMCPService
  tag: ""  # 由 helm upgrade --set image.tag=... 注入
  pullPolicy: IfNotPresent

replicaCount: 2

resources:
  requests:
    cpu: "2"
    memory: "4Gi"
  limits:
    cpu: "4"
    memory: "8Gi"

env:
  HUAWEICLOUD_REGION: cn-southwest-2

# AK/SK 不在这里！从 Vault 注入到环境变量
# Vault Agent Injector 或 External Secrets Operator 由集群级配置
# 这里只声明引用方式（具体注解依团队 Vault 集成方案）

service:
  type: ClusterIP
  port: 8080
  managementPort: 8081

probes:
  liveness:
    path: /actuator/health/liveness
    port: 8081
    initialDelaySeconds: 30
    periodSeconds: 10
  readiness:
    path: /actuator/health/readiness
    port: 8081
    initialDelaySeconds: 20
    periodSeconds: 5
```

`templates/deployment.yaml`：标准 Spring Boot Deployment 模板，重点：
- 暴露 8080（业务）+ 8081（management）
- liveness/readiness 都指向 management 端口
- env 段引用 Vault secret（具体语法看你团队的 Vault Operator）

## 验收标准

- [ ] `docker build .` 在本地成功（或 CodeArts 构建成功）
- [ ] 镜像启动后能访问 `/actuator/health`
- [ ] `helm template helm/dpom-mcp-server` 输出合法 YAML
- [ ] `helm install --dry-run` 通过
- [ ] CodeArts Pipeline 触发后镜像能推到 SWR
- [ ] 部署到 CCE 后 Pod 就绪
- [ ] Pod 启动日志 JSON 格式可见
- [ ] 文档 `README.md` 包含部署一节，含 `helm upgrade --install` 命令示例

## AI 易错点提醒

1. **Maven 模块复制顺序**：Dockerfile 中先复制 pom.xml 再复制源码是为了利用 layer cache。模块目录名是 `agentic-*` 短名。
2. **`.dockerignore` 必须包含** `target/`, `.git/`, `*.iml`, `.idea/`，否则上下文巨大。
3. **CodeArts YAML 语法**以你团队现有项目为准，本任务卡仅供参考。
4. **Vault 注入方式**因团队基础设施而异，不要凭空发明 annotation；让你的 SRE 同事确认。
5. **镜像版本号**：`pom.xml` 用 `0.0.1-SNAPSHOT` 期间，CI 构建时把 `-SNAPSHOT` 去掉拼时间戳。release 阶段才用正式版本号 `2.0.0`。

## 完成后

PR：`feat(T02): CI pipeline + Dockerfile + Helm chart`。
