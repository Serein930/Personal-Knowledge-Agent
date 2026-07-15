# RAG 固定评估联调手册

本文用于验证 Stage 9 的固定评估集、指标计算、基线对比、PostgreSQL 持久化和前端评估面板。

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

## 四、运行与对比

```powershell
$job = @{ datasetId = 1; datasetVersion = 1; topK = 5 } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/v1/workspaces/1/evaluations/jobs" `
  -ContentType "application/json" -Body $job
```

同一版本运行第二次后，响应中的 `baselineJobId` 应指向第一次成功任务。查看对比：

```text
GET http://localhost:8081/api/v1/workspaces/1/evaluations/jobs/{jobId}/comparison
GET http://localhost:8081/api/v1/workspaces/1/evaluations/dashboard
```

基线差值是“当前减基线”。Recall、MRR、引用覆盖率和拒答准确率越高通常越好；耗时、Token 和成本越低通常越好。

## 五、成本配置

价格单位是美元/百万 Token：

```powershell
$env:AGENTMIND_EVALUATION_INPUT_COST = "2.50"
$env:AGENTMIND_EVALUATION_OUTPUT_COST = "10.00"
```

模拟模型没有供应商用量，因此结果会把 `tokenUsageEstimated` 标记为 `true`。真实模型提供用量后优先使用真实值。

## 六、PostgreSQL 模式

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

## 七、自动测试

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
