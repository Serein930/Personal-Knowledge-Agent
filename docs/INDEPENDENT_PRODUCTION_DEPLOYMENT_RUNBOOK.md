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

## 三、受保护 Environment

创建 `production-deployment` GitHub Environment，设置必要审核人、禁止自审、限制默认分支，并配置：

变量：

```text
PRODUCTION_BASE_URL=https://实际生产域名
PRODUCTION_WORKSPACE_ID=预置冒烟知识空间编号
PRODUCTION_STACK_NAME=agentmind-production
```

秘密：

```text
PRODUCTION_ACCESS_TOKEN=短期生产冒烟访问令牌
PRODUCTION_DEPLOYMENT_GUARD=随机部署守卫
```

冒烟知识空间中需要预置一份可重复查询的文档，并记录其文档编号。令牌只能具备该知识空间的最小验证权限，执行结束后应立即吊销或等待短时过期。

## 四、执行顺序

1. 运行“发布候选全链路验收”，取得真实 RPO、RTO、P95、P99、吞吐和故障恢复证据。
2. 等待真实 staging E2E 和最终发布审批通过。
3. 运行“正式版本发布”，创建 GitHub Release，并记录该工作流运行编号。
4. 在 GitHub Actions 手工运行“独立生产集群部署”。
5. 填写正式版本发布运行编号、固定检索问题、预置文档编号和灰度观察秒数。
6. `production-deployment` 审核人确认目标版本和变更窗口后批准执行。

工作流会下载指定正式发布产物，核对发布清单、发布记录、提交、版本、摘要镜像以及清单 SHA-256，然后执行约 10% 灰度、灰度冒烟、稳定组更新、全量冒烟。

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
./scripts/tests/production-deployment-tests.ps1
```

该测试只校验正式发布证据绑定和工作流保护契约，不会调用 Docker、连接生产服务或触发 GitHub Actions。

## 八、停止条件

只有真实生产部署工作流成功、两阶段冒烟均通过、生产观测无异常且部署证据完成归档后，才能把对应版本标记为已部署。若尚未配置独立生产集群，本阶段应停留在“代码与运行手册就绪”。
