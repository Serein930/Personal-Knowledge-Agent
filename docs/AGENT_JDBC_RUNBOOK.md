# 智能体写工具 PostgreSQL 联调手册

## 存储内容

Flyway 的 `V6__reconcile_agent_and_study_schema.sql` 负责创建以下表：

- `agent_tool_confirmations`：确认单、令牌摘要、参数与状态流转。
- `agent_tool_call_audits`：工具类型、请求摘要、结果摘要、耗时与失败原因。
- `knowledge_notes`：用户确认后创建的知识笔记。
- `study_flashcards`：用户确认后保存的复习卡片。

笔记和复习卡片通过“用户、知识空间、请求编号”唯一约束保证数据库级幂等。

## 启动数据库

```powershell
cd D:\Program\AgentMind
docker compose up -d agentmind-postgres
docker exec agentmind-postgres psql -U agentmind -d agentmind -f /docker-entrypoint-initdb.d/03-agent-write-tools.sql
```

第二条命令适用于已经存在数据卷的环境。新建数据卷会在容器首次初始化时自动执行该脚本。

## 使用 JDBC 模式启动后端

```powershell
$env:JAVA_HOME = "D:\Tools\Java21"
$env:Path = "D:\Tools\Java21\bin;$env:Path"
cd D:\Program\AgentMind\backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

`local` 配置会启用：

- PostgreSQL 智能体写工具仓储。
- Spring JDBC 事务管理器。
- pgvector 向量仓储。

不指定 `local` 时仍使用内存仓储，无需数据库。

## 手动运行数据库集成测试

```powershell
$env:AGENTMIND_AGENT_JDBC_INTEGRATION_TEST = "true"
mvn -Dtest=JdbcAgentWriteToolIntegrationTests test
```

测试会清空四张 Stage 7 表，只能连接本地开发数据库。当前测试同时覆盖：

- 确认单、审计和复习卡片正常提交。
- 两个独立 JDBC 仓储实例并发争抢同一确认单，只有一次条件更新成功。
- 故障写工具的业务数据随主事务回滚。
- 失败审计通过独立事务保留，确认单最终收口为 `FAILED`。

## 验证事务与幂等

1. 创建并确认一张 `flashcard.create` 确认单。
2. 查询 `study_flashcards`，应只有一条记录。
3. 使用相同 `requestId` 和相同参数再次确认，应复用结果。
4. 使用相同 `requestId` 和不同参数，应返回 `RESOURCE_CONFLICT`。
5. 两个请求并发确认同一确认单时，只有一个请求能把状态从 `PENDING_CONFIRMATION` 更新为 `EXECUTING`。
6. 写工具抛出异常时，业务表不应出现部分数据，审计表应保留一条 `FAILED` 记录。
7. 人工构造超过执行时限的 `EXECUTING` 确认单后，维护任务应将其标记为 `FAILED`，且不会再次执行工具。
