param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [Parameter(Mandatory = $true)]
    [long]$WorkspaceId,
    [Parameter(Mandatory = $true)]
    [string]$Query,
    [Parameter(Mandatory = $true)]
    [long]$ExpectedDocumentId,
    [string]$AccessToken = $env:PRODUCTION_ACCESS_TOKEN,
    [string]$EnvironmentName = $env:AGENTMIND_ENVIRONMENT,
    [ValidateSet("canary", "stable")]
    [string]$Phase = "stable",
    [string]$ReportRoot = ".production-deployment-evidence"
)

$ErrorActionPreference = "Stop"
$startedAt = [DateTimeOffset]::UtcNow
$checks = [System.Collections.Generic.List[object]]::new()
$conversationId = $null
$failure = $null

if ($EnvironmentName -ne "production") { throw "发布后冒烟只允许在 production 环境执行" }
if ($BaseUrl -notmatch '^https://') { throw "生产地址必须使用 HTTPS" }
if ($WorkspaceId -le 0 -or $ExpectedDocumentId -le 0) { throw "知识空间和期望文档编号必须是正整数" }
if ([string]::IsNullOrWhiteSpace($Query)) { throw "生产冒烟检索词不能为空" }
if ([string]::IsNullOrWhiteSpace($AccessToken)) { throw "缺少 PRODUCTION_ACCESS_TOKEN" }

$headers = @{ Authorization = "Bearer $AccessToken"; Accept = "application/json" }
function Invoke-ProductionApi {
    param([string]$Method, [string]$Path, [object]$Body)
    $parameters = @{
        Method = $Method
        Uri = "$($BaseUrl.TrimEnd('/'))$Path"
        Headers = $headers
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json; charset=utf-8"
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }
    Invoke-RestMethod @parameters
}

try {
    $health = Invoke-RestMethod -Uri "$($BaseUrl.TrimEnd('/'))/actuator/health/readiness" -TimeoutSec 15
    if ($health.status -ne "UP") { throw "生产就绪探针未返回 UP" }
    $checks.Add(@{ name = "就绪探针"; passed = $true })

    $currentUser = Invoke-ProductionApi -Method GET -Path "/api/v1/users/me"
    if ([long]$currentUser.data.id -le 0) { throw "真实 OIDC 用户映射无效" }
    $checks.Add(@{ name = "OIDC 与用户持久化"; passed = $true })

    $search = Invoke-ProductionApi -Method POST `
        -Path "/api/v1/workspaces/$WorkspaceId/knowledge/search" `
        -Body @{ query = $Query; topK = 10 }
    $matched = @($search.data.results) | Where-Object { [long]$_.documentId -eq $ExpectedDocumentId } | Select-Object -First 1
    if ($null -eq $matched) { throw "生产混合检索没有命中预置冒烟文档" }
    $checks.Add(@{ name = "pgvector 与 OpenSearch 混合检索"; passed = $true })

    $chat = Invoke-ProductionApi -Method POST `
        -Path "/api/v1/workspaces/$WorkspaceId/rag/chat" `
        -Body @{ question = "请仅根据知识库回答：$Query"; topK = 10 }
    $conversationId = [long]$chat.data.conversationId
    $citation = @($chat.data.citations) | Where-Object { [long]$_.documentId -eq $ExpectedDocumentId } | Select-Object -First 1
    if ($conversationId -le 0 -or $null -eq $citation) { throw "生产 RAG 回答缺少会话或预期引用" }
    $checks.Add(@{ name = "模型、Redis 会话与引用"; passed = $true })

    Invoke-ProductionApi -Method DELETE `
        -Path "/api/v1/workspaces/$WorkspaceId/chat/conversations/$conversationId" | Out-Null
    $conversationId = $null
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($null -ne $conversationId -and $conversationId -gt 0) {
        try {
            Invoke-ProductionApi -Method DELETE `
                -Path "/api/v1/workspaces/$WorkspaceId/chat/conversations/$conversationId" | Out-Null
        } catch {
            # 清理失败不覆盖原始故障；服务端审计日志继续保留失败会话线索。
        }
    }
    $report = [ordered]@{
        schemaVersion = "1.0"
        evidenceType = "production_post_release_smoke"
        environment = $EnvironmentName
        phase = $Phase
        baseUrl = $BaseUrl
        workspaceId = $WorkspaceId
        expectedDocumentId = $ExpectedDocumentId
        startedAt = $startedAt.ToString("o")
        completedAt = ([DateTimeOffset]::UtcNow).ToString("o")
        checks = $checks
        passed = $null -eq $failure
        failure = $failure
    }
    New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
    # 灰度验证和全量切换验证必须分别留证，不能让后一次执行覆盖前一次结果。
    $reportPath = Join-Path $ReportRoot "production-$Phase-post-release-smoke.json"
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding UTF8
}

if ($null -ne $failure) { throw "生产发布后冒烟失败：$failure。报告=$reportPath" }
Write-Host "生产发布后冒烟通过：$reportPath"
