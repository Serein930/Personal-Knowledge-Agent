# 生产验收与安全加固手册

## 一、本阶段交付边界

本阶段完成 Redis 分布式限流、外部秘密注入、备份恢复、后端镜像、健康探针、优雅停机、
Docker Swarm 滚动发布、k6 性能基线和真实依赖集成测试入口。默认开发配置仍不依赖生产基础设施，
只有启用 `production` Profile 才会执行严格验收并拒绝不安全配置。

## 二、Redis 分布式限流

限流使用 Redis Lua 原子执行自增和过期时间设置。JWT 请求按用户编号共享配额，认证接口按客户端地址
共享配额。默认一分钟窗口内：通用接口 300 次、认证接口 20 次、摄取接口 60 次、RAG 接口 60 次。
超限返回 `429 RATE_LIMITED`、`Retry-After` 和剩余配额响应头；Redis 故障时生产配置返回 `503`。

只有反向代理已经覆盖并清洗外部传入的 `X-Forwarded-For` 时，才能配置：

```powershell
$env:AGENTMIND_CLIENT_IP_HEADER="X-Forwarded-For"
```

Prometheus 指标为 `agentmind_http_rate_limit_requests_total`，标签只包含固定接口分类和结果，不包含用户编号。

## 三、外部秘密与生产启动

生产配置支持环境变量，也支持把秘密以同名文件挂载到 `/run/secrets`。推荐文件名：

```text
spring.datasource.password
spring.data.redis.password
agentmind.storage.minio.access-key
agentmind.storage.minio.secret-key
agentmind.keyword-index.opensearch.password
```

生产校验要求认证、JDBC、Redis 限流、MinIO、pgvector、OpenSearch、Outbox 和 Redis 会话全部开启，
并拒绝本机跨域来源、空秘密和示例密码。非秘密变量参考仓库根目录 `.env.example`。
OIDC 模式还必须配置 `AGENTMIND_OIDC_AUDIENCE`，后端会同时校验发行方、签名、有效期和 API 受众。

构建镜像：

```powershell
docker build -t registry.example.com/agentmind/backend:0.1.0 .
docker push registry.example.com/agentmind/backend:0.1.0
```

若生产网络要求从企业镜像仓库拉取基础镜像，可在不修改 Dockerfile 的情况下覆盖构建参数：

```powershell
docker build `
  --build-arg MAVEN_IMAGE=registry.example.com/base/maven:3.9.9-eclipse-temurin-21-alpine@sha256:企业镜像摘要 `
  --build-arg RUNTIME_IMAGE=registry.example.com/base/eclipse-temurin:21-jre-alpine@sha256:企业镜像摘要 `
  -t registry.example.com/agentmind/backend:0.1.0 .
```

## 四、滚动发布和回滚

> 最终生产拓扑已经改为 TLS 网关、稳定组和 10% 灰度组，以下基础说明由
> `PRODUCTION_FINAL_ACCEPTANCE_RUNBOOK.md` 中的发布步骤补充并覆盖。

Docker Swarm 使用两个副本、`start-first` 更新顺序和入口路由网格。新实例通过就绪探针后才停止旧实例，
30 秒监控窗口内失败会自动回滚。首次部署前创建外部网络和秘密：

```powershell
docker swarm init
docker network create --driver overlay --opt encrypted agentmind-production
./deploy/create-swarm-secrets.ps1 `
  -TlsCertificatePath "D:/secure/tls/fullchain.pem" `
  -TlsPrivateKeyPath "D:/secure/tls/private.key"
./deploy/deploy-release.ps1 -Image "registry.example.com/agentmind/backend:0.1.0"
```

观察发布：

```powershell
docker service ps agentmind_agentmind-backend --no-trunc
Invoke-RestMethod https://knowledge.example.com/actuator/health/readiness
```

手工回滚：

```powershell
./deploy/rollback-release.ps1
```

应用收到 `SIGTERM` 后停止接收新流量，并最多使用 30 秒完成当前请求；Swarm 提供 45 秒停止宽限期。

## 五、备份恢复演练

本地验收环境先启动 PostgreSQL、MinIO 和 OpenSearch：

```powershell
docker compose --profile minio --profile opensearch up -d
$env:AGENTMIND_MINIO_ACCESS_KEY="本地访问密钥"
$env:AGENTMIND_MINIO_SECRET_KEY="本地秘密密钥"
./scripts/backup-production.ps1
```

每份备份包含 PostgreSQL 自包含转储、MinIO 对象镜像、OpenSearch 一致性快照仓库和 SHA-256 清单。
恢复必须在停止业务写入后执行：

```powershell
./scripts/restore-production.ps1 `
  -BackupDirectory ".agentmind-backups/20260716-220000" `
  -ConfirmMaintenanceWindow
```

验收必须记录恢复耗时，并核对用户、知识空间、文档数量、随机对象哈希、OpenSearch 文档数和固定 RAG 题。
备份文件应进一步复制到异地加密存储，不能只留在同一宿主机。

## 六、正式性能基线

准备至少一百篇测试文档、有效访问令牌和两个后端副本，然后运行：

容量测试使用同一个验收用户持续请求，测试环境应把 `AGENTMIND_RATE_LIMIT_RAG_REQUESTS` 和
`AGENTMIND_RATE_LIMIT_GENERAL_REQUESTS` 提高到预期吞吐以上；限流门禁应使用独立低配额场景验证。

```powershell
k6 run `
  -e BASE_URL=http://localhost:8081 `
  -e WORKSPACE_ID=1 `
  -e ACCESS_TOKEN=$env:AGENTMIND_ACCESS_TOKEN `
  -e VUS=30 `
  -e DURATION=2m `
  scripts/production-capacity-test.js
```

门禁要求错误率低于 1%，健康检查 P95 小于 200ms，检索 P95 小于 800ms，RAG P95 小于 3 秒。
真实模型的延迟波动较大，必须在报告中记录模型、区域、上下文长度和是否命中降级。

## 七、测试命令

```powershell
cd backend
mvn test
```

本地 Redis 联调：

```powershell
$env:AGENTMIND_REDIS_INTEGRATION_TEST="true"
mvn -Dtest=RedisDistributedRateLimiterIntegrationTests test
```

Docker 生产依赖联调：

```powershell
$env:AGENTMIND_RUN_PRODUCTION_INTEGRATION="true"
mvn -Dtest=ProductionKnowledgeIndexPipelineIntegrationTests,IdentityPersistenceIntegrationTests test
```

真实 OIDC 联调：

```powershell
$env:AGENTMIND_RUN_OIDC_INTEGRATION="true"
mvn -Dtest=OidcJwtDecoderIntegrationTests test
```
