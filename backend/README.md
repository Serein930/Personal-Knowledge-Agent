# AgentMind 后端服务

Personal Knowledge Agent 的后端服务，负责文档摄取、网页采集、知识检索和 RAG 问答编排。

## 当前阶段

后端已经进入 Stage 6 前置阶段：在已有向量检索能力上，补充 RAG 回答生成抽象、提示词模板、拒答策略和模型适配配置开关。

已实现能力：

- Java 21 与 Spring Boot 3.x Maven 工程。
- 统一接口响应、分页响应和全局异常处理。
- 健康检查接口。
- 文档与摄取任务 DTO 契约。
- 文件上传校验与本地对象存储适配。
- URL 安全校验与原始 HTML 抓取骨架。
- Markdown、TXT、代码文本和 HTML 文本提取。
- 带 Markdown 标题感知的基础 chunk 切分策略。
- 临时内存 chunk 预览接口，便于开发阶段验证。
- 本地确定性 embedding 客户端，便于无模型密钥验证检索流程。
- 内存向量库，支持按知识空间隔离的语义检索。
- PostgreSQL + pgvector 表结构和 `VectorStore` 适配骨架。
- RAG 问答接口、引用来源返回和检索上下文组装。
- `AnswerGenerator` 回答生成端口。
- 默认 `MockAnswerGenerator`，用于基于引用来源生成可重复的模拟回答。
- RAG 提示词模板、提示词版本号和低置信度拒答策略。
- Spring AI ChatModel 回答生成适配器骨架，默认不启用。
- 模型调用观测记录仓储、内存实现、数据库适配骨架和分页查询接口。
- 模型调用总体及分组聚合指标接口，可按模型和提示词版本比较成功率、降级率与平均耗时。

暂未实现能力：

- 数据库持久化。
- 用户认证和授权。
- 真实 MinIO 部署适配。
- PDF 和 Word 文本提取。
- 真实 Spring AI Embedding 模型接入。
- 真实 Spring AI ChatModel 模型调用。
- PostgreSQL + pgvector 检索质量的生产级验证。
- SSE 或 WebSocket 流式回答。

## 启动

环境要求：

- JDK 21
- Maven 3.9+

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run
```

健康检查：

```text
GET http://localhost:8080/api/v1/health
```

## 测试

```powershell
cd D:\Program\AgentMind\backend
mvn test
```

## 文档摄取接口

查询文档列表：

```text
GET http://localhost:8080/api/v1/workspaces/1/documents?page=1&pageSize=20
```

上传 Markdown、TXT 或 HTML 文件：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/documents/files" `
  -Form @{ file = Get-Item ".\README.md"; title = "README document"; tags = "docs" }
```

采集网页：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/documents/web-pages" `
  -ContentType "application/json" `
  -Body '{"url":"https://example.com/article","title":"Example article","tags":["Web","Test"]}'
```

查询摄取任务：

```text
GET http://localhost:8080/api/v1/workspaces/1/ingestion-tasks/{taskId}
```

预览生成的 chunk：

```text
GET http://localhost:8080/api/v1/workspaces/1/documents/{documentId}/chunks
```

检索当前知识空间中的已索引 chunk：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/knowledge/search" `
  -ContentType "application/json" `
  -Body '{"query":"thread pool worker threads","topK":5}'
```

生成带引用来源的 RAG 模拟回答：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/rag/chat" `
  -ContentType "application/json" `
  -Body '{"question":"How do thread pools help backend tasks?","topK":5}'
```

当前 RAG 行为：

- 接口会先检索当前知识空间内的 chunk。
- 响应会返回每个命中片段的引用来源。
- 后端会生成纯文本 `promptContext`，供测试、调试和后续真实模型调用使用。
- 后端会通过 `RagPromptTemplate` 生成模型提示词。
- 后端会通过 `RagRefusalPolicy` 判断是否因为资料不足或相关性过低而拒答。
- 默认回答生成器是 `MockAnswerGenerator`，会根据前几个引用片段生成稳定的模拟回答。
- 当前不会调用真实大模型，因此令牌用量仍为零。
- 后续可以通过配置切换到 Spring AI ChatModel 适配器。

RAG 配置：

```yaml
agentmind:
  rag:
    answer-generator: mock
    prompt-version: rag-chat-v1
    model-name: mock-local
    minimum-citation-score: 0.05
    max-context-citations: 5
    spring-ai-failure-fallback-enabled: true
```

配置说明：

- `answer-generator`：回答生成器类型，默认 `mock`；可切换为 `spring-ai` 以验证适配器骨架。
- `prompt-version`：提示词版本号，后续用于提示词评估和回溯。
- `model-name`：项目侧记录的模型名称，用于日志、响应元数据和后续观测表。
- `minimum-citation-score`：最低引用相关性阈值，低于该值时触发拒答。
- `max-context-citations`：最多放入提示词上下文的引用数量。
- `spring-ai-failure-fallback-enabled`：真实模型调用失败时是否返回降级回答，默认开启。

真实模型本地联调文档：

```text
docs/SPRING_AI_RAG_RUNBOOK.md
```

模型调用观测记录查询：

```text
GET http://localhost:8080/api/v1/workspaces/1/rag/model-calls?page=1&pageSize=20
```

按最终状态过滤：

```text
GET http://localhost:8080/api/v1/workspaces/1/rag/model-calls?page=1&pageSize=20&status=FALLBACK
```

查询模型调用聚合指标：

```text
GET http://localhost:8080/api/v1/workspaces/1/rag/model-calls/metrics
```

该接口返回知识空间总体指标，以及按“模型名称 + 提示词版本”分组的调用次数、成功率、
降级率和平均耗时，可直接作为后续前端评估面板的数据源。

观测记录默认保存在内存中，可通过以下配置切换到数据库适配器：

```yaml
agentmind:
  rag:
    observation-store: jdbc
```

完整联调说明：

```text
docs/RAG_MODEL_CALL_AUDIT_RUNBOOK.md
```

## PostgreSQL 与 pgvector 适配

默认向量库仍然是内存实现：

```yaml
agentmind:
  vector-store:
    type: memory
    embedding-dimensions: 128
```

后续切换到 PostgreSQL + pgvector：

1. 在仓库根目录启动本地 pgvector 数据库：

```powershell
cd D:\Program\AgentMind
docker compose up -d agentmind-postgres
```

2. 容器首次初始化时会自动执行表结构脚本：

```text
backend/src/main/resources/db/schema/knowledge_vector_chunks.sql
```

3. 使用 `local` profile 启动后端：

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

已提交的 `application-local.yml` 会使用本地 Docker 数据库：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/agentmind
    username: agentmind
    password: your-local-password

agentmind:
  vector-store:
    type: pgvector
    embedding-dimensions: 128
```

应用服务仍然依赖 `VectorStore` 接口，因此内存实现和 pgvector 实现可以通过配置平滑切换。

手动 pgvector 集成测试：

- 先启动 Docker Compose。
- 打开 `PgVectorStoreIntegrationTests`。
- 临时移除或覆盖 `@Disabled`。
- 在 IDEA 或 Maven 中运行该测试类。

该测试默认禁用，避免日常单元测试依赖 Docker。

端到端 pgvector 检索联调文档：

```text
docs/PGVECTOR_RETRIEVAL_RUNBOOK.md
```

注意事项：

- 上传文件和抓取的 HTML 快照会存放在 `.agentmind-storage`，该目录已被 Git 忽略。
- 当前阶段 Markdown、TXT、代码文本和 HTML 可以生成 chunk。
- 生成的 chunk 会写入当前配置的向量库。
- 当前 embedding 实现是本地确定性算法，只用于验证检索链路，不代表生产语义检索质量。
- pgvector 适配器已经可以通过配置启用，但默认仍不开启。
- PDF 和 Word 文件当前可以保存原始文件，解析能力留到后续阶段。
- 文档、任务、chunk 和向量数据当前主要保存在内存中，服务重启后会重置。
