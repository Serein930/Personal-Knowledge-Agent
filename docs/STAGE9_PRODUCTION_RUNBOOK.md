# Stage 9 生产化联调手册

本文验证评估任务租约恢复、OpenSearch BM25 双路召回、RRF 融合、可替换模型裁判和
Micrometer/OpenTelemetry 链路观测。默认开发模式仍不依赖外部模型、OpenSearch 或 OTLP 采集端。

## 一、能力边界

- PostgreSQL 是多实例任务租约的唯一裁决者，线程池投递本身不代表取得执行权。
- 只有当前租约持有者且租约未过期时，才能写入评估进度、成功、失败或取消终态。
- 进程失联后，运行中任务保留逐题结果并恢复为待执行；取消中的任务恢复为已取消。
- 混合检索使用向量与 BM25 两个独立榜单，再通过 RRF 融合名次。
- 模型裁判只计算质量分，不允许修改评估任务、知识资产或 Agent 工具状态。
- 指标标签不包含用户、知识空间、任务编号等高基数信息。

## 二、启动 PostgreSQL 与 OpenSearch

```powershell
cd D:\Program\AgentMind
docker compose --profile opensearch up -d agentmind-postgres agentmind-opensearch
docker compose ps
```

已有 PostgreSQL 数据卷必须通过后端启动或专用迁移任务应用 Flyway，不能手工执行建表脚本：

```powershell
Set-Location .\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

启动日志出现 Flyway 校验或迁移失败时必须停止发布并修复，禁止通过关闭校验绕过。

组合启用 JDBC、pgvector 和 OpenSearch：

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local,opensearch"
```

上传或重新摄取文档后，片段会同时写入 pgvector 和 OpenSearch。历史文档若只存在 pgvector，
需要重新摄取一次才能参与 BM25 召回。

## 三、验证 BM25 与 RRF

1. 上传一份包含罕见精确术语的 Markdown 文档。
2. 创建一条问题中包含该术语的固定评估题。
3. 分别运行 `VECTOR` 与 `HYBRID` 实验，保持评估集版本、候选池和 TopK 一致。
4. 查看逐题 `retrievedSources`、Recall@K、MRR 和 NDCG@K。

真实 OpenSearch 集成测试：

```powershell
$env:AGENTMIND_RUN_OPENSEARCH_INTEGRATION = "true"
$env:AGENTMIND_OPENSEARCH_URL = "http://localhost:9200"
mvn "-Dtest=OpenSearchKeywordIndexIntegrationTests" test
```

## 四、验证任务租约和实例异常恢复

联调时可以临时缩短租约：

```powershell
$env:AGENTMIND_EVALUATION_LEASE_DURATION = "15s"
$env:AGENTMIND_EVALUATION_HEARTBEAT_INTERVAL = "5s"
$env:AGENTMIND_EVALUATION_RECOVERY_FIXED_DELAY_MILLIS = "5000"
```

1. 使用 `local` 配置启动后端并提交包含多道题的评估任务。
2. 在任务处于 `RUNNING` 时强制结束后端进程。
3. 等待租约超过 15 秒后重新启动后端。
4. 查询原任务，确认 `attemptCount` 和 `recoveryCount` 增加，已完成题目没有重复写入。
5. 对 `CANCEL_REQUESTED` 状态重复该过程，确认任务最终进入 `CANCELED`。

多个实例可配置不同实例编号，便于日志定位：

```powershell
$env:AGENTMIND_EVALUATION_INSTANCE_ID = "agentmind-local-a"
```

## 五、切换真实模型裁判

默认配置：

```yaml
agentmind:
  evaluation:
    judge:
      type: deterministic
```

使用 Spring AI 裁判前配置模型和密钥：

```powershell
$env:OPENAI_API_KEY = "仅保存在当前终端的密钥"
$env:SPRING_AI_MODEL_CHAT = "openai"
$env:AGENTMIND_EVALUATION_JUDGE_TYPE = "spring-ai"
$env:AGENTMIND_EVALUATION_JUDGE_MODEL = "实际使用的模型名称"
mvn spring-boot:run "-Dspring-boot.run.profiles=local,opensearch"
```

逐题检查 `judgeEvidence`：正常真实评分时 `judgeType=spring-ai` 且 `fallbackUsed=false`；
调用失败并允许降级时，`judgeType=deterministic` 且 `fallbackUsed=true`。常规 CI 始终使用默认裁判，
不会产生模型费用。

## 六、启用 OpenTelemetry

应用已通过 Micrometer Observation 观测检索、重排、生成和裁判阶段，并记录任务结果、续租失败和
任务恢复计数。默认不向外部发送链路。

```powershell
$env:AGENTMIND_TRACING_ENABLED = "true"
$env:AGENTMIND_TRACING_SAMPLING_PROBABILITY = "1.0"
$env:AGENTMIND_OTLP_TRACING_ENDPOINT = "http://localhost:4318/v1/traces"
```

本地可通过 Actuator 查看指标：

```text
GET /actuator/metrics/agentmind.rag.evaluation.jobs
GET /actuator/metrics/agentmind.rag.evaluation.lease.recoveries
GET /actuator/metrics/agentmind.rag.evaluation.lease.renewal.failures
GET /actuator/metrics/agentmind.rag.evaluation.phase
```

## 七、默认自动测试

```powershell
cd D:\Program\AgentMind\backend
mvn test
```

默认测试覆盖租约排他领取和恢复、BM25 知识空间隔离、RRF 融合、确定性裁判证据、指标计数及
既有评估 API。OpenSearch、PostgreSQL 和真实模型均为显式联调项。
