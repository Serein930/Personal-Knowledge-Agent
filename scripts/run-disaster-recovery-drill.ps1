param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [Parameter(Mandatory = $true)]
    [string]$EnvironmentName,
    [Parameter(Mandatory = $true)]
    [string]$ConfirmationText,
    [int]$RpoTargetMinutes = 1440,
    [int]$RtoTargetMinutes = 60,
    [string]$ComposeFile = "docker-compose.yml",
    [string]$ReportRoot = ".disaster-recovery-reports",
    [string]$AcceptanceEnvironment = "staging",
    [string]$GitCommit = $env:GITHUB_SHA,
    [string]$CandidateImage = $env:AGENTMIND_CANDIDATE_IMAGE
)

$ErrorActionPreference = "Stop"
if ($ConfirmationText -ne "DISPOSABLE:$EnvironmentName") {
    throw "恢复演练会覆盖目标环境。ConfirmationText 必须精确填写 DISPOSABLE:$EnvironmentName"
}
if ($RpoTargetMinutes -le 0 -or $RtoTargetMinutes -le 0) {
    throw "RPO 和 RTO 目标必须大于零"
}

$resolvedBackup = (Resolve-Path -Path $BackupDirectory).Path
$manifest = Get-Content -Path (Join-Path $resolvedBackup "manifest.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$backupCreatedAt = [DateTimeOffset]::Parse($manifest.createdAt)
$backupAgeMinutes = [Math]::Round(([DateTimeOffset]::Now - $backupCreatedAt).TotalMinutes, 2)
$startedAt = [DateTimeOffset]::Now
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$checks = [ordered]@{}
$failure = $null

function Assert-Command([string]$Message) {
    if ($LASTEXITCODE -ne 0) {
        throw $Message
    }
}

try {
    # 调用正式恢复脚本，所有覆盖保护和备份哈希校验仍然生效。
    & "$PSScriptRoot/restore-production.ps1" `
        -BackupDirectory $resolvedBackup `
        -ComposeFile $ComposeFile `
        -ConfirmMaintenanceWindow

    docker compose -f $ComposeFile exec -T agentmind-postgres `
        psql -U agentmind -d agentmind -v ON_ERROR_STOP=1 -c "SELECT 1" | Out-Null
    Assert-Command "PostgreSQL 恢复后检查失败"
    $checks.postgresql = "通过"

    $openSearchHealth = Invoke-RestMethod -Uri "http://localhost:9200/_cluster/health"
    if ($openSearchHealth.status -eq "red") {
        throw "OpenSearch 恢复后集群状态为 red"
    }
    $checks.openSearch = "通过：$($openSearchHealth.status)"

    $minioAccessKey = $env:AGENTMIND_MINIO_ACCESS_KEY
    $minioSecretKey = $env:AGENTMIND_MINIO_SECRET_KEY
    if ([string]::IsNullOrWhiteSpace($minioAccessKey) -or [string]::IsNullOrWhiteSpace($minioSecretKey)) {
        throw "MinIO 恢复检查缺少环境凭据"
    }
    $env:MC_HOST_agentmind = "http://${minioAccessKey}:${minioSecretKey}@localhost:9000"
    docker run --rm --network "container:agentmind-minio" -e MC_HOST_agentmind `
        minio/mc:RELEASE.2025-04-16T18-13-26Z ls "agentmind/$($manifest.minioBucket)" | Out-Null
    Assert-Command "MinIO 恢复后检查失败"
    $checks.minio = "通过"
} catch {
    $failure = $_.Exception.Message
} finally {
    $stopwatch.Stop()
}

$durationMinutes = [Math]::Round($stopwatch.Elapsed.TotalMinutes, 2)
$report = [ordered]@{
    schemaVersion = "1.0"
    evidenceType = "disaster_recovery"
    evidenceId = "drill-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    environment = $AcceptanceEnvironment
    drillEnvironment = $EnvironmentName
    gitCommit = $GitCommit
    candidateImage = $CandidateImage
    startedAt = $startedAt.ToString("o")
    completedAt = ([DateTimeOffset]::Now).ToString("o")
    backupCreatedAt = $backupCreatedAt.ToString("o")
    backupAgeMinutes = $backupAgeMinutes
    rpoTargetMinutes = $RpoTargetMinutes
    rpoMet = $backupAgeMinutes -le $RpoTargetMinutes
    restoreDurationMinutes = $durationMinutes
    rtoTargetMinutes = $RtoTargetMinutes
    rtoMet = $durationMinutes -le $RtoTargetMinutes
    checks = $checks
    passed = $null -eq $failure -and $backupAgeMinutes -le $RpoTargetMinutes -and $durationMinutes -le $RtoTargetMinutes
    failure = $failure
}
New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$reportPath = Join-Path $ReportRoot "$($report.evidenceId).json"
$report | ConvertTo-Json -Depth 6 | Set-Content -Path $reportPath -Encoding UTF8
if (-not $report.passed) {
    throw "灾备演练未通过，详情见 $reportPath"
}
Write-Host "灾备演练通过：RPO=$backupAgeMinutes 分钟，RTO=$durationMinutes 分钟，报告=$reportPath"
