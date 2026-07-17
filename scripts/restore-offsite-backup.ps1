param(
    [Parameter(Mandatory = $true)]
    [string]$DestinationDirectory,
    [Parameter(Mandatory = $true)]
    [string]$Repository,
    [Parameter(Mandatory = $true)]
    [string]$PasswordFile,
    [string]$Snapshot = "latest",
    [string]$Tag = "agentmind-production",
    [string]$ResticImage = "restic/restic:0.19.1@sha256:136600b6ff6843d61d355f7f71f460a166429f35de6fd11b568fece3c9a4d510"
)

$ErrorActionPreference = "Stop"
$resolvedPassword = (Resolve-Path -Path $PasswordFile).Path
$destination = [System.IO.Path]::GetFullPath($DestinationDirectory)
if (Test-Path $destination) {
    if ((Get-ChildItem -Path $destination -Force | Measure-Object).Count -gt 0) {
        throw "异地恢复目标目录必须为空：$destination"
    }
} else {
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
}

$env:RESTIC_REPOSITORY = $Repository
$arguments = @(
    "run", "--rm",
    "-e", "RESTIC_REPOSITORY",
    "-e", "AWS_ACCESS_KEY_ID",
    "-e", "AWS_SECRET_ACCESS_KEY",
    "-e", "AWS_SESSION_TOKEN",
    "-e", "AWS_DEFAULT_REGION",
    "-e", "RESTIC_PASSWORD_FILE=/run/secrets/restic_password",
    "-v", "${resolvedPassword}:/run/secrets/restic_password:ro",
    "-v", "${destination}:/restore",
    $ResticImage,
    "restore", $Snapshot, "--tag", $Tag, "--target", "/restore"
)
& docker @arguments
if ($LASTEXITCODE -ne 0) {
    throw "从异地加密仓库恢复备份失败"
}

$manifestPath = Get-ChildItem -Path $destination -Filter manifest.json -Recurse | Select-Object -First 1
if ($null -eq $manifestPath) {
    throw "异地恢复结果缺少 manifest.json"
}
$manifest = Get-Content -Path $manifestPath.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
$backupRoot = $manifestPath.Directory.FullName
foreach ($file in $manifest.files) {
    $path = Join-Path $backupRoot $file.path
    if (-not (Test-Path $path)) {
        throw "异地恢复文件缺失：$($file.path)"
    }
    $actual = (Get-FileHash -Path $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $file.sha256) {
        throw "异地恢复文件哈希不一致：$($file.path)"
    }
}
Write-Host "异地备份已恢复并通过 SHA-256 清单校验：$backupRoot"
