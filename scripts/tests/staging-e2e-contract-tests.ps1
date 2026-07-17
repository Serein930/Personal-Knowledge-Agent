$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$workflow = Get-Content -LiteralPath (Join-Path $repositoryRoot ".github/workflows/staging-full-stack-e2e.yml") -Raw -Encoding UTF8
$playwrightConfig = Get-Content -LiteralPath (Join-Path $repositoryRoot "ui/playwright.staging.config.ts") -Raw -Encoding UTF8
$viteConfig = Get-Content -LiteralPath (Join-Path $repositoryRoot "ui/vite.config.ts") -Raw -Encoding UTF8
$testCase = Get-Content -LiteralPath (Join-Path $repositoryRoot "ui/staging-e2e/real-dependencies.spec.ts") -Raw -Encoding UTF8

function Assert-Contains {
    param([string]$Content, [string]$Expected, [string]$Message)
    if (-not $Content.Contains($Expected)) { throw $Message }
}

# 后置工作流必须由统一验收成功触发，并绑定默认分支上的相同提交。
Assert-Contains $workflow "workflow_run:" "真实 staging E2E 没有使用门禁完成事件触发"
Assert-Contains $workflow "发布候选全链路验收" "真实 staging E2E 没有绑定统一验收工作流"
Assert-Contains $workflow "github.event.workflow_run.conclusion == 'success'" "上游失败时仍可能执行真实 E2E"
Assert-Contains $workflow "github.event.workflow_run.head_branch == 'main'" "真实 E2E 没有限制默认分支"
Assert-Contains $workflow 'ref: ${{ github.event.workflow_run.head_sha }}' "真实 E2E 没有检出通过门禁的同一提交"
Assert-Contains $workflow "environment: staging" "真实 E2E 没有使用受保护 staging Environment"

# 令牌只能进入服务器代理，禁止通过 VITE_ 前缀暴露给浏览器构建环境。
Assert-Contains $playwrightConfig "AGENTMIND_STAGING_E2E_ACCESS_TOKEN" "Playwright 没有把短期令牌交给安全代理"
Assert-Contains $viteConfig 'Authorization: `Bearer ${stagingAccessToken}`' "Vite 代理没有注入 OIDC Bearer 令牌"
if ($viteConfig.Contains("VITE_STAGING_ACCESS_TOKEN") -or $playwrightConfig.Contains("VITE_STAGING_ACCESS_TOKEN")) {
    throw "真实 staging 令牌不能使用 VITE_ 前缀"
}

# 浏览器用例必须覆盖生产依赖真正参与的核心用户链路。
Assert-Contains $testCase "/api/v1/users/me" "真实 E2E 缺少 OIDC 用户验证"
Assert-Contains $testCase "提交文件" "真实 E2E 缺少文件摄取"
Assert-Contains $testCase "pgvector/OpenSearch" "真实 E2E 缺少生产索引等待边界"
Assert-Contains $testCase "引用来源" "真实 E2E 缺少检索增强生成引用验证"

Write-Host "真实 staging E2E 安全与触发契约测试全部通过"
