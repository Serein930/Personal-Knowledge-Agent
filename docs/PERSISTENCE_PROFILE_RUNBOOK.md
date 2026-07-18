# 持久化 Profile 使用手册

## 模式边界

项目保留三种明确的运行边界：

| 模式 | 结构化数据 | 会话记忆 | 向量 | 用途 |
| --- | --- | --- | --- | --- |
| 无 profile | 内存 | 内存 | 内存 | 单元测试、无依赖快速启动 |
| `local` | PostgreSQL JDBC | Redis | pgvector | 本机真实依赖联调 |
| `production` | PostgreSQL JDBC | Redis | pgvector | 多实例正式运行 |

`local` 和 `production` 下，用户、知识空间、文档元数据、Agent 确认单、工具审计、笔记、复习卡片、学习画像、RAG 调用审计、评估集与评估任务都不允许使用内存 Repository。

## 本地启动

先启动 PostgreSQL、Redis 和其他本地依赖，再启动后端：

```powershell
Set-Location D:\Program\AgentMind\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

`local` 默认启用 `local-jwt`。全新数据库应先调用 `/api/v1/auth/register`，注册流程会在同一业务流程中创建用户和默认知识空间。后续请求必须携带返回的访问令牌，不能再依赖固定演示用户编号。

本地 Redis 地址可以通过以下变量覆盖：

```text
AGENTMIND_REDIS_HOST
AGENTMIND_REDIS_PORT
AGENTMIND_REDIS_DATABASE
AGENTMIND_REDIS_PASSWORD
```

## 生产防回退

生产 profile 在启动完成前校验以下关键配置：

```text
agentmind.core.persistence.store=jdbc
agentmind.agent.persistence.store=jdbc
agentmind.rag.observation-store=jdbc
agentmind.evaluation.store=jdbc
agentmind.chat.memory.store=redis
agentmind.vector-store.type=pgvector
```

任意一项不符合要求都会抛出“正式环境配置验收失败”，应用不会进入可接收流量状态。错误信息只包含配置名称，不输出数据库、Redis 或对象存储秘密。

## 自动化验证

```powershell
Set-Location D:\Program\AgentMind\backend
.\mvnw.cmd "-Dtest=ProductionConfigurationValidatorTests,PersistenceProfileConfigurationTests,RepositoryPersistenceConditionContractTests" test
```

Repository 契约测试会扫描所有以 `InMemory`、`Jdbc` 或 `Redis` 命名的仓储适配器，验证其配置前缀、取值和缺省行为。后续新增持久化适配器时必须沿用该条件装配约定。

需要验证真实 Spring 上下文时运行：

```powershell
$env:AGENTMIND_RUN_PERSISTENCE_PROFILE_INTEGRATION = "true"
.\mvnw.cmd "-Dtest=PersistentRepositoryProfileIntegrationTests" test
Remove-Item Env:AGENTMIND_RUN_PERSISTENCE_PROFILE_INTEGRATION
```

该测试使用一次性 PostgreSQL/pgvector 与 Redis 容器，验证 local profile 实际装配 JDBC、Redis 和 pgvector Bean，并确认不存在内存 Repository。
