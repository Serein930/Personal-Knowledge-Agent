# Spring AI 真实模型联调手册

本文档用于记录 Stage 6 阶段的真实聊天模型本地开关、环境变量配置、手动联调流程和失败降级策略。日常开发默认仍使用模拟回答生成器，避免本地启动、自动化测试和前端联调依赖真实模型密钥。

## 默认模式

默认配置不会调用真实聊天模型：

```yaml
spring:
  ai:
    model:
      chat: none

agentmind:
  rag:
    answer-generator: mock
    model-name: mock-local
    tool-calling-enabled: true
    max-tool-round-trips: 4
    spring-ai-failure-fallback-enabled: true
    observation-store: memory
```

默认模式适合：

- 前端页面联调。
- 文档摄取和检索链路验证。
- 自动化测试。
- 没有真实模型密钥的开发环境。

## 环境变量

真实模型联调前，只在本机终端设置环境变量，不要写入仓库文件：

```powershell
$env:OPENAI_API_KEY="你的真实模型密钥"
```

可选模型配置：

```powershell
$env:SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL="gpt-4o-mini"
```

说明：

- `OPENAI_API_KEY` 由 Spring AI OpenAI 自动配置读取。
- 仓库中的 `agentmind-local-placeholder` 只是本地占位值，用于避免默认模拟模式启动时报缺失密钥。
- 不要把真实密钥写入 `application.yml`、`application-local.yml`、测试文件或提交记录。

## 启动真实模型模式

在后端目录执行：

```powershell
cd D:\Program\AgentMind\backend

mvn spring-boot:run `
  "-Dspring-boot.run.arguments=--spring.ai.model.chat=openai --agentmind.rag.answer-generator=spring-ai --agentmind.rag.model-name=gpt-4o-mini"
```

关键开关说明：

- `spring.ai.model.chat=openai`：允许 Spring AI 创建真实聊天模型客户端。
- `agentmind.rag.answer-generator=spring-ai`：让项目使用真实模型回答生成适配器。
- `agentmind.rag.model-name=gpt-4o-mini`：写入项目侧观测元数据，方便日志和后续审计表识别模型。
- `agentmind.rag.tool-calling-enabled=true`：向真实模型提供当前只读工具白名单。
- `agentmind.rag.max-tool-round-trips=4`：限制同步问答中模型与工具的最大往返次数，防止异常循环。

当前模型可见工具：

- `knowledge_search`：对应内部工具 `knowledge.search`，按当前知识空间执行语义检索。
- `document_read_chunk`：对应内部工具 `document.read_chunk`，读取当前知识空间中的指定文档片段。

模型看到的是符合供应商命名限制的下划线名称，后端执行时仍会映射回内部白名单名称。写工具不会自动暴露给模型。

## 手动联调流程

1. 启动后端。
2. 先确认健康检查：

```text
GET http://localhost:8080/api/v1/health
```

3. 上传一份 Markdown 或纯文本资料，确保知识空间中有可检索片段。
4. 调用检索接口，确认当前知识空间能返回引用片段：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/knowledge/search" `
  -ContentType "application/json" `
  -Body '{"query":"thread pool worker threads","topK":5}'
```

5. 调用问答接口：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/rag/chat" `
  -ContentType "application/json" `
  -Body '{"question":"线程池为什么能提升后端任务处理效率？","topK":5}'
```

6. 检查响应字段：

- `citations` 应返回引用来源。
- `retrievalContext.promptVersion` 应显示当前提示词版本。
- `generationMetadata.answerGenerator` 应为 `spring-ai`。
- `generationMetadata.modelName` 应为启动参数中配置的模型名称。
- `generationMetadata.refused` 为 `false` 时表示真实模型成功返回回答。
- `toolCalls` 在模型使用工具时返回调用状态、结果摘要与耗时；未调用工具时为空数组。

7. 查询工具审计，确认记录绑定到本次会话和消息：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/workspaces/1/agent/tool-calls?page=1&pageSize=20"
```

如需临时关闭模型自动工具选择，可在启动参数中增加：

```text
--agentmind.rag.tool-calling-enabled=false
```

## 失败降级策略

真实模型可能因为密钥错误、网络失败、供应商限流或超时而不可用。项目默认开启失败降级：

```yaml
agentmind:
  rag:
    spring-ai-failure-fallback-enabled: true
```

开启后，如果真实模型调用失败：

- 后端不会直接返回 500。
- `SpringAiChatModelAnswerGenerator` 会返回一段可解释的降级回答。
- `generationMetadata.refused` 会变为 `true`。
- `generationMetadata.refusalReason` 会记录真实模型调用失败原因。
- 应用日志会记录一次失败事件和一次降级事件。

如果你正在调试真实模型配置，想让错误直接暴露，可以关闭降级：

```powershell
mvn spring-boot:run `
  "-Dspring-boot.run.arguments=--spring.ai.model.chat=openai --agentmind.rag.answer-generator=spring-ai --agentmind.rag.spring-ai-failure-fallback-enabled=false"
```

关闭后，真实模型异常会继续向上抛出，便于定位密钥、网络和供应商配置问题。

## 可观测记录规划

当前阶段已经加入轻量观测模型：

- `RagModelCallObservation`
- `RagModelCallStatus`

目前这些字段只进入应用日志，暂不落库。后续进入评估与可观测阶段时，可以扩展为数据库表或链路追踪事件，建议保留以下字段：

- 知识空间编号。
- 会话编号和消息编号。
- 提示词版本。
- 回答生成器类型。
- 模型名称。
- 引用数量。
- 是否拒答。
- 调用状态。
- 耗时毫秒。
- 回答长度。
- 失败原因。
- 令牌用量。

## 回滚到模拟模式

停止服务后使用默认配置重新启动即可：

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run
```

默认模拟模式不会调用真实模型，也不需要真实密钥。
