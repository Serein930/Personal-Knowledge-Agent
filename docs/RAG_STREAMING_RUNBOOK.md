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
4. `complete`：发送回答长度、增量数量、生成元数据和令牌用量，并正常关闭连接。

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

## 当前边界

- 当前尚未保存会话消息，`conversationId` 只透传请求值。
- 当前令牌用量仍为零，后续需要从真实模型响应元数据中提取。
- 当前没有心跳事件；若生产代理存在较短空闲超时，可以在部署阶段增加心跳。
- 当前没有认证和知识空间归属校验，接入用户体系后必须补齐权限检查。
