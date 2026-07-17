param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [Parameter(Mandatory = $true)]
    [string]$Repository,
    [Parameter(Mandatory = $true)]
    [string]$PasswordFile,
    [string]$Tag = "agentmind-production",
    [string]$ResticImage = "restic/restic:0.19.1@sha256:136600b6ff6843d61d355f7f71f460a166429f35de6fd11b568fece3c9a4d510"
)

$ErrorActionPreference = "Stop"
$resolvedBackup = (Resolve-Path -Path $BackupDirectory).Path
$resolvedPassword = (Resolve-Path -Path $PasswordFile).Path
if ((Get-Item $resolvedPassword).Length -eq 0) {
    throw "restic 密码文件不能为空"
}
if (-not (Test-Path (Join-Path $resolvedBackup "manifest.json"))) {
    throw "备份目录缺少 manifest.json，拒绝同步不完整备份"
}

# Docker 只继承当前进程中的云凭据变量，命令行参数和日志都不会出现凭据原文。
$env:RESTIC_REPOSITORY = $Repository
$commonArguments = @(
    "run", "--rm",
    "-e", "RESTIC_REPOSITORY",
    "-e", "AWS_ACCESS_KEY_ID",
    "-e", "AWS_SECRET_ACCESS_KEY",
    "-e", "AWS_SESSION_TOKEN",
    "-e", "AWS_DEFAULT_REGION",
    "-e", "RESTIC_PASSWORD_FILE=/run/secrets/restic_password",
    "-v", "${resolvedPassword}:/run/secrets/restic_password:ro",
    "-v", "${resolvedBackup}:/backup:ro",
    $ResticImage
)

# 仓库不存在时只初始化一次；其他错误仍由后续命令明确暴露。
& docker @commonArguments snapshots --json *> $null
if ($LASTEXITCODE -ne 0) {
    & docker @commonArguments init
    if ($LASTEXITCODE -ne 0) {
        throw "初始化异地加密备份仓库失败"
    }
}

& docker @commonArguments backup /backup --tag $Tag --host "agentmind-production"
if ($LASTEXITCODE -ne 0) {
    throw "异地加密备份上传失败"
}
& docker @commonArguments check --read-data-subset=5%
if ($LASTEXITCODE -ne 0) {
    throw "异地备份仓库抽样校验失败"
}
& docker @commonArguments snapshots --tag $Tag --latest 3
Write-Host "异地备份完成。restic 已在客户端加密，仍需由对象存储启用版本控制和不可变保留策略。"
