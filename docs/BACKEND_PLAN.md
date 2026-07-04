# 后端开发规划

本文档用于规划 Personal Knowledge Agent 后端的阶段性建设。后端开发必须遵守 `AGENTS.md` 中定义的项目定位、架构原则、安全规范和阶段路线。

## 后端定位

后端是 Personal Knowledge Agent 的核心业务和 Agent 编排层，负责用户与知识空间隔离、文档与网页摄取、异步任务、知识检索、RAG 问答、Agent 工具调用、学习系统和评估观测。

后端不是简单的大模型转发服务。所有模型调用、工具调用和知识检索都必须围绕真实业务对象、权限边界、审计记录和可观测能力设计。

## 技术栈

- Java 21
- Spring Boot 3.x
- Maven
- Spring Web
- Spring Validation
- Spring Security 后续引入认证授权
- Spring Data JPA 后续用于持久化
- PostgreSQL + pgvector 后续用于结构化数据与向量检索
- Redis 后续用于缓存、会话和任务状态
- MinIO 后续用于原始文件存储
- Spring AI 后续用于 Chat、Embedding、Vector Store、Tool Calling、Chat Memory 和 RAG

## 开发原则

- 先契约，后实现。
- 先基础工程，后业务闭环。
- 先同步接口，后异步任务。
- 先可验证的小阶段，后复杂 Agent 编排。
- Controller 只处理 HTTP 语义，不直接写业务规则。
- Service 负责用例编排和事务边界。
- Model/DTO 明确区分，不从 Controller 直接暴露数据库实体。
- 所有用户数据必须保留用户或知识空间归属。
- 所有 Agent 工具调用必须可审计。

## 推荐目录结构

第一阶段先落地基础包结构，后续按模块逐步填充：

```text
backend
  pom.xml
  src/main/java/com/agentmind
    AgentMindApplication.java
    common
      exception
      response
    health
      controller
```

后续扩展结构：

```text
com.agentmind
  common
    config
    exception
    response
    security
    validation
  user
  workspace
  document
  ingestion
  knowledge
  chat
  agent
  study
  evaluation
```

## 阶段规划

### Stage 1：Spring Boot 基础工程

目标：

- 创建后端 Maven 工程。
- 添加 Spring Boot 基础依赖。
- 添加统一 API 响应结构。
- 添加全局异常处理。
- 添加健康检查接口。
- 添加基础配置文件。
- 添加后端 README 或运行说明。

验收标准：

- 后端目录结构清晰。
- 代码能表达后续企业级分层方向。
- 健康检查接口契约明确。
- 统一响应格式可复用。
- 没有引入数据库、AI 模型和外部中间件的强依赖。

### Stage 2：核心契约与领域模型落地

目标：

- 用户模型。
- 知识空间模型。
- 文档元数据模型。
- 摄取任务模型。
- Agent 工具调用审计模型。
- 基础 DTO、枚举和接口路径。

验收标准：

- 模型字段能支撑前端页面和后续业务。
- 所有用户资产均包含用户或知识空间归属。
- 状态枚举清晰，便于异步任务流转。

### Stage 3：文件与 URL 摄取接口

目标：

- 文件上传接口。
- URL 采集任务创建接口。
- 摄取任务查询接口。
- 基础校验，包括文件大小、文件类型和 URL 安全。

验收标准：

- 接口不真正做复杂解析也能创建任务。
- 接口可被前端采集中心页面联调。
- URL 采集预留 SSRF 防护边界。

### Stage 4：文档解析与异步任务

目标：

- 文件文本解析。
- 网页正文提取。
- 摄取任务状态流转。
- 原始文件或 HTML 快照存储适配。

验收标准：

- 大文件或网页处理不阻塞请求线程。
- 任务失败原因可追踪。
- 文本清洗和 chunk 切分有独立可测组件。

### Stage 5：Embedding 与向量检索

目标：

- 接入 Embedding 模型。
- 配置 pgvector。
- 保存 chunk 和向量。
- 实现按知识空间过滤的语义检索。

验收标准：

- 删除文档时可同步处理 chunk 和向量。
- 检索结果包含来源元数据和相似度分数。

### Stage 6：RAG 问答

目标：

- RAG 问答接口。
- 引用来源返回。
- 检索链路记录。
- 低置信度拒答或提示资料不足。

验收标准：

- 前端 Agent 问答页面可联调。
- 每次问答可追踪使用了哪些 chunk。

### Stage 7：Agent 工具调用

目标：

- 通过 Spring AI Tool Calling 封装工具。
- 实现创建笔记、生成复习卡片、生成学习计划等工具。
- 记录工具调用审计。

验收标准：

- 每个工具有权限校验和参数校验。
- 工具调用链路可在前端观测。

### Stage 8：学习系统与长期记忆

目标：

- 学习计划。
- 复习卡片。
- 会话摘要。
- 用户学习画像。
- 薄弱知识点分析。

验收标准：

- Agent 能基于用户知识资产生成可执行学习计划。
- 学习行为可持续沉淀。

### Stage 9：评估与可观测

目标：

- RAG 评估集。
- Recall@K、引用覆盖率、响应耗时、Token 消耗。
- 开发者观测接口。

验收标准：

- 可以解释不同检索策略的效果差异。
- 简历项目能讲清楚质量如何衡量。

## 第一阶段开发边界

本阶段只实现 Stage 1，不实现：

- 用户注册登录。
- 数据库持久化。
- 文件上传。
- URL 采集。
- Spring AI 模型调用。
- RAG 检索。
- Agent 工具调用。

这些内容必须等后续阶段根据核心契约逐步实现。
