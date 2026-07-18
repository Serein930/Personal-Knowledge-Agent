param(
    [string]$Profiles = "local,local-ai,opensearch"
)

# Windows PowerShell 5 默认仍可能使用 GBK 控制台代码页。这里统一终端与 Java 日志为 UTF-8，
# 只修改当前脚本进程，不改操作系统全局设置，也不会读取或输出模型密钥。
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
& "$env:SystemRoot\System32\chcp.com" 65001 | Out-Null

$backendRoot = Split-Path -Parent $PSScriptRoot
Push-Location $backendRoot
try {
    & ".\mvnw.cmd" spring-boot:run "-Dspring-boot.run.profiles=$Profiles"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
