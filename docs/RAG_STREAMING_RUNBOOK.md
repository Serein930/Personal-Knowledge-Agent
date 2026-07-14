# 检索增强生成流式问答联调手册

本文档用于验证 Stage 6 的服务器发送事件流式问答接口、事件顺序、中文编码、异常结束和模型调用审计。

## 启动配置

默认配置使用本地模拟生成器，不需要模型密钥：

```yaml
agentmind:
  rag:
    answer-generator: mock
    model-name: mock-local
    stream-timeout-millis: 60000
    stream-chunk-size: 24
    tool-calling-enabled: true
```

- `stream-timeout-millis`：单次 SSE 会话和 Spring AI 响应流的最长等待时间，单位为毫秒。
- `stream-chunk-size`：模拟回答和降级回答每个文本增量的最大字符数，最小有效值为 1。

## 调用流式接口

先启动后端：

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run
```

在另一个 PowerShell 终端中调用：

```powershell
curl.exe -N `
  -X POST "http://localhost:8080/api/v1/workspaces/1/rag/chat/stream" `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  --data-raw '{"question":"线程池为什么可以复用工作线程？","topK":5}'
```

`-N` 用于关闭命令行客户端的输出缓冲，便于逐条观察事件。响应类型为：

```text
text/event-stream;charset=UTF-8
```

## 事件协议

正常调用的事件顺序如下：

1. `metadata`：会话编号、消息编号、提示词版本、生成器、模型和引用数量。
2. `citation`：每个检索引用单独发送；没有可靠检索结果时不会产生该事件。
3. `delta`：按递增序号发送回答文本增量。
4. `tool_call`：可选事件；真实模型调用只读工具后，按调用顺序返回脱敏审计摘要。
5. `complete`：发送回答长度、增量数量、工具摘要、生成元数据和令牌用量，并正常关闭连接。

没有工具调用时不会发送 `tool_call`，`complete.toolCalls` 返回空数组。发生工具调用时，当前事件顺序为
`metadata -> citation -> delta -> tool_call -> complete`。

异常调用在已经发送的事件之后追加：

```text
event:error
data:{"code":"STREAM_GENERATION_FAILED","message":"流式回答生成失败，请稍后重试","retryable":true}
```

每条事件都带有 `id`。前端应按照 `delta.sequence` 追加文本，不应把 SSE 帧本身直接展示给用户。

## 前端接入注意事项

浏览器原生 `EventSource` 只支持 GET 请求，而当前接口需要通过 POST 发送问题和检索参数。因此前端应使用
`fetch` 读取响应流，并使用成熟的 SSE 解析库处理跨数据块边界的事件，不要简单按换行符手动切割网络数据。

前端建议维护以下消息状态：

- 收到 `metadata` 后创建临时助手消息。
- 收到 `citation` 后更新引用来源区域。
- 收到 `delta` 后按序追加正文。
- 收到 `tool_call` 后更新本次回答的工具执行轨迹。
- 收到 `complete` 后把消息标记为完成。
- 收到 `error` 或网络断开后把消息标记为失败，并根据 `retryable` 决定是否展示重试操作。

## 断连、超时与异常

- 客户端断开或写响应失败后，服务会停止继续发送增量，并向生成器传播取消信号。
- SSE 会话达到配置超时时间后会传播超时取消信号；Spring AI 响应流还会执行同样时长的模型侧超时。
- 模型在首个增量前失败时，可以按已有配置返回流式降级回答。
- 模型已经发出部分正文后再失败时，不会拼接降级答案，而是发送错误终态，避免形成语义混杂的回答。
- 默认模拟生成器执行很快，手动按 `Ctrl+C` 时可能已经完成；取消路径由自动化测试稳定覆盖。

## 审计验证

查询当前知识空间的最终模型调用记录：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/workspaces/1/rag/model-calls?page=1&pageSize=20"
```

流式调用只保存一条最终记录：

- `SUCCEEDED`：回答和完成事件正常发送。
- `FALLBACK`：真实模型在首个增量前失败，并成功发送降级回答。
- `FAILED`：模型或生成过程异常结束。
- `CANCELLED`：客户端断开或会话超时导致生成取消。

`STARTED` 只写应用日志，不保存到仓库，因此一次流式调用不会同时出现开始和最终两条审计记录。

## 与短期会话记忆协作

- 不传 `conversationId` 时会自动创建新会话，`metadata` 和 `complete` 事件返回真实会话编号。
- 传入当前知识空间中的会话编号时，会加载最近的已完成消息辅助理解追问。
- 流式回答完整生成后才保存助手正文。
- 失败和取消会保存消息状态与原因，但正文为空，不会进入后续提示词。

完整说明见 `docs/CHAT_MEMORY_RUNBOOK.md`。

## 当前边界

- 当前会话消息保存在内存中，服务重启后会清空。
- 当前令牌用量仍为零，后续需要从真实模型响应元数据中提取。
- 当前没有心跳事件；若生产代理存在较短空闲超时，可以在部署阶段增加心跳。
- 当前使用演示用户权限边界和知识空间/会话联合校验；接入 Spring Security 后需要把演示用户替换为真实认证主体。
