param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [string]$ComposeFile = "docker-compose.yml",
    [string]$DatabaseName = "agentmind",
    [string]$DatabaseUser = "agentmind",
    [string]$MinioBucket = "agentmind",
    [string]$OpenSearchSnapshotVolume = "agentmind_agentmind-opensearch-snapshots",
    [switch]$ConfirmMaintenanceWindow
)

$ErrorActionPreference = "Stop"
if (-not $ConfirmMaintenanceWindow) {
    throw "恢复会覆盖现有数据，请在停止业务写入后增加 -ConfirmMaintenanceWindow 参数"
}
$resolvedBackup = (Resolve-Path -Path $BackupDirectory).Path
$manifestPath = Join-Path $resolvedBackup "manifest.json"
if (-not (Test-Path $manifestPath)) {
    throw "备份清单不存在：$manifestPath"
}
$manifest = Get-Content -Raw -Path $manifestPath -Encoding UTF8 | ConvertFrom-Json
foreach ($file in $manifest.files) {
    $fullPath = Join-Path $resolvedBackup $file.path
    if (-not (Test-Path $fullPath)) {
        throw "备份文件缺失：$($file.path)"
    }
    $actualHash = (Get-FileHash -Path $fullPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $file.sha256) {
        throw "备份文件校验失败：$($file.path)"
    }
}

function Assert-LastCommand([string]$Message) {
    if ($LASTEXITCODE -ne 0) {
        throw $Message
    }
}

# 数据库恢复使用 clean 与 if-exists，必须在维护窗口且业务实例停止写入后执行。
$databaseFile = Join-Path $resolvedBackup "postgresql.dump"
docker cp $databaseFile "agentmind-postgres:/tmp/postgresql.dump"
Assert-LastCommand "PostgreSQL 恢复文件复制失败"
docker compose -f $ComposeFile exec -T agentmind-postgres `
    pg_restore -U $DatabaseUser -d $DatabaseName --clean --if-exists --no-owner /tmp/postgresql.dump
Assert-LastCommand "PostgreSQL 恢复失败"
docker compose -f $ComposeFile exec -T agentmind-postgres rm -f /tmp/postgresql.dump

$minioAccessKey = $env:AGENTMIND_MINIO_ACCESS_KEY
$minioSecretKey = $env:AGENTMIND_MINIO_SECRET_KEY
if ([string]::IsNullOrWhiteSpace($minioAccessKey) -or [string]::IsNullOrWhiteSpace($minioSecretKey)) {
    throw "恢复 MinIO 前必须配置 AGENTMIND_MINIO_ACCESS_KEY 和 AGENTMIND_MINIO_SECRET_KEY"
}
$minioBackupPath = (Join-Path $resolvedBackup "minio").Replace("\", "/")
$minioHost = "http://${minioAccessKey}:${minioSecretKey}@localhost:9000"
docker run --rm --network "container:agentmind-minio" `
    -e "MC_HOST_agentmind=$minioHost" `
    -v "${minioBackupPath}:/backup:ro" minio/mc:RELEASE.2025-04-16T18-13-26Z `
    mirror --overwrite /backup "agentmind/$MinioBucket"
Assert-LastCommand "MinIO 对象恢复失败"

# OpenSearch 恢复的是快照仓库，再通过官方 restore API 回放索引。
docker compose -f $ComposeFile stop agentmind-opensearch
$archivePath = $resolvedBackup.Replace("\", "/")
docker run --rm `
    -v "${OpenSearchSnapshotVolume}:/snapshots" `
    -v "${archivePath}:/backup:ro" alpine:3.21 `
    sh -c "rm -rf /snapshots/* && tar -xzf /backup/opensearch-snapshot-repository.tar.gz -C /snapshots"
Assert-LastCommand "OpenSearch 快照仓库解包失败"
docker compose -f $ComposeFile start agentmind-opensearch
Start-Sleep -Seconds 20
$repositoryBody = @{type = "fs"; settings = @{location = "/usr/share/opensearch/snapshots"}} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Put -Uri "http://localhost:9200/_snapshot/agentmind_backup" `
    -ContentType "application/json" -Body $repositoryBody | Out-Null
Invoke-RestMethod -Method Post -Uri "http://localhost:9200/_all/_close" | Out-Null
Invoke-RestMethod -Method Post `
    -Uri "http://localhost:9200/_snapshot/agentmind_backup/$($manifest.openSearchSnapshot)/_restore?wait_for_completion=true" `
    -ContentType "application/json" -Body '{"include_global_state":false}' | Out-Null
Write-Host "恢复完成，请执行数据量核对、对象抽检、索引搜索和 RAG 回归后再恢复流量。"
