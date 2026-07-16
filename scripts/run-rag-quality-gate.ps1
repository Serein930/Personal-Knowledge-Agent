param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [Parameter(Mandatory = $true)]
    [long]$WorkspaceId,
    [Parameter(Mandatory = $true)]
    [long]$DatasetId,
    [Parameter(Mandatory = $true)]
    [int]$DatasetVersion,
    [string]$AccessToken = $env:AGENTMIND_ACCESS_TOKEN,
    [int]$TopK = 5,
    [int]$CandidatePoolSize = 20,
    [ValidateSet("VECTOR", "HYBRID")]
    [string]$RetrievalStrategy = "VECTOR",
    [ValidateSet("NONE", "LEXICAL")]
    [string]$RerankStrategy = "NONE",
    [double]$MinimumRecallAtK = 80,
    [double]$MinimumNdcgAtK = 70,
    [double]$MinimumFaithfulness = 0,
    [double]$MinimumAnswerRelevance = 0,
    [long]$MaximumAverageLatencyMillis = 10000,
    [int]$MaximumTotalTokens = 100000,
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"
$root = $BaseUrl.TrimEnd('/')
$headers = @{}
if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
    $headers["Authorization"] = "Bearer $AccessToken"
}
$jobUri = "$root/v1/workspaces/$WorkspaceId/evaluations/jobs"
$request = @{
    datasetId = $DatasetId
    datasetVersion = $DatasetVersion
    experimentName = "CI质量门禁-$RetrievalStrategy-$RerankStrategy"
    retrievalStrategy = $RetrievalStrategy
    candidatePoolSize = $CandidatePoolSize
    rerankStrategy = $RerankStrategy
    topK = $TopK
    qualityGate = @{
        minimumRecallAtK = $MinimumRecallAtK
        minimumNdcgAtK = $MinimumNdcgAtK
        minimumFaithfulness = $MinimumFaithfulness
        minimumAnswerRelevance = $MinimumAnswerRelevance
        maximumAverageLatencyMillis = $MaximumAverageLatencyMillis
        maximumTotalTokens = $MaximumTotalTokens
    }
} | ConvertTo-Json -Depth 6

$submitted = Invoke-RestMethod -Method Post -Uri $jobUri -Headers $headers `
    -ContentType "application/json; charset=utf-8" -Body $request
$jobId = $submitted.data.id
Write-Host "已提交评估任务 #$jobId"

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds 1
    $current = Invoke-RestMethod -Method Get -Uri "$jobUri/$jobId" -Headers $headers
    Write-Host "任务 #$jobId 状态=$($current.data.status)，进度=$($current.data.progress)%"
    if ($current.data.terminal) {
        break
    }
} while ((Get-Date) -lt $deadline)

if (-not $current.data.terminal) {
    Write-Error "评估任务 #$jobId 在 $TimeoutSeconds 秒内未完成"
    exit 1
}
if ($current.data.status -ne "SUCCEEDED") {
    Write-Error "评估任务 #$jobId 未成功完成：$($current.data.failureReason)"
    exit 1
}
if ($current.data.qualityGateResult.status -ne "PASSED") {
    $violations = $current.data.qualityGateResult.violations -join "；"
    Write-Error "RAG质量门禁未通过：$violations"
    exit 1
}

Write-Host "RAG质量门禁通过：Recall@K=$($current.data.metrics.recallAtK)，NDCG@K=$($current.data.metrics.ndcgAtK)，平均耗时=$($current.data.metrics.averageLatencyMillis)ms"
exit 0
