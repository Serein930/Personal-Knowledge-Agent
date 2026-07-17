# Spring AI 真实 RAG 模型联调手册

## 目标

本手册用于验证 RAG 同步回答和 SSE 流式回答已经从 Mock 生成器切换到 Spring AI `ChatModel`。
普通开发与自动化测试继续默认使用 Mock，`local-ai` 和 `production` profile 明确使用真实模型，避免生产环境因遗漏配置静默退回模拟回答。

## 运行模式

| 模式 | 启动 profile | 回答生成器 | 是否需要真实密钥 |
| --- | --- | --- | --- |
| 离线开发 | 默认 | `mock` | 否 |
| 本地真实联调 | `local,local-ai` | `spring-ai` | 是 |
| 生产环境 | `production` | `spring-ai` | 是 |

`local-ai` 只切换聊天模型，不会强制切换 Embedding。需要同时验证真实向量模型时，按照 `SPRING_AI_EMBEDDING_RUNBOOK.md` 额外设置向量模型环境变量。

## 环境变量

真实模型密钥只能通过当前终端、操作系统密钥存储或外部密钥管理系统注入：

```powershell
$env:OPENAI_API_KEY = "你的真实模型密钥"
```

可选配置：

```powershell
$env:AGENTMIND_CHAT_MODEL = "gpt-4o-mini"
$env:AGENTMIND_RAG_MODEL_NAME = "gpt-4o-mini"
$env:AGENTMIND_CHAT_TEMPERATURE = "0.2"
$env:AGENTMIND_RAG_FAILURE_FALLBACK_ENABLED = "true"
```

说明：

- `AGENTMIND_CHAT_MODEL` 是实际发送给 Spring AI 的供应商模型名称。
- `AGENTMIND_RAG_MODEL_NAME` 是写入回答元数据和审计记录的模型名称，通常应与实际模型一致。
- 温度默认 `0.2`，减少知识问答中的随机扩写。
- 本地占位密钥只服务于默认 Mock 启动；`local-ai` 和 `production` 都要求真实 `OPENAI_API_KEY`。
- 禁止把密钥写入 YAML、测试代码、截图、日志或 Git 提交。

## 启动本地真实模式

先启动本地 PostgreSQL 等依赖，然后执行：

```powershell
Set-Location D:\Program\AgentMind\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,local-ai"
```

服务默认监听 `http://localhost:8081`。启动日志中不应出现 Mock 回答生成器处理真实请求的记录。

## 手动 API 联调

1. 检查健康接口：

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/health"
```

2. 在知识空间上传并完成一份 Markdown 或 TXT 文档摄取。
3. 调用检索接口确认存在引用上下文。
4. 调用同步 RAG：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/v1/workspaces/1/rag/chat" `
  -ContentType "application/json" `
  -Body '{"question":"线程池为什么能够提升任务处理效率？","topK":5}'
```

5. 检查响应：

- `generationMetadata.answerGenerator` 应为 `spring-ai`。
- `generationMetadata.modelName` 应与环境变量一致。
- `usage.totalTokens` 应来自真实供应商响应且大于零。
- `citations` 应包含本次回答使用的知识片段。
- 没有可靠资料时应执行低置信度拒答，不应调用模型编造答案。

6. 使用前端或 SSE 客户端调用流式接口，确认文本增量、引用、完成事件顺序正确，并检查完成事件中的 Token 用量。

## 独立真实模型测试

设置 `OPENAI_API_KEY` 后运行：

```powershell
Set-Location D:\Program\AgentMind\backend
.\mvnw.cmd "-Dtest=SpringAiRagManualIntegrationTests" test
```

该测试会产生一次真实模型调用和少量费用，用于验证 Spring AI 自动配置、真实回答和 Token 元数据。未设置密钥时自动跳过，常规持续集成不会访问付费模型。

## 工具调用

真实模型默认只能看到只读工具：

- `knowledge_search`：在当前知识空间检索知识。
- `document_read_chunk`：读取当前知识空间的指定文档片段。

工具执行仍经过用户、知识空间和会话上下文校验，并受最大往返次数限制。创建笔记、复习卡片等写工具不会直接暴露给模型，必须走待确认单和确认令牌流程。

## 失败策略

默认开启安全降级：

```text
AGENTMIND_RAG_FAILURE_FALLBACK_ENABLED=true
```

同步模型失败时：

- 保存失败或降级审计，日志保留供应商异常用于排障。
- 客户端只收到稳定的中文降级提示，不会收到密钥、供应商响应体或内部异常细节。
- 回答标记为拒答，不把降级文本伪装成知识库结论。

SSE 模型失败时：

- 第一个文本增量发出前失败，可以返回安全降级文本。
- 已经发出部分模型文本后失败，发送错误终态，不把降级文本拼接到半截回答后。
- 客户端断开和超时仍保持取消状态，并确保一次流式调用只保存一条最终审计。

调试供应商配置时可以临时关闭降级，让原始异常直接在本地调用栈中暴露：

```powershell
$env:AGENTMIND_RAG_FAILURE_FALLBACK_ENABLED = "false"
```

该模式仅用于受控本地排障，生产环境通常保留安全降级并结合模型调用审计、指标和告警处理故障。

## 回滚

停止服务、清除本地真实模型环境变量后，以默认 profile 启动：

```powershell
Set-Location D:\Program\AgentMind\backend
.\mvnw.cmd spring-boot:run
```

默认模式恢复为 Mock，不访问真实聊天模型。
