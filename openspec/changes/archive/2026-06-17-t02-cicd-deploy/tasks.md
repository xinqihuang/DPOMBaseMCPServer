> 存量回填：以下任务已于早期 commit 随 T02 交付（原任务卡 `docs/tasks/T02-cicd-deploy.md`）。已交付项勾选 `[x]`；任务卡"不做（本期未交付）"及验收遗留项列在末尾未勾选。

## 1. 镜像构建（Dockerfile）

- [x] 1.1 多阶段 `Dockerfile`：Stage 1 `maven:3.9-eclipse-temurin-21` 构建，Stage 2 `eclipse-temurin:21-jre-jammy` 运行
- [x] 1.2 先按模块复制 `pom.xml`（根 + 6 个 `agentic-*` 模块）跑 `dependency:go-offline`，再复制源码 `package`，利用 layer cache
- [x] 1.3 `EXPOSE 8080 8081`，`ENTRYPOINT java -XX:MaxRAMPercentage=70 -jar /app/app.jar`
- [x] 1.4 `.dockerignore` 含 `target/` / `.git/` / `*.iml` / `.idea/`

## 2. CI Pipeline（.cloudbuild/build.yml）

- [x] 2.1 `version: 2.0`，`PRE_BUILD` checkout（scm=codehub）
- [x] 2.2 `BUILD`：Maven openjdk-21 `clean package` + `upload_artifact`（`agentic-mcp/target/*.jar`）
- [x] 2.3 `PACKAGE`：`docker_build` + `docker_push` 到 SWR `powercloud/DPOMBaseMCPService`，tag `${VERSION}_$(date +%Y%m%d%H%M%S)`

## 3. Helm Chart（helm/dpom-mcp-server）

- [x] 3.1 `Chart.yaml` / `values.yaml` / `values-prod.yaml`
- [x] 3.2 `values.yaml`：image（repository + tag 注入 + IfNotPresent）、replicaCount 2、resources（req 2C/4Gi、limit 4C/8Gi）、env HUAWEICLOUD_REGION=cn-southwest-2、service ClusterIP 8080/8081
- [x] 3.3 `templates/deployment.yaml`：暴露 8080+8081，liveness/readiness 指向 8081 management 端口
- [x] 3.4 `templates/{service.yaml,configmap.yaml,_helpers.tpl,NOTES.txt}`
- [x] 3.5 env 段引用 Vault secret（注入方式依集群级 Vault Operator，不硬编码 annotation）

## 4. 构建脚本与 tag 规则

- [x] 4.1 `scripts/build-image.sh`：从 `pom.xml` 版本号去 `-SNAPSHOT` 拼 `yyyyMMddHHmmss`(UTC+8) 生成 tag
- [x] 4.2 `scripts/smoke/.gitkeep` 占位

## 5. 验收

- [x] 5.1 `docker build .` 本地成功（或 CodeArts 构建成功）
- [x] 5.2 镜像启动后能访问 `/actuator/health`
- [x] 5.3 `helm template helm/dpom-mcp-server` 输出合法 YAML
- [x] 5.4 `helm install --dry-run` 通过
- [x] 5.5 CodeArts Pipeline 触发后镜像能推到 SWR
- [x] 5.6 部署到 CCE 后 Pod 就绪
- [x] 5.7 Pod 启动日志 JSON 格式可见

## 6. 遗留项（本期未交付）

- [ ] 6.1 README 部署一节含 `helm upgrade --install` 命令示例
- [ ] 6.2 冒烟脚本正文（`scripts/smoke/`，T05 起填充）
- [ ] 6.3 ArgoCD / GitOps 自动同步（本期不引入）
- [ ] 6.4 多环境 promotion 流水线（本期只有"贵阳一"一个环境）
