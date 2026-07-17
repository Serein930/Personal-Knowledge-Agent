# 受保护预发布环境部署与验收手册

## 一、阶段目标

本阶段用于把仓库中的生产发布能力部署到真实、受保护、可审计的预发布环境，并运行完整证据工作流。最终需要得到：

- 真实 OIDC 登录与资源服务器验证结果。
- PostgreSQL、pgvector、Redis、MinIO、OpenSearch 的真实依赖链路结果。
- Vault 短期秘密渲染和版本化轮换记录。
- 异地备份可恢复证明，以及真实 RPO、RTO。
- 正式规格资源上的 P95、P99、错误率和最大安全吞吐量。
- 单实例故障期间的连续失败次数和副本恢复时间。

仓库不会生成或填写虚假指标。本手册中的“部署完成”必须以受保护 Runner 实际执行成功为准。

## 二、推荐拓扑

至少准备以下隔离资源：

| 资源 | 最低用途要求 |
| --- | --- |
| 主验收集群 | Docker Swarm 管理节点和工作节点，运行两个稳定后端、一个灰度后端和两个网关副本 |
| 灾备验收节点 | 独立 Docker Engine，只运行可丢弃恢复环境，不连接主集群管理面和持久卷 |
| PostgreSQL | 启用 pgvector，使用独立预发布数据库、备份账号和连接池监控 |
| Redis | 启用认证与传输加密，供限流和会话记忆使用 |
| MinIO | 使用独立桶、最小权限账号、版本控制和生命周期策略 |
| OpenSearch | 使用独立索引、认证、快照仓库和集群健康监控 |
| OIDC 身份提供方 | 提供固定 issuer、audience 和验收用户，访问令牌不能写入仓库 |
| Vault | 使用审计日志、最小权限策略、短期认证和受限渲染目录 |
| 异地备份仓库 | 与主集群隔离的对象存储，启用版本控制、保留策略和只读恢复凭据 |
| 可观测平台 | 接收 Prometheus 指标和 OpenTelemetry 链路，保存容量及故障窗口数据 |

预发布环境应使用独立域名和有效 TLS 证书。后端 `8081` 不得从公网直接访问，所有外部请求必须经过 HTTPS 网关。

## 三、Runner 准备

GitHub 仓库需要两个自托管 Runner：

### 主验收 Runner

标签：

```text
self-hosted
agentmind-staging
```

必须安装 PowerShell 7、Docker、Vault CLI 和 Cosign，并连接到主验收 Swarm 管理节点。运行账号必须能读取 Vault Agent 配置、访问受限渲染目录、拉取 GHCR 候选镜像和管理 AgentMind 栈，但不应拥有无关主机权限。

### 灾备 Runner

标签：

```text
self-hosted
agentmind-staging-dr
```

必须安装 PowerShell 7、Docker Compose、Vault CLI 和 restic。该节点不能是主验收 Swarm 管理节点，不能挂载主集群数据卷，也不能使用生产 Swarm 栈文件。它只使用只读异地备份凭据恢复到可丢弃环境。

从 GitHub 仓库的“设置、操作、Runner、新建自托管 Runner”页面取得官方安装命令，分别下载与校验对应平台的 Runner 包。安装包解压后，使用仓库提供的注册脚本统一标签并避免令牌进入命令历史：

```powershell
$env:GITHUB_RUNNER_REGISTRATION_TOKEN = "粘贴 GitHub 页面生成的一次性令牌"
./deploy/staging/register-github-runner.ps1 `
  -Role primary `
  -RunnerDirectory "D:\受保护目录\agentmind-staging-runner" `
  -RunnerName "agentmind-staging-primary-01" `
  -InstallService `
  -ConfirmRunnerRegistration
```

灾备节点将 `Role` 改为 `disaster-recovery`，并使用独立目录和名称。脚本分别写入 `agentmind-staging` 与 `agentmind-staging-dr` 标签；已存在 `.runner` 身份时会拒绝覆盖。注册令牌只从当前进程环境读取，并在调用 GitHub 配置程序前从环境中清除。

注册完成后，应先在 GitHub 页面确认两个 Runner 都是空闲在线状态，再手工执行本地零变更就绪检查。不要在同一主机上同时放置主验收和灾备 Runner。

## 四、运行时配置

非秘密配置模板位于：

```text
deploy/staging/staging.env.example
```

将模板复制到仓库外的受保护配置目录，替换 OIDC、PostgreSQL、Redis、MinIO、OpenSearch、前端来源、可信代理网段和 OTLP 地址。模板只允许保存非秘密值。

以下内容必须由 Vault 或部署平台注入，禁止写入环境模板：

```text
spring.datasource.password
spring.data.redis.password
agentmind.storage.minio.access-key
agentmind.storage.minio.secret-key
agentmind.keyword-index.opensearch.password
agentmind_tls_certificate.pem
agentmind_tls_private_key.pem
```

主集群首次部署前创建加密覆盖网络：

```powershell
docker network create --driver overlay --opt encrypted agentmind-production
```

使用 Vault Agent 渲染秘密后创建初始 Docker Secret，再以同一个不可变镜像初始化稳定组和灰度组：

```powershell
vault agent -config="替换为主 Runner 上的 Vault Agent 配置绝对路径"

./deploy/create-swarm-secrets.ps1 `
  -TlsCertificatePath "替换为渲染后的证书绝对路径" `
  -TlsPrivateKeyPath "替换为渲染后的私钥绝对路径"

./deploy/deploy-release.ps1 `
  -Image "ghcr.io/serein930/personal-knowledge-agent/backend@sha256:替换为真实摘要"
```

首次部署必须确认三个服务达到目标副本数，HTTPS 就绪探针返回成功，且 PostgreSQL 和 Redis 已进入 readiness 健康组。MinIO 与 OpenSearch 还需通过一次真实文档摄取、对象读取和混合检索验证。

## 五、GitHub Environment 配置

创建名为 `staging` 的 GitHub Environment，启用必要审核人、禁止自审，并限制允许部署的分支或标签。

变量：

```text
STAGING_BASE_URL
STAGING_WORKSPACE_ID
STAGING_VAULT_AGENT_CONFIG
STAGING_VAULT_RENDERED_DIRECTORY
STAGING_DR_VAULT_AGENT_CONFIG
STAGING_DR_VAULT_RENDERED_DIRECTORY
STAGING_RESTIC_REPOSITORY
STAGING_RESTIC_PASSWORD_FILE
STAGING_BACKUP_REGION
STAGING_DR_ENVIRONMENT
STAGING_DR_COMPOSE_FILE
STAGING_RPO_TARGET_MINUTES
STAGING_RTO_TARGET_MINUTES
```

秘密：

```text
STAGING_ACCESS_TOKEN
STAGING_BACKUP_ACCESS_KEY_ID
STAGING_BACKUP_SECRET_ACCESS_KEY
STAGING_BACKUP_SESSION_TOKEN
```

所有 Runner 路径变量都必须填写绝对路径。S3 访问密钥应优先替换为云工作负载身份产生的短期凭据；若暂时无法使用，至少限制为目标备份桶的只读恢复权限并设置短期轮换。

仓库提供不含秘密值的声明模板：

```text
deploy/staging/github-environment.example.json
```

将模板复制到仓库外，替换审核人用户编号和全部占位变量。秘密值只放入当前 PowerShell 进程，变量名需与模板中的映射一致：

```powershell
$env:AGENTMIND_STAGING_ACCESS_TOKEN = "预发布验收用户的短期访问令牌"
$env:AGENTMIND_STAGING_BACKUP_ACCESS_KEY_ID = "异地备份只读访问编号"
$env:AGENTMIND_STAGING_BACKUP_SECRET_ACCESS_KEY = "异地备份只读秘密"

./scripts/configure-github-staging-environment.ps1 `
  -ConfigurationFile "D:\受保护目录\github-staging-environment.json" `
  -ValidateOnly

./scripts/configure-github-staging-environment.ps1 `
  -ConfigurationFile "D:\受保护目录\github-staging-environment.json" `
  -ConfirmEnvironmentUpdate
```

第二条命令需要已认证且有仓库管理权限的 GitHub CLI。脚本创建或更新 `staging` Environment、必要审核人、禁止自审、受保护分支策略、变量和秘密；秘密通过标准输入传给 GitHub CLI，不会作为命令参数或文件内容保存。完成后应立即清除当前进程中的秘密环境变量。

## 六、零变更就绪门禁

`staging-acceptance.yml` 会首先并行检查两个 Runner，任一失败时不会进入 Vault 轮换、灰度发布或故障注入。

主 Runner 检查：

- PowerShell 7、Docker、Vault CLI、Cosign 可执行。
- Vault 配置和渲染目录使用有效绝对路径。
- 当前节点是可用 Swarm 管理节点。
- `agentmind-production` 是启用加密的 overlay 网络。
- 稳定、灰度和网关服务达到最小运行副本数。
- 验收地址使用 HTTPS。

灾备 Runner 检查：

- PowerShell 7、Docker Compose、Vault CLI、restic 可执行。
- 当前节点不是主集群 Swarm 管理节点。
- 灾备 Compose 文件存在且不是生产 `docker-stack.yml`。
- restic 仓库是远端仓库而不是本地目录。
- 通过只读 `restic snapshots` 验证仓库、密码、临时凭据和网络可用。

就绪报告写入 `.staging-readiness-reports`，只记录工具路径、版本、拓扑和状态，不记录秘密值。该报告使用 `reportType`，不会被发布候选冻结脚本误认为六类生产证据。

## 七、执行真实验收

推荐通过 `release-candidate-acceptance.yml` 运行完整流程：

1. 在 GitHub Actions 页面选择“发布候选全链路验收”。
2. 填写并发用户数、稳态持续时间和 restic 快照，然后手动运行。
3. 供应链门禁执行常规测试、真实依赖测试、镜像构建、软件物料清单、漏洞扫描、GHCR 推送、Cosign 无密钥签名和来源证明。
4. 供应链门禁生成 `release-candidate.json`，并直接输出带 `sha256` 摘要的不可变镜像。
5. 预发布验收只消费该输出；供应链失败或输出为空时不会调度任何自托管 Runner。
6. 审核人批准 `staging` Environment。
7. 等待双 Runner 就绪、预检、秘密轮换、灰度、真实依赖冒烟、容量、故障注入和灾备全部通过。
8. 工作流冻结六类证据并晋级稳定组；任一变更后步骤失败时自动撤销灰度。

保留单独手动运行 `production-release-gate.yml` 和 `staging-acceptance.yml` 的能力，用于失败诊断和按同一摘要重试。单独运行 staging 时必须从候选清单读取完整镜像地址，不得从普通标签推断摘要。

不得使用普通标签、`latest`、本地构造 JSON 或手工修改报告绕过门禁。

灰度实例启动后、容量测试开始前，工作流会运行 `test-staging-dependency-smoke.ps1`。该脚本通过真实业务接口完成以下检查：

- 使用 OIDC 访问令牌读取当前用户。
- 上传带唯一标记的 Markdown 文档并等待异步摄取完成。
- 从文档列表验证 PostgreSQL 元数据。
- 从知识检索验证 pgvector 与 OpenSearch 双路索引结果。
- 执行一次带来源引用的检索增强生成问答。
- 查询并删除会话，验证 Redis 会话记忆。

报告保存在 `.staging-smoke-reports/staging-dependency-smoke.json` 并作为工作流产物留存。验收会话会自动删除；上传文档暂时没有删除接口，因此必须使用专用验收知识空间，并通过生命周期策略定期清理带 `staging-smoke` 标签的证据文档。

## 八、指标固化

容量工作流生成的 `performance-summary.json` 是 P95、P99、错误率和吞吐分析的原始依据；故障和灾备报告分别给出副本恢复时间、最大连续失败次数、RPO 与 RTO。

验收通过后，将实测值填写到 `PERFORMANCE_BASELINE_REPORT.md`，并保留一份带发布候选编号的不可变副本。最大吞吐量不能只使用一次固定并发测试推断，应至少进行逐级升压，直到错误率、延迟、资源或限流门禁首次失败，再把前一个稳定等级记录为安全容量。

以下条件全部满足后才允许冻结发布候选：

- 六类证据属于同一 Git 提交和同一不可变镜像。
- RPO、RTO、P95、P99 和错误率均达到门禁。
- 故障期间网关连续失败次数和副本恢复时间达标。
- 真实 OIDC、对象存储、混合检索和会话链路通过。
- 可观测平台在完整观察窗口内没有新的严重告警。

## 九、本地可执行测试

普通开发机只能验证安全边界和冻结逻辑：

```powershell
./scripts/tests/staging-bootstrap-tests.ps1
./scripts/tests/staging-runner-readiness-tests.ps1
./scripts/tests/production-acceptance-evidence-tests.ps1
./scripts/tests/release-candidate-workflow-contract-tests.ps1
```

第一条测试验证 Runner 注册、Environment 声明和真实依赖冒烟的防误操作边界；第二条测试证明开发环境无法绕过受保护 Runner 门禁；第三条测试证明失败、缺失或被篡改的验收证据不能冻结发布候选；第四条测试固定供应链摘要向 staging 的直接传递契约。它们不产生任何生产性能结论。
