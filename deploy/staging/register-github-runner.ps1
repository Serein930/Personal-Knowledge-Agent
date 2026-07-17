param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("primary", "disaster-recovery")]
    [string]$Role,
    [Parameter(Mandatory = $true)]
    [string]$RunnerDirectory,
    [Parameter(Mandatory = $true)]
    [string]$RunnerName,
    [string]$RepositoryUrl = "https://github.com/Serein930/Personal-Knowledge-Agent",
    [string]$WorkDirectory = "_work",
    [string]$ServiceUser = "",
    [switch]$InstallService,
    [switch]$ConfirmRunnerRegistration
)

$ErrorActionPreference = "Stop"

if (-not $ConfirmRunnerRegistration) {
    throw "Runner 注册会修改当前机器，必须显式传入 ConfirmRunnerRegistration"
}
if ($RepositoryUrl -notmatch '^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?$') {
    throw "RepositoryUrl 必须是 github.com 上的 HTTPS 仓库地址"
}
if ($RunnerName -notmatch '^[A-Za-z0-9_.-]{3,64}$') {
    throw "RunnerName 只能包含字母、数字、点、下划线和连字符，长度为 3 至 64"
}
if (-not [System.IO.Path]::IsPathRooted($RunnerDirectory)) {
    throw "RunnerDirectory 必须使用绝对路径"
}
if (-not (Test-Path -LiteralPath $RunnerDirectory -PathType Container)) {
    throw "Runner 安装目录不存在：$RunnerDirectory"
}

$resolvedRunnerDirectory = (Resolve-Path -LiteralPath $RunnerDirectory).Path
$configuredMarker = Join-Path $resolvedRunnerDirectory ".runner"
if (Test-Path -LiteralPath $configuredMarker) {
    throw "当前目录已经注册 Runner。请先按 GitHub 官方流程移除旧注册，脚本不会自动覆盖现有身份"
}

# 注册令牌只能通过当前进程环境注入，避免出现在脚本参数历史、配置文件和 Git 记录中。
$registrationToken = $env:GITHUB_RUNNER_REGISTRATION_TOKEN
if ([string]::IsNullOrWhiteSpace($registrationToken)) {
    throw "缺少一次性 GITHUB_RUNNER_REGISTRATION_TOKEN"
}
$env:GITHUB_RUNNER_REGISTRATION_TOKEN = $null

$label = if ($Role -eq "primary") { "agentmind-staging" } else { "agentmind-staging-dr" }
$arguments = @(
    "--unattended",
    "--url", $RepositoryUrl.TrimEnd('/'),
    "--token", $registrationToken,
    "--name", $RunnerName,
    "--labels", $label,
    "--work", $WorkDirectory
)

$isWindows = $IsWindows -or $env:OS -eq "Windows_NT"
if ($isWindows) {
    $configCommand = Join-Path $resolvedRunnerDirectory "config.cmd"
    if (-not (Test-Path -LiteralPath $configCommand -PathType Leaf)) {
        throw "Runner 安装目录缺少 config.cmd，请先下载并校验 GitHub Actions Runner 安装包"
    }
    & $configCommand @arguments
} else {
    $configCommand = Join-Path $resolvedRunnerDirectory "config.sh"
    if (-not (Test-Path -LiteralPath $configCommand -PathType Leaf)) {
        throw "Runner 安装目录缺少 config.sh，请先下载并校验 GitHub Actions Runner 安装包"
    }
    & bash $configCommand @arguments
}
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $configuredMarker)) {
    throw "GitHub Actions Runner 注册失败"
}

if ($InstallService) {
    if ($isWindows) {
        $serviceCommand = Join-Path $resolvedRunnerDirectory "svc.cmd"
        if (-not (Test-Path -LiteralPath $serviceCommand -PathType Leaf)) {
            throw "Runner 安装目录缺少 svc.cmd"
        }
        & $serviceCommand install
        if ($LASTEXITCODE -ne 0) { throw "Runner Windows 服务安装失败" }
        & $serviceCommand start
        if ($LASTEXITCODE -ne 0) { throw "Runner Windows 服务启动失败" }
    } else {
        $serviceCommand = Join-Path $resolvedRunnerDirectory "svc.sh"
        if (-not (Test-Path -LiteralPath $serviceCommand -PathType Leaf)) {
            throw "Runner 安装目录缺少 svc.sh"
        }
        $sudo = Get-Command sudo -ErrorAction SilentlyContinue
        if ($null -eq $sudo) {
            throw "Linux Runner 安装系统服务需要 sudo"
        }
        $installArguments = @($serviceCommand, "install")
        if (-not [string]::IsNullOrWhiteSpace($ServiceUser)) {
            $installArguments += $ServiceUser
        }
        & $sudo.Source @installArguments
        if ($LASTEXITCODE -ne 0) { throw "Runner Linux 服务安装失败" }
        & $sudo.Source $serviceCommand start
        if ($LASTEXITCODE -ne 0) { throw "Runner Linux 服务启动失败" }
    }
}

Write-Host "GitHub Actions Runner 注册完成：名称=$RunnerName，角色=$Role，标签=$label"
Write-Host "一次性注册令牌已从当前进程环境移除。请在 GitHub 仓库设置中确认 Runner 在线。"
