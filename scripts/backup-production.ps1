param(
    [string]$BackupRoot = ".agentmind-backups",
    [string]$ComposeFile = "docker-compose.yml",
    [string]$DatabaseName = "agentmind",
    [string]$DatabaseUser = "agentmind",
    [string]$MinioBucket = "agentmind",
    [string]$EnvironmentName = "production",
    [string]$OpenSearchSnapshotVolume = "agentmind_agentmind-opensearch-snapshots"
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupDirectory = Join-Path (Resolve-Path -Path ".").Path "$BackupRoot/$timestamp"
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $backupDirectory "minio") -Force | Out-Null

function Assert-LastCommand([string]$Message) {
    if ($LASTEXITCODE -ne 0) {
        throw $Message
    }
}

# PostgreSQL 使用自包含格式，便于恢复前查看对象清单并选择性恢复。
$databaseFileName = "postgresql.dump"
docker compose -f $ComposeFile exec -T agentmind-postgres `
    pg_dump -U $DatabaseUser -d $DatabaseName -Fc -f "/tmp/$databaseFileName"
Assert-LastCommand "PostgreSQL 备份失败"
docker cp "agentmind-postgres:/tmp/$databaseFileName" (Join-Path $backupDirectory $databaseFileName)
Assert-LastCommand "PostgreSQL 备份文件复制失败"
docker compose -f $ComposeFile exec -T agentmind-postgres rm -f "/tmp/$databaseFileName"

# MinIO 凭据只从当前进程环境读取，并传给一次性客户端容器。
$minioAccessKey = $env:AGENTMIND_MINIO_ACCESS_KEY
$minioSecretKey = $env:AGENTMIND_MINIO_SECRET_KEY
if ([string]::IsNullOrWhiteSpace($minioAccessKey) -or [string]::IsNullOrWhiteSpace($minioSecretKey)) {
    throw "备份 MinIO 前必须配置 AGENTMIND_MINIO_ACCESS_KEY 和 AGENTMIND_MINIO_SECRET_KEY"
}
$minioBackupPath = (Join-Path $backupDirectory "minio").Replace("\", "/")
$minioHost = "http://${minioAccessKey}:${minioSecretKey}@localhost:9000"
docker run --rm --network "container:agentmind-minio" `
    -e "MC_HOST_agentmind=$minioHost" `
    -v "${minioBackupPath}:/backup" minio/mc:RELEASE.2025-04-16T18-13-26Z `
    mirror --overwrite "agentmind/$MinioBucket" /backup
Assert-LastCommand "MinIO 对象备份失败"

# OpenSearch 先生成一致性快照，再归档快照仓库卷；不能直接复制正在写入的数据目录。
$repositoryBody = @{type = "fs"; settings = @{location = "/usr/share/opensearch/snapshots"}} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Put -Uri "http://localhost:9200/_snapshot/agentmind_backup" `
    -ContentType "application/json" -Body $repositoryBody | Out-Null
$snapshotName = "snapshot-$timestamp"
Invoke-RestMethod -Method Put `
    -Uri "http://localhost:9200/_snapshot/agentmind_backup/$snapshotName?wait_for_completion=true" | Out-Null
$archivePath = $backupDirectory.Replace("\", "/")
docker run --rm `
    -v "${OpenSearchSnapshotVolume}:/snapshots:ro" `
    -v "${archivePath}:/backup" alpine:3.21 `
    tar -czf /backup/opensearch-snapshot-repository.tar.gz -C /snapshots .
Assert-LastCommand "OpenSearch 快照仓库归档失败"

$files = Get-ChildItem -Path $backupDirectory -Recurse -File | Where-Object { $_.Name -ne "manifest.json" }
$manifest = [ordered]@{
    formatVersion = 2
    environment = $EnvironmentName
    createdAt = (Get-Date).ToString("o")
    database = $DatabaseName
    minioBucket = $MinioBucket
    openSearchSnapshot = $snapshotName
    files = @($files | ForEach-Object {
        [ordered]@{
            path = $_.FullName.Substring($backupDirectory.Length + 1).Replace("\", "/")
            size = $_.Length
            sha256 = (Get-FileHash -Path $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    })
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -Path (Join-Path $backupDirectory "manifest.json") -Encoding UTF8
Write-Host "备份完成：$backupDirectory"
