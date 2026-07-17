# Flyway 数据库迁移手册

## 迁移职责

PostgreSQL 正式表结构只能通过 `backend/src/main/resources/db/migration` 演进。当前版本顺序如下：

| 版本 | 职责 |
| --- | --- |
| V1 | 兼容项目早期四份幂等基线脚本 |
| V2 | 知识索引 Outbox |
| V3 | 用户、知识空间、成员和文档元数据 |
| V4 | pgvector 文档片段和向量索引归并 |
| V5 | RAG 模型调用观测归并 |
| V6 | Agent 写工具、笔记、复习卡片和学习系统归并 |
| V7 | RAG 评估集、任务、租约和实验配置归并 |

`db/schema` 是已发布 V1 使用的冻结资源，不是后续开发入口。不得修改 `V1-V7` 已发布内容；新增字段、索引、约束或数据修复时必须创建更高版本迁移。

## 本地启动验证

使用 `local` 配置启动后端时，Flyway 会在业务 Bean 初始化前完成校验和迁移：

```powershell
Set-Location D:\Program\AgentMind\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

可以在 PostgreSQL 中检查迁移历史：

```sql
select installed_rank, version, description, type, success
from flyway_schema_history
order by installed_rank;
```

当前完整数据库应存在成功的 `V1-V7` 记录。Flyway 校验失败时不得删除历史记录、修改已发布脚本或关闭 `validate-on-migrate`，应新增修复迁移。

## 全新数据库集成测试

空库测试会启动一次性 `pgvector/pgvector:pg16` 容器，仅执行 Flyway，然后验证正式表、关键约束、vector 扩展、`vector(128)` 字段以及第二次迁移无重复执行：

```powershell
Set-Location D:\Program\AgentMind\backend
$env:AGENTMIND_RUN_FLYWAY_INTEGRATION = "true"
.\mvnw.cmd "-Dtest=FlywayFreshDatabaseIntegrationTests" test
Remove-Item Env:AGENTMIND_RUN_FLYWAY_INTEGRATION
```

该测试不会连接或清理本地开发数据库。日常单元测试不默认启动 Docker，避免普通构建依赖外部基础设施。
