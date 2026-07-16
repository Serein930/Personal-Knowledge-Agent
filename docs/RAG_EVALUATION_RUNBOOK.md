# RAG 固定评估联调手册

本文用于验证 Stage 9 的固定评估集、异步任务、实验配置快照、指标计算、基线对比、
PostgreSQL 持久化、CI 质量门禁和前端评估面板。

## 一、准备知识数据

先在采集中心上传 Markdown 或 TXT 文档，等待摄取成功，再通过知识检索接口确认目标资料可以命中。
评估集中的可回答题需要填写实际 `chunkId` 或 `documentId`；拒答题不填写期望来源。

建议固定评估集同时包含：

- 能准确命中一个片段的简单题。
- 需要多个片段才能覆盖的组合题。
- 容易召回相似但错误资料的干扰题。
- 知识库范围之外、应该拒答的问题。

## 二、启动默认内存模式

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run
```

默认地址为 `http://localhost:8081`。打开前端“评估观测”页面，可以创建固定评估集并运行任务。

## 三、创建评估集

```powershell
$body = @{
  name = "Java 并发固定回归集"
  description = "验证检索、引用和拒答"
  cases = @(
    @{
      caseKey = "virtual-thread"
      question = "虚拟线程适合哪些任务？"
      expectedRelevantChunkIds = @("替换为实际片段编号")
      expectedRelevantDocumentIds = @()
      expectedRefusal = $false
      expectedAnswerKeywords = @("虚拟线程", "阻塞")
    },
    @{
      caseKey = "unknown-topic"
      question = "资料中没有涉及的主题"
      expectedRelevantChunkIds = @()
      expectedRelevantDocumentIds = @()
      expectedRefusal = $true
      expectedAnswerKeywords = @()
    }
  )
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/v1/workspaces/1/evaluations/datasets" `
  -ContentType "application/json" -Body $body
```

题目调整后调用 `POST /evaluations/datasets/{datasetId}/versions` 创建下一版本。系统不允许覆盖旧版本。

## 四、异步运行、取消与重试

```powershell
$job = @{
  datasetId = 1
  datasetVersion = 1
  experimentName = "混合检索与词法重排实验"
  retrievalStrategy = "HYBRID"
  candidatePoolSize = 20
  rerankStrategy = "LEXICAL"
  topK = 5
  qualityGate = @{
    minimumRecallAtK = 80
    minimumNdcgAtK = 70
    maximumAverageLatencyMillis = 10000
  }
} | ConvertTo-Json -Depth 6
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/v1/workspaces/1/evaluations/jobs" `
  -ContentType "application/json" -Body $job
```

提交接口返回 HTTP 202 和 `PENDING`/`RUNNING` 快照，客户端使用任务编号查询进度：

```text
GET  /api/v1/workspaces/{workspaceId}/evaluations/jobs/{jobId}
POST /api/v1/workspaces/{workspaceId}/evaluations/jobs/{jobId}/cancel
POST /api/v1/workspaces/{workspaceId}/evaluations/jobs/{jobId}/retry
```

运行中取消先进入 `CANCEL_REQUESTED`，执行器会在单题边界结束；只有 `FAILED` 或 `CANCELED`
任务允许重试。重试创建新任务并复用原实验快照，不覆盖历史记录。

同一版本运行第二次后，响应中的 `baselineJobId` 应指向第一次成功任务。查看对比与趋势：

```text
GET http://localhost:8081/api/v1/workspaces/1/evaluations/jobs/{jobId}/comparison
GET http://localhost:8081/api/v1/workspaces/1/evaluations/dashboard
GET http://localhost:8081/api/v1/workspaces/1/evaluations/datasets/{datasetId}/trends
GET http://localhost:8081/api/v1/workspaces/1/evaluations/datasets/{datasetId}/versions/diff?fromVersion=1&toVersion=2
```

基线差值是“当前减基线”。当前实现记录 Recall@K、MRR、NDCG@K、引用覆盖率、拒答准确率、
答案关键词覆盖率、忠实度、答案相关性、检索/重排/生成耗时、Token 和成本。

忠实度和答案相关性当前使用中英文分词后的确定性文本相似度，是离线可重复的近似指标，
不能替代人工判断或大模型裁判。它们的意义是先稳定发现明显回退，后续可平滑替换为模型裁判端口。

当前 `HYBRID` 策略在向量召回的候选池内按“向量分数 70% + 词法相似度 30%”融合，
`LEXICAL` 再对候选进行确定性词法重排。它还不是独立 BM25 倒排召回，因此无法补回完全未进入
向量候选池的文档；现阶段通过冻结候选池大小保证实验可重复，后续接入 OpenSearch 后再升级为双路召回。

## 五、JSON/CSV 导入导出

```text
POST /api/v1/workspaces/{workspaceId}/evaluations/datasets/import?format=JSON
POST /api/v1/workspaces/{workspaceId}/evaluations/datasets/import?format=CSV
GET  /api/v1/workspaces/{workspaceId}/evaluations/datasets/{datasetId}/versions/{version}/export?format=JSON
GET  /api/v1/workspaces/{workspaceId}/evaluations/datasets/{datasetId}/versions/{version}/export?format=CSV
```

导入接口使用 `multipart/form-data`，文件字段名为 `file`。CSV 的列表字段使用 JSON 数组保存，
由 Commons CSV 和 Jackson 共同解析，避免关键词或片段编号中的逗号破坏列结构。导入仍经过正常业务校验。

## 六、成本配置

价格单位是美元/百万 Token：

```powershell
$env:AGENTMIND_EVALUATION_INPUT_COST = "2.50"
$env:AGENTMIND_EVALUATION_OUTPUT_COST = "10.00"
```

模拟模型没有供应商用量，因此结果会把 `tokenUsageEstimated` 标记为 `true`。真实模型提供用量后优先使用真实值。

## 七、PostgreSQL 模式

首次创建数据库容器时，`docker-compose.yml` 会自动执行 `rag_evaluations.sql`。已有数据卷需要手工执行新增脚本：

```powershell
Get-Content -Raw .\backend\src\main\resources\db\schema\rag_evaluations.sql |
  docker compose exec -T agentmind-postgres psql -U agentmind -d agentmind
```

使用 `local` 配置启动后端：

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

此时 `agentmind.evaluation.store=jdbc`，评估集、版本、任务、聚合指标和逐题证据会写入 PostgreSQL。

## 八、自动测试

```powershell
cd D:\Program\AgentMind\backend
mvn "-Dtest=RagEvaluationMetricCalculatorTests,RagEvaluationControllerTests" test
```

常规测试使用内存仓储、确定性向量和模拟模型，不需要 API Key，也不会产生模型费用。

真实 PostgreSQL 集成测试默认跳过，数据库就绪后显式运行：

```powershell
$env:AGENTMIND_EVALUATION_JDBC_INTEGRATION_TEST = "true"
mvn "-Dtest=JdbcRagEvaluationIntegrationTests" test
```

## 九、CI 质量门禁

后端部署并预置固定评估集后，可直接运行：

```powershell
.\scripts\run-rag-quality-gate.ps1 `
  -BaseUrl "http://localhost:8081/api" `
  -WorkspaceId 1 -DatasetId 1 -DatasetVersion 1 `
  -RetrievalStrategy HYBRID -RerankStrategy LEXICAL `
  -MinimumRecallAtK 80 -MinimumNdcgAtK 70
```

脚本会提交任务、轮询终态并检查质量门禁，任何阈值未通过都会返回退出码 1。
`.github/workflows/rag-quality-gate.yml` 提供手动 GitHub Actions 工作流；仓库需要配置
`RAG_EVALUATION_BASE_URL` 密钥，指向 CI 可访问且已准备固定数据的测试环境。
