# 身份认证与核心持久化验收手册

## 本阶段范围

本阶段完成 Spring Security 无状态认证、用户与知识空间成员持久化、文档元数据持久化和 MinIO 对象存储适配。所有业务控制器不再接收客户端声明的用户编号，而是通过 `Authorization: Bearer <token>` 从认证上下文解析当前用户。

部署、限流、备份恢复、滚动发布和正式性能基线不放入本次提交，将在下一生产验收阶段完成。

## 安全模式

### 关闭模式

`AGENTMIND_SECURITY_MODE=disabled` 只用于单元测试和无数据库界面联调。该模式固定使用种子用户，不应部署到公网或共享测试环境。

### 本地 JWT 模式

```powershell
$env:AGENTMIND_SECURITY_MODE="local-jwt"
$env:AGENTMIND_JWT_SECRET="请替换为至少32字符的随机高强度秘密"
$env:AGENTMIND_CORE_PERSISTENCE_STORE="jdbc"
```

密码使用 BCrypt 12 轮摘要，访问令牌默认两小时过期。签名秘密为空或少于 32 个字符时应用拒绝启动。

注册：

```powershell
$body = @{
  username = "serein"
  displayName = "Serein"
  email = "serein@example.com"
  password = "请替换为至少12字符的密码"
} | ConvertTo-Json

$result = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/v1/auth/register" `
  -ContentType "application/json" -Body $body
$env:AGENTMIND_ACCESS_TOKEN = $result.data.accessToken
```

登录：

```powershell
$body = @{username="serein"; password="请替换为注册密码"} | ConvertTo-Json
$result = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/v1/auth/login" `
  -ContentType "application/json" -Body $body
$env:AGENTMIND_ACCESS_TOKEN = $result.data.accessToken
```

访问受保护接口：

```powershell
$headers = @{Authorization="Bearer $env:AGENTMIND_ACCESS_TOKEN"}
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/users/me" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/workspaces" -Headers $headers
```

### OIDC 模式

```powershell
$env:AGENTMIND_SECURITY_MODE="oidc"
$env:AGENTMIND_OIDC_ISSUER_URI="https://identity.example.com/realms/agentmind"
$env:AGENTMIND_CORE_PERSISTENCE_STORE="jdbc"
```

后端会读取发行方元数据和签名公钥校验令牌。身份提供方必须提供数字型 `uid` 声明，或把本地用户编号放入数字型 `sub`；该编号必须已经同步到 `app_user`。后续企业身份同步阶段可以增加首次登录自动开户，目前不接受无法映射到本地用户的数据访问。

## PostgreSQL 核心数据

Flyway `V3__create_identity_workspace_document.sql` 创建：

- `app_user`：账号、密码摘要、角色和状态。
- `knowledge_workspace`：知识空间及所有者。
- `workspace_member`：所有者、编辑者和只读成员关系。
- `knowledge_document`：来源、对象键、标签、摄取状态和 chunk 数量。

上传、网页采集、文档列表、片段查询、任务查询、语义检索和 Agent 工具调用都会校验知识空间成员关系。JDBC 查询以知识空间编号作为强制条件。

## MinIO

启动本地 MinIO：

```powershell
$env:AGENTMIND_MINIO_ACCESS_KEY="agentmind"
$env:AGENTMIND_MINIO_SECRET_KEY="请替换为本地开发密码"
docker compose --profile minio up -d agentmind-minio
```

后端配置：

```powershell
$env:AGENTMIND_STORAGE_TYPE="minio"
$env:AGENTMIND_MINIO_ENDPOINT="http://localhost:9000"
$env:AGENTMIND_MINIO_ACCESS_KEY="agentmind"
$env:AGENTMIND_MINIO_SECRET_KEY="请替换为本地开发密码"
$env:AGENTMIND_MINIO_BUCKET="agentmind"
```

首次写入会幂等创建存储桶。数据库只保存对象键，不保存宿主机路径或公开下载地址。

## 完整启动示例

```powershell
docker compose --profile minio --profile opensearch up -d

$env:AGENTMIND_SECURITY_MODE="local-jwt"
$env:AGENTMIND_JWT_SECRET="请替换为至少32字符的随机高强度秘密"
$env:AGENTMIND_CORE_PERSISTENCE_STORE="jdbc"
$env:AGENTMIND_STORAGE_TYPE="minio"
$env:AGENTMIND_MINIO_ACCESS_KEY="agentmind"
$env:AGENTMIND_MINIO_SECRET_KEY="请替换为本地开发密码"
mvn -Dspring-boot.run.profiles=local spring-boot:run
```

## 测试

```powershell
cd backend
mvn test
```

Docker 可用时执行 PostgreSQL 集成测试：

```powershell
$env:AGENTMIND_RUN_PRODUCTION_INTEGRATION="true"
mvn -Dtest=IdentityPersistenceIntegrationTests test
```

重点测试包括无令牌 `401`、JWT 用户解析、密码错误统一响应、跨空间访问拒绝、MinIO 空凭据阻断、Flyway 核心表和文档元数据持久化。
