# pgvector 检索联调手册

本文档用于验证 Stage 5 的真实向量检索链路：

```text
Markdown 上传 -> 文本提取 -> chunk 切分 -> embedding 生成 -> pgvector 写入 -> /knowledge/search
```

当前 embedding 实现是本地确定性算法，不是生产级语义模型。它的作用是让我们在不依赖真实模型密钥的情况下，
验证 PostgreSQL + pgvector 的存储和检索链路是否打通。

## 前置条件

- JDK 21
- Maven 3.9+
- Docker Desktop
- PowerShell
- 本机 `5432` 端口可用

## 1. 启动 pgvector

在仓库根目录执行：

```powershell
cd D:\Program\AgentMind
docker compose up -d agentmind-postgres
```

检查容器状态：

```powershell
docker compose ps
```

首次启动时由 Flyway 创建 `knowledge_vector_chunks` 表：

```text
backend/src/main/resources/db/migration/V4__reconcile_knowledge_vector_chunks.sql
```

已有数据卷不需要为了表结构升级而删除；启动后端时 Flyway 会自动应用尚未执行的版本。只有确定要清空全部本地数据时才使用：

```powershell
docker compose down -v
docker compose up -d agentmind-postgres
```

## 2. 使用 local profile 启动后端

当前终端先切换到 Java 21：

```powershell
$env:JAVA_HOME='D:\Tools\Java21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

启动后端：

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

`local` profile 会把向量库切换为 pgvector：

```yaml
agentmind:
  vector-store:
    type: pgvector
```

## 3. 运行手动检索脚本

打开第二个 PowerShell 窗口：

```powershell
cd D:\Program\AgentMind
powershell -ExecutionPolicy Bypass -File .\scripts\manual-pgvector-retrieval.ps1
```

脚本会执行以下动作：

1. 创建临时 Markdown 文件。
2. 调用 `POST /api/v1/workspaces/1/documents/files` 上传文件。
3. 读取返回的 `documentId` 和 `taskId`。
4. 调用 `GET /api/v1/workspaces/1/documents/{documentId}/chunks` 查看切分结果。
5. 调用 `POST /api/v1/workspaces/1/knowledge/search` 验证检索结果。

预期结果：

- 上传响应状态为 `SUCCEEDED`。
- 至少返回一个 chunk。
- 检索结果包含刚上传的文档 ID。
- 排名靠前的检索结果内容包含 `thread pool` 或 `backend worker`。

## 4. 可选数据库检查

可以直接查看 pgvector 表数据：

```powershell
docker exec -it agentmind-postgres psql -U agentmind -d agentmind
```

执行 SQL：

```sql
select workspace_id, document_id, chunk_id, chunk_sequence, left(content, 80)
from knowledge_vector_chunks
order by created_at desc
limit 5;
```

## 5. 清理环境

停止服务但保留数据：

```powershell
docker compose down
```

删除本地数据库数据卷：

```powershell
docker compose down -v
```

## 常见问题

- 如果后端启动时报 `spring.datasource.url is required`，请确认已经启用 `local` profile。
- 如果后端无法连接 PostgreSQL，请检查 `docker compose ps`，并确认 `5432` 端口没有被占用。
- 如果检索没有返回结果，请先调用 chunks 接口；如果 chunks 为空，说明解析或切分阶段没有产生可索引内容。
- 如果 `mvn` 使用 Java 17，请在当前终端先把 `JAVA_HOME` 设置为 Java 21。
