param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [Parameter(Mandatory = $true)]
    [long]$WorkspaceId,
    [string]$AccessToken = $env:STAGING_ACCESS_TOKEN,
    [string]$EnvironmentName = $env:AGENTMIND_ENVIRONMENT,
    [ValidateRange(1, 120)]
    [int]$MaximumPollCount = 60,
    [ValidateRange(1, 60)]
    [int]$PollIntervalSeconds = 2,
    [string]$ReportRoot = ".staging-smoke-reports"
)

$ErrorActionPreference = "Stop"

function Add-SmokeCheck {
    param([string]$Name, [string]$Dependency, [string]$Evidence)

    $script:checks.Add([ordered]@{
        name = $Name
        dependency = $Dependency
        passed = $true
        evidence = $Evidence
    })
}

function Invoke-AgentMindJson {
    param(
        [ValidateSet("GET", "POST", "DELETE")]
        [string]$Method,
        [string]$Path,
        [object]$Body
    )

    $parameters = @{
        Method = $Method
        Uri = "$($BaseUrl.TrimEnd('/'))$Path"
        Headers = $script:authorizationHeader
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json; charset=utf-8"
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }
    Invoke-RestMethod @parameters
}

if ($EnvironmentName -ne "staging") {
    throw "真实依赖冒烟只允许在 staging 环境执行"
}
if ($BaseUrl -notmatch '^https://') {
    throw "预发布验收地址必须使用 HTTPS"
}
if ($WorkspaceId -le 0) {
    throw "WorkspaceId 必须是正整数"
}
if ([string]::IsNullOrWhiteSpace($AccessToken)) {
    throw "缺少 STAGING_ACCESS_TOKEN"
}

$checks = [System.Collections.Generic.List[object]]::new()
$authorizationHeader = @{ Authorization = "Bearer $AccessToken" }
$runId = [Guid]::NewGuid().ToString("N")
$marker = "agentmind-staging-smoke-$runId"
$documentId = $null
$taskId = $null
$conversationId = $null
$failureReason = $null
$startedAt = [DateTimeOffset]::UtcNow
$temporaryFile = Join-Path ([System.IO.Path]::GetTempPath()) "$marker.md"
$reportDirectory = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $ReportRoot))
$reportPath = Join-Path $reportDirectory "staging-dependency-smoke.json"

try {
    New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    @(
        "# AgentMind 预发布真实依赖冒烟"
        ""
        "唯一验收标记：$marker"
        ""
        "该文档用于验证对象存储、文档元数据、向量索引、关键词索引和问答引用链路。"
    ) | Set-Content -LiteralPath $temporaryFile -Encoding UTF8

    $currentUser = Invoke-AgentMindJson -Method GET -Path "/api/v1/users/me"
    if ($null -eq $currentUser.data.id) {
        throw "OIDC 令牌通过验证，但当前用户响应缺少用户编号"
    }
    Add-SmokeCheck -Name "读取当前用户" -Dependency "OIDC、PostgreSQL" -Evidence "已解析受保护用户身份"

    # Invoke-RestMethod 的 Form 参数由 PowerShell 7 提供，Runner 就绪门禁已经固定要求 pwsh。
    $uploadResponse = Invoke-RestMethod `
        -Method POST `
        -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/workspaces/$WorkspaceId/documents/files" `
        -Headers $authorizationHeader `
        -Form @{
            file = Get-Item -LiteralPath $temporaryFile
            title = $marker
            tags = "staging-smoke"
        } `
        -TimeoutSec 60
    $documentId = [long]$uploadResponse.data.documentId
    $taskId = [long]$uploadResponse.data.taskId
    if ($documentId -le 0 -or $taskId -le 0) {
        throw "文件上传响应缺少有效的文档编号或任务编号"
    }
    Add-SmokeCheck -Name "上传验收文档" -Dependency "MinIO、PostgreSQL" -Evidence "文档编号=$documentId，任务编号=$taskId"

    $task = $null
    for ($attempt = 1; $attempt -le $MaximumPollCount; $attempt++) {
        $taskResponse = Invoke-AgentMindJson -Method GET `
            -Path "/api/v1/workspaces/$WorkspaceId/ingestion-tasks/$taskId"
        $task = $taskResponse.data
        if ($task.status -eq "SUCCEEDED") { break }
        if ($task.status -in @("FAILED", "CANCELED")) {
            throw "摄取任务异常结束：状态=$($task.status)，原因=$($task.errorMessage)"
        }
        Start-Sleep -Seconds $PollIntervalSeconds
    }
    if ($task.status -ne "SUCCEEDED") {
        throw "摄取任务在限定时间内未完成：最后状态=$($task.status)"
    }
    Add-SmokeCheck -Name "完成异步摄取" -Dependency "PostgreSQL、pgvector、OpenSearch" -Evidence "任务状态=SUCCEEDED"

    $documentList = Invoke-AgentMindJson -Method GET `
        -Path "/api/v1/workspaces/$WorkspaceId/documents?page=1&pageSize=100&keyword=$marker"
    $storedDocument = @($documentList.data.records) | Where-Object { [long]$_.id -eq $documentId } | Select-Object -First 1
    if ($null -eq $storedDocument) {
        throw "文档元数据列表中未找到刚上传的验收文档"
    }
    Add-SmokeCheck -Name "查询文档元数据" -Dependency "PostgreSQL" -Evidence "已查询到文档编号=$documentId"

    $searchResponse = $null
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        $searchResponse = Invoke-AgentMindJson -Method POST `
            -Path "/api/v1/workspaces/$WorkspaceId/knowledge/search" `
            -Body @{ query = $marker; topK = 10 }
        $matchedResult = @($searchResponse.data.results) |
            Where-Object { [long]$_.documentId -eq $documentId -and $_.content -match [regex]::Escape($marker) } |
            Select-Object -First 1
        if ($null -ne $matchedResult) { break }
        Start-Sleep -Seconds $PollIntervalSeconds
    }
    if ($null -eq $matchedResult) {
        throw "混合检索未返回刚摄取的验收文档片段"
    }
    Add-SmokeCheck -Name "检索验收标记" -Dependency "pgvector、OpenSearch" -Evidence "命中文档编号=$documentId"

    $ragResponse = Invoke-AgentMindJson -Method POST `
        -Path "/api/v1/workspaces/$WorkspaceId/rag/chat" `
        -Body @{ question = "请根据知识库说明唯一验收标记 $marker"; topK = 10 }
    $conversationId = [long]$ragResponse.data.conversationId
    $matchedCitation = @($ragResponse.data.citations) |
        Where-Object { [long]$_.documentId -eq $documentId } |
        Select-Object -First 1
    if ($conversationId -le 0 -or $null -eq $matchedCitation) {
        throw "检索增强生成响应缺少会话编号或目标文档引用"
    }
    Add-SmokeCheck -Name "生成带引用回答" -Dependency "模型适配器、pgvector、OpenSearch" -Evidence "会话编号=$conversationId，引用文档编号=$documentId"

    $messagesResponse = Invoke-AgentMindJson -Method GET `
        -Path "/api/v1/workspaces/$WorkspaceId/chat/conversations/$conversationId/messages?page=1&pageSize=100"
    if (@($messagesResponse.data.records).Count -lt 2) {
        throw "会话记忆中没有完整的用户消息和助手消息"
    }
    Add-SmokeCheck -Name "查询短期会话" -Dependency "Redis" -Evidence "已读取完整问答消息"

    Invoke-AgentMindJson -Method DELETE `
        -Path "/api/v1/workspaces/$WorkspaceId/chat/conversations/$conversationId" | Out-Null
    Add-SmokeCheck -Name "清理短期会话" -Dependency "Redis" -Evidence "会话已通过业务接口删除"
    $conversationId = $null
} catch {
    $failureReason = $_.Exception.Message
} finally {
    if ($null -ne $conversationId -and $conversationId -gt 0) {
        try {
            Invoke-AgentMindJson -Method DELETE `
                -Path "/api/v1/workspaces/$WorkspaceId/chat/conversations/$conversationId" | Out-Null
        } catch {
            # 清理失败不能覆盖最初的验收失败原因，具体情况由服务端审计日志继续追踪。
        }
    }
    if (Test-Path -LiteralPath $temporaryFile) {
        Remove-Item -LiteralPath $temporaryFile -Force
    }

    $completedAt = [DateTimeOffset]::UtcNow
    $report = [ordered]@{
        reportType = "staging_dependency_smoke"
        schemaVersion = 1
        environment = $EnvironmentName
        baseUrl = $BaseUrl
        workspaceId = $WorkspaceId
        runId = $runId
        marker = $marker
        documentId = $documentId
        taskId = $taskId
        startedAt = $startedAt.ToString("o")
        completedAt = $completedAt.ToString("o")
        durationMilliseconds = [math]::Round(($completedAt - $startedAt).TotalMilliseconds)
        passed = [string]::IsNullOrWhiteSpace($failureReason)
        failureReason = $failureReason
        checks = $checks
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding UTF8
}

if (-not [string]::IsNullOrWhiteSpace($failureReason)) {
    throw "预发布真实依赖冒烟失败：$failureReason。报告：$reportPath"
}

Write-Host "预发布真实依赖冒烟通过。报告：$reportPath"
