# AGENTS.md

本文件是本仓库的项目开发宪章。任何 AI 助手或开发者在修改代码前，都应该先阅读本文件，并遵守其中的项目定位、技术选型、模块边界和开发规范。

## 项目定位

Personal Knowledge Agent 是一个以 Java 为主要后端语言的个人知识管理 Agent 平台。

本项目不是普通的 AI 聊天机器人，也不是简单的“上传文档后问答”系统。它的目标是帮助用户收集、整理、检索、复习和复用个人学习资料，例如 PDF、Markdown 笔记、网页文章、技术博客、官方文档、代码片段和面试资料。

项目长期目标是构建一个兼具真实实用性和工程技术深度的 Agent 项目，可用于个人长期学习，也可作为简历项目和面试项目展示。

## 产品目标

- 支持从文件和网页链接中采集个人知识。
- 将原始资料转化为可检索、可问答、可复习的知识资产。
- 提供基于 RAG 的知识问答，并返回引用来源。
- 通过 Agent 工具生成笔记、摘要、复习卡片和学习计划。
- 跟踪用户学习历史、薄弱知识点和复习进度。
- 提供 RAG 检索质量、模型调用和 Agent 行为的可观测能力。
- 保持项目本身具备真实可用性，而不是只做演示 Demo。

## 非目标

- 不做简单的大模型聊天套壳。
- 不在 Controller 中直接调用模型。
- 不把业务逻辑、持久化逻辑和 AI 编排逻辑混在一个类中。
- 不为了炫技提前引入与当前阶段无关的大型基础设施。
- 不将 API Key、Token、密码、私钥等敏感信息提交到 Git。
- 不随意添加偏离项目定位的功能。

## 主要技术栈

后端技术：

- Java 21
- Spring Boot 3.x
- Spring AI 作为主要 AI 应用框架
- Spring Web 提供 REST API
- Spring Security 处理认证和授权
- Spring Data JPA 或 MyBatis-Plus 处理数据访问
- Bean Validation 处理请求参数校验

AI 与 Agent：

- Spring AI Chat Model 抽象
- Spring AI Embedding Model 抽象
- Spring AI Vector Store 抽象
- Tool Calling 实现 Agent 工具调用
- Chat Memory 处理会话上下文
- RAG 检索增强生成
- 基于引用来源的答案溯源
- 模型评估和可观测能力

数据与存储：

- PostgreSQL 存储结构化数据
- pgvector 存储向量并支持相似度检索
- Redis 用于缓存、会话和轻量任务状态
- MinIO 存储原始文件
- Elasticsearch 或 OpenSearch 可作为后续混合检索扩展

文档与网页采集：

- Apache Tika 用于多格式文档文本提取
- PDFBox 用于 PDF 精细化解析
- Jsoup 用于 HTML 抓取和解析
- Readability 类正文提取方案用于网页正文抽取
- Playwright 或 Selenium 可作为 JavaScript 渲染页面的后续兜底方案

前端技术：

- React
- TypeScript
- Vite
- Ant Design 或其他统一组件库
- Markdown 渲染
- PDF 预览
- ECharts 或 Recharts 用于数据可视化
- SSE 或 WebSocket 用于流式响应

工程与部署：

- Docker Compose 管理本地开发依赖
- Maven Wrapper 统一后端构建方式
- GitHub Actions 在项目结构稳定后用于 CI
- OpenAPI、Swagger 或 Knife4j 用于接口文档

## 架构原则

- 使用清晰的分层架构和模块边界。
- Controller 只处理 HTTP 入参、出参和状态码，不承载业务逻辑。
- Application Service 负责编排用例、事务和跨模块流程。
- Domain Service 负责与传输层无关的核心业务规则。
- Infrastructure Adapter 负责封装外部系统，例如大模型、对象存储、向量库、网页抓取器。
- AI 编排逻辑必须显式、可测试、可追踪。
- 文档摄取、向量化、知识点抽取等耗时任务应异步执行。
- 依赖个人知识库生成的答案必须尽量返回引用来源。
- Agent 工具调用必须具备权限校验和审计记录。

## 推荐后端包结构

除非后续架构设计明确调整，否则默认采用以下包结构：

```text
com.agentmind
  AgentMindApplication
  common
    config
    exception
    response
    security
    validation
  user
    controller
    service
    model
    repository
  workspace
    controller
    service
    model
    repository
  document
    controller
    service
    model
    repository
    parser
    chunk
  ingestion
    service
    task
    web
  knowledge
    service
    model
    repository
    vector
  chat
    controller
    service
    memory
    model
  agent
    service
    tool
    planner
    audit
  study
    controller
    service
    flashcard
    plan
  evaluation
    controller
    service
    metric
```

## 核心功能模块

### 1. 用户与知识空间模块

- 用户注册和登录。
- 个人知识空间管理。
- 按知识空间隔离文档、向量、会话和学习记录。
- 基础角色和权限模型。
- 用户模型、语言、学习目标等偏好配置。

### 2. 文档摄取模块

- 支持上传 PDF、Markdown、TXT、Word、HTML 和代码文件。
- 将原始文件存储到对象存储。
- 提取文本和元数据。
- 清洗噪声内容。
- 将内容切分为语义 chunk。
- 生成 embedding。
- 保存 chunk 元数据和向量。
- 跟踪文档摄取状态和失败原因。

### 3. 网页文章采集模块

- 支持提交 CSDN、博客园、掘金、官方文档、GitHub README、个人博客等网页链接。
- 校验 URL 安全性。
- 抓取 HTML 内容。
- 提取标题、作者、发布时间、来源站点和正文。
- 尽量去除导航、广告、评论和推荐内容。
- 必要时保存原始 HTML 快照。
- 通过 URL 和正文 hash 检测重复内容。
- 当网页内容变化时支持版本管理。

### 4. RAG 知识问答模块

- 默认只在用户当前选择的知识空间中检索。
- 支持向量检索。
- 后续支持关键词检索和混合检索。
- 支持按文档类型、标签、来源、时间等元数据过滤。
- 返回引用来源，包括文档标题、chunk id、相似度分数和原文片段。
- 当没有可靠资料时，应拒答或提示资料不足。
- 记录 prompt、检索结果、模型输出、token 消耗、响应耗时和用户反馈。

### 5. Agent 工具调用模块

初始阶段只实现实用且边界清晰的工具：

- 检索文档。
- 读取文档片段。
- 创建笔记。
- 生成复习卡片。
- 生成学习计划。
- 分析薄弱知识点。
- 创建复习任务。
- 总结指定主题。

所有工具调用都必须记录日志。会修改数据的工具必须校验用户权限和知识空间归属。

### 6. 长期记忆模块

- 保存会话摘要。
- 统计用户高频提问主题。
- 记录已掌握和薄弱知识点。
- 保存用户学习目标和偏好。
- 避免把完整历史会话全部传给模型。
- 结合短期上下文和长期摘要记忆生成回答。

### 7. 学习系统模块

- 从文档和网页文章中生成复习卡片。
- 支持复习状态和掌握程度。
- 根据主题和时间生成学习计划。
- 跟踪学习进度。
- 根据薄弱知识点推荐复习内容。

### 8. 评估与可观测模块

- 记录每一次 RAG 调用链路。
- 记录检索策略、topK、相似度分数、prompt 版本、模型名称、耗时和 token 消耗。
- 维护一份小型评估集。
- 对比不同 chunk 策略、topK、混合检索和 rerank 策略。
- 跟踪 Recall@K、引用覆盖率、回答可用性、平均耗时和用户反馈。

## RAG 设计规范

- chunk 必须保留来源元数据。
- 内容切分应尽量遵循语义结构。
- Markdown 优先按标题层级切分。
- PDF 解析应尽量去除重复页眉、页脚和页码。
- 代码类文档应尽量避免在类或方法中间切断。
- 检索必须按用户和知识空间过滤。
- 基于用户私有知识生成的答案必须尽量附带引用。
- 当检索置信度较低时，应说明现有资料不足。
- 不得在缺少检索依据的情况下声称用户资料中存在某个事实。

## 网页采集规范

- 只允许 `http` 和 `https` URL。
- 必须阻止本地地址、内网地址和回环地址，避免 SSRF。
- 设置请求超时时间和最大响应体大小。
- 尊重站点 robots、频率限制和个人学习用途边界。
- 保存来源 URL 和抓取时间。
- 正文提取逻辑必须可替换，因为不同网站结构不同。
- 不得把某一个网站的 DOM 结构当成通用规则。

## 安全规范

- 禁止提交任何敏感信息。
- API Key 应使用环境变量或本地忽略配置。
- 所有请求 DTO 必须做参数校验。
- 每个文档、向量、会话、笔记、卡片和计划都必须校验用户和知识空间归属。
- 任何会写入数据的 Agent 工具都必须做授权校验。
- 未来集成外部工具时必须使用工具白名单和审计日志。
- 上传文件必须校验大小、扩展名和内容类型。
- URL 摄取必须防止 SSRF。

## API 设计规范

- 常规 CRUD 和任务管理使用 REST API。
- 聊天流式响应使用 SSE 或 WebSocket。
- 使用统一响应结构。
- 使用清晰的请求 DTO 和响应 DTO。
- Controller 不直接返回持久化实体。
- 列表接口必须支持分页。
- 常见失败场景应有稳定错误码。

## 数据库设计规范

- 使用明确的主键。
- 重要实体包含 `created_at` 和 `updated_at`。
- 涉及用户数据的表必须包含用户或知识空间归属字段。
- 异步任务必须有状态字段。
- 向量 chunk 的元数据必须可查询。
- 删除文档时必须同步删除或标记失效对应 chunk 和向量。
- 面向用户的知识资产优先使用软删除。

## 代码规范

- 优先编写清晰、直接、可维护的代码。
- 避免为了炫技引入复杂抽象。
- 方法长度应保持在易于理解和测试的范围内。
- 使用构造器注入。
- 在 Service 边界控制事务。
- 使用领域化异常和统一异常处理。
- 日志记录关键状态变化，不记录无意义噪声。
- 不允许静默吞掉异常。
- 对业务规则、解析逻辑、chunk 策略和安全敏感逻辑编写测试。
- 引入新依赖前必须确认其服务于当前阶段目标。

## 测试策略

- chunk 策略、解析逻辑、URL 校验和权限校验应编写单元测试。
- Repository 和核心 Service 流程应编写集成测试。
- 关键 Controller 接口应编写 API 测试。
- RAG 评估测试应尽量使用固定数据集和 mock 模型，保证可重复。
- 常规 CI 不应依赖真实付费模型调用。

## Git 与提交规范

使用 Conventional Commits：

- `chore: initialize project structure`
- `feat: add document ingestion pipeline`
- `fix: prevent SSRF in web ingestion`
- `docs: update architecture guide`
- `test: add chunking strategy tests`
- `refactor: extract vector search adapter`

按有意义的开发阶段或功能提交。避免在同一个 commit 中混入无关修改。

## 开发阶段规划

### Stage 1：项目基础

- 创建 Spring Boot 后端工程。
- 添加基础配置。
- 添加健康检查接口。
- 添加统一响应结构和异常处理。
- 编写初始 README 和架构说明。
- 视情况添加 PostgreSQL、Redis、MinIO 的 Docker Compose。

### Stage 2：用户、知识空间与文档元数据

- 用户模型。
- 知识空间模型。
- 文档元数据模型。
- 基础认证和授权。
- 知识空间隔离规则。

### Stage 3：文件文档摄取

- 文件上传。
- 对象存储集成。
- 文本提取。
- 内容分块。
- 异步摄取任务状态。

### Stage 4：网页文章采集

- URL 提交。
- URL 安全校验。
- HTML 抓取和解析。
- 正文提取。
- 重复检测和版本管理。

### Stage 5：Embedding 与向量检索

- 集成 Embedding 模型。
- 配置 pgvector。
- 保存 chunk 向量。
- 实现按知识空间过滤的语义检索。

### Stage 6：RAG 聊天

- 聊天接口。
- 检索增强 prompt 构造。
- 流式响应。
- 引用来源返回。
- 聊天历史和短期记忆。

### Stage 7：Agent 工具

- 集成 Tool Calling。
- 生成笔记。
- 生成复习卡片。
- 生成学习计划。
- 记录工具调用审计日志。

### Stage 8：长期记忆与学习系统

- 会话摘要。
- 学习画像。
- 知识盲区分析。
- 复习计划。
- 复习卡片工作流。

### Stage 9：评估与可观测

- RAG 调用链路记录。
- 评估数据集。
- 检索指标。
- Token 和耗时统计。
- 面向开发者的评估面板。

## 每次开发前检查清单

在修改代码前必须完成：

1. 阅读本 `AGENTS.md`。
2. 检查当前 Git 状态。
3. 确认当前所属开发阶段。
4. 将修改范围控制在当前需求或阶段内。
5. 不随意修改公共接口，除非这是本次需求的一部分。
6. 不将密钥、机器本地配置和临时文件提交到 Git。
7. 运行相关测试；如果无法运行，需要说明原因。

## 简历定位

项目可以按如下方式描述：

```text
Personal Knowledge Agent Platform
基于 Java 21、Spring Boot 和 Spring AI 构建个人知识管理 Agent 平台，支持文档摄取、网页文章采集、RAG 知识问答、引用溯源、Agent 工具调用、学习计划生成、复习卡片、长期记忆和 RAG 评估。
```

核心简历关键词：

- Java 21
- Spring Boot
- Spring AI
- RAG
- Tool Calling
- pgvector
- PostgreSQL
- Redis
- MinIO
- 文档摄取
- 网页文章采集
- 长期记忆
- RAG 评估
- 可观测性
- Agent 工程化

## 最终原则

始终优先保证项目真实有用。技术深度应该来自对真实问题的解决、清晰的架构设计、可衡量的质量改进和可维护的代码实现。
