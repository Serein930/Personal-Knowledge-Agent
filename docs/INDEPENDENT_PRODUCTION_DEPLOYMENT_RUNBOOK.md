# 独立生产集群部署手册

## 一、适用边界

本手册用于把已经创建 GitHub Release 的不可变摘要镜像部署到独立生产集群。GitHub Release 只代表版本已经固化，不代表生产流量已经切换。

仓库脚本不能代替真实主机、域名、证书、OIDC、数据库、对象存储、搜索集群和人工审批。没有真实运行证据时，不得宣称生产部署、自动回滚或发布后冒烟已经通过。

## 二、生产 Runner

在生产 Swarm 管理节点注册专用自托管 Runner，并同时配置以下标签：

```text
self-hosted
agentmind-production
```

该 Runner 至少需要 PowerShell 7、Git、Docker CLI，并必须能够管理目标 Swarm、拉取 GitHub 容器仓库摘要镜像、访问生产 HTTPS 地址。Runner 不应与主验收或灾备 Runner 混用。

先从 GitHub 仓库的 `Settings -> Actions -> Runners` 获取一次性注册令牌，并在已经下载、校验官方 Runner 安装包的生产主机上执行：

```powershell
$env:GITHUB_RUNNER_REGISTRATION_TOKEN = "一次性令牌"
./deploy/production/register-github-runner.ps1 `
  -RunnerDirectory "D:\actions-runner" `
  -RunnerName "agentmind-production-01" `
  -InstallService `
  -ConfirmRunnerRegistration
```

Linux 主机使用对应绝对路径，并可通过 `ServiceUser` 指定最小权限服务账号。脚本不会下载 Runner，也不会覆盖已注册身份；注册完成后会清除当前进程中的一次性令牌。

## 三、受保护 Environment

创建 `production-deployment` GitHub Environment，设置必要审核人、禁止自审、限制默认分支，并配置：

变量：

```text
PRODUCTION_BASE_URL=https://实际生产域名
PRODUCTION_WORKSPACE_ID=预置冒烟知识空间编号
PRODUCTION_STACK_NAME=agentmind-production
PRODUCTION_OVERLAY_NETWORK=agentmind-production
```

秘密：

```text
PRODUCTION_ACCESS_TOKEN=短期生产冒烟访问令牌
PRODUCTION_DEPLOYMENT_GUARD=随机部署守卫
```

冒烟知识空间中需要预置一份可重复查询的文档，并记录其文档编号。令牌只能具备该知识空间的最小验证权限，执行结束后应立即吊销或等待短时过期。

复制仓库中的无秘密模板到仓库外，替换审核人、生产地址和知识空间编号，然后通过进程环境注入秘密：

```powershell
Copy-Item ./deploy/production/github-environment.example.json D:\secure\production-environment.json
$env:AGENTMIND_PRODUCTION_ACCESS_TOKEN = "短期最小权限令牌"
$env:AGENTMIND_PRODUCTION_DEPLOYMENT_GUARD = "随机部署守卫"

./scripts/configure-github-production-environment.ps1 `
  -ConfigurationFile D:\secure\production-environment.json `
  -ValidateOnly

./scripts/configure-github-production-environment.ps1 `
  -ConfigurationFile D:\secure\production-environment.json `
  -ConfirmEnvironmentUpdate
```

真实写入需要提前安装并认证 GitHub CLI。配置脚本强制至少一个审核人、禁止自审、仅允许受保护分支，并拒绝示例域名、空秘密和非 HTTPS 地址。

## 四、执行顺序

1. 运行“发布候选全链路验收”，取得真实 RPO、RTO、P95、P99、吞吐和故障恢复证据。
2. 等待真实 staging E2E 和最终发布审批通过。
3. 运行“正式版本发布”，创建 GitHub Release，并记录该工作流运行编号。
4. 在 GitHub Actions 手工运行“独立生产集群部署”。
5. 填写正式版本发布运行编号、固定检索问题、预置文档编号和灰度观察秒数。
6. `production-deployment` 审核人确认目标版本和变更窗口后批准执行。

工作流会下载指定正式发布产物，核对发布清单、发布记录、提交、版本、摘要镜像以及清单 SHA-256，然后执行约 10% 灰度、灰度冒烟、稳定组更新、全量冒烟。

部署前的零变更门禁还会检查生产 Runner 的 PowerShell、Git、Docker、Swarm 管理权限、加密覆盖网络、稳定组与灰度组副本，以及 `start-first` 和失败回滚策略。该检查只读取环境，不更新服务。

## 五、自动回滚

部署开始前会读取稳定组和灰度组的原镜像。灰度更新、观察、稳定组切换或任一冒烟失败时，脚本都会尝试把两个服务恢复到发布前摘要镜像，并在部署报告中记录 `rolledBack`。

自动回滚报告不等于故障已经完全解除。维护者仍需检查 Swarm 服务副本、网关流量、数据库兼容性、队列积压和告警，再决定是否关闭事故。

## 六、证据产物

每次运行保留 365 天的 `production-deployment-*` 产物，至少包含：

```text
production-deployment.json
production-canary-post-release-smoke.json
production-stable-post-release-smoke.json
```

发布后冒烟覆盖就绪探针、真实 OIDC 用户、PostgreSQL 用户映射、pgvector 与 OpenSearch 检索、模型回答、Redis 会话和引用来源。报告不保存访问令牌。

## 七、本地边界测试

```powershell
./scripts/tests/production-bootstrap-tests.ps1
./scripts/tests/production-deployment-tests.ps1
```

这些测试只校验 Runner 注册保护、Environment 配置、正式发布证据绑定和工作流契约，不会注册真实 Runner、调用 Docker、连接生产服务或触发 GitHub Actions。

## 八、停止条件

只有真实生产部署工作流成功、两阶段冒烟均通过、生产观测无异常且部署证据完成归档后，才能把对应版本标记为已部署。若尚未配置独立生产集群，本阶段应停留在“代码与运行手册就绪”。

当前开发机没有 GitHub CLI、生产主机和受保护环境秘密，因此只能完成仓库内校验，不能在本地代替真实审核人运行发布候选验收、staging E2E、最终审批、GitHub Release 或生产部署。
