# Spring AI 真实向量模型联调手册

## 目标

本手册用于验证文档摄取和知识检索已经从确定性模拟向量平滑切换到 Spring AI `EmbeddingModel`。
默认开发配置继续使用 `deterministic`，生产配置固定使用 `spring-ai`，避免日常测试产生外部调用和费用。

## 配置项

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OPENAI_API_KEY` | 无 | OpenAI 密钥，仅通过环境变量或密钥管理系统注入 |
| `AGENTMIND_EMBEDDING_PROVIDER` | `deterministic` | 本地真实联调时设置为 `spring-ai` |
| `AGENTMIND_SPRING_AI_EMBEDDING_MODEL` | `none` | 本地真实联调时设置为 `openai` |
| `AGENTMIND_EMBEDDING_MODEL` | `deterministic-local` | 真实联调建议使用 `text-embedding-3-small` |
| `AGENTMIND_EMBEDDING_DIMENSIONS` | `128` | 模型输出、应用校验和 pgvector 列必须保持一致 |
| `AGENTMIND_EMBEDDING_BATCH_SIZE` | `32` | 单次外部请求包含的文本数量 |
| `AGENTMIND_EMBEDDING_MAXIMUM_ATTEMPTS` | `3` | 包含首次调用在内的最大尝试次数 |
| `AGENTMIND_EMBEDDING_RETRY_INITIAL_BACKOFF` | `200ms` | 指数退避的初始等待时间 |
| `AGENTMIND_EMBEDDING_INPUT_COST_PER_MILLION_TOKENS` | `0` | 每百万输入 Token 的美元单价，由维护者按实际账单更新 |

价格不会硬编码在代码中，因为供应商计费可能变化。费用指标是根据模型返回的输入 Token 和当前配置单价计算的估算值，应以供应商账单为准。

## 本地联调

先启动本地 PostgreSQL 和 pgvector，再在 PowerShell 中设置仅对当前终端生效的变量：

```powershell
$env:OPENAI_API_KEY = "你的本地密钥"
$env:AGENTMIND_EMBEDDING_PROVIDER = "spring-ai"
$env:AGENTMIND_SPRING_AI_EMBEDDING_MODEL = "openai"
$env:AGENTMIND_EMBEDDING_MODEL = "text-embedding-3-small"
$env:AGENTMIND_EMBEDDING_DIMENSIONS = "128"
Set-Location D:\Program\AgentMind\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

启动后上传 Markdown 或 TXT 文档，再调用知识检索接口。摄取与查询会共用同一个真实向量模型，日志只记录模型名、批次数、Token、费用估算、耗时和尝试次数，不记录用户原文。

## 手动模型测试

设置 `OPENAI_API_KEY` 后运行：

```powershell
.\mvnw.cmd "-Dtest=SpringAiEmbeddingManualIntegrationTests" test
```

未设置密钥时该测试自动跳过。常规测试使用 Mock `EmbeddingModel`，覆盖批量切分、顺序保持、失败重试、返回维度校验和指标记录，不访问付费服务。

## 维度与重建约束

当前数据库向量列沿用 `128` 维。切换模型或修改维度前必须同时调整 Flyway 迁移和 `AGENTMIND_EMBEDDING_DIMENSIONS`，并重建全部文档索引。
不能在同一向量库中混用确定性模拟向量和真实模型向量；两者不属于同一个语义空间，混用会使相似度失去意义。

## 失败策略

- 外部模型发生临时异常时按配置执行有限次数指数退避重试。
- 模型返回数量或维度不符合契约时立即失败，不重复发送相同付费请求。
- 重试耗尽后终止本次摄取或检索，不回退到确定性向量，避免污染真实向量索引。
- 失败内容由现有摄取任务状态机记录，后续可由任务重试流程重新执行。
