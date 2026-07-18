param(
    [string]$Profiles = "local,local-ai,opensearch",
    [string]$JvmArguments = "-Xms256m -Xmx1g -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
)

$utf8 = [System.Text.Encoding]::GetEncoding(65001)
$OutputEncoding = $utf8
& "$env:SystemRoot\System32\chcp.com" 65001 | Out-Null

$backendRoot = Split-Path -Parent $PSScriptRoot
Push-Location $backendRoot
try {
    & ".\mvnw.cmd" spring-boot:run "-Dspring-boot.run.profiles=$Profiles" "-Dspring-boot.run.jvmArguments=$JvmArguments"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
