# 自动 OBS 诊断证据存储运维说明

## 启用条件

默认配置不连接 OBS。目标环境必须显式注入：

```text
DPOM_OBS_ENABLED=true
DPOM_OBS_AUTOMATIC_STORAGE_ENABLED=true
DPOM_OBS_ENDPOINT=<environment OBS endpoint>
DPOM_OBS_BUCKET=<environment bucket>
DPOM_OBS_PREFIX=<environment prefix>
DPOM_OBS_SERVICE_CODE=<stable service code>
HUAWEICLOUD_AK=<secret injection>
HUAWEICLOUD_SK=<secret injection>
DPOM_OBS_KMS_KEY_ID=<optional KMS key id>
```

bucket、endpoint、prefix 和 service code 都是环境配置，不允许把验证环境值写入镜像、Git 或 Helm 默认值。
AK/SK 必须由 Vault/Kubernetes Secret 等秘密注入机制提供，禁止写入命令记录、配置文件和日志。

## 运行行为

自动存储在证据引用形成前执行。载荷会先净化常见敏感字段，再生成规范 JSON、SHA-256 摘要与确定性对象键；
只有 OBS 返回匹配的对象键、字节数和非空 ETag 后才会返回证据引用。任何上传或校验错误均使当前证据采集失败。

逐包 approve/revoke 控制面已移除。部署仍保持双 gate 默认关闭，目标位置由环境锁定，运行身份应限制在配置的
bucket/prefix，并只授权必要的 PutObject、GetObject、GetObjectMetadata 及 KMS 使用权限。

## 验证

真实 OBS 测试默认跳过。只有显式设置 `RUN_OBS_E2E=true`，并同时提供上述 endpoint、bucket、prefix 与 AK/SK
环境变量时才执行。测试在 `{prefix}/verification/` 下上传非敏感小对象，验证 PUT、HEAD、GET、ETag、字节内容
和 SHA-256 用户元数据，并保留对象作为验收证据。测试目标不得成为应用默认值。
