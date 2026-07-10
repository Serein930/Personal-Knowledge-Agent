param(
    [string]$BaseUrl = "http://localhost:8080",
    [long]$WorkspaceId = 1
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

Write-Step "Creating a temporary Markdown document"
$tempDirectory = Join-Path $env:TEMP "agentmind-pgvector"
New-Item -ItemType Directory -Force -Path $tempDirectory | Out-Null
$markdownPath = Join-Path $tempDirectory "agentmind-thread-pool-note.md"

@"
# Java Thread Pool Retrieval Note

Thread pools reuse backend worker threads and reduce task creation overhead.

## Queue Tuning

Core size, maximum size and queue policy should match the workload characteristics.
This note is intentionally small so the manual pgvector retrieval path is easy to verify.
"@ | Set-Content -Path $markdownPath -Encoding UTF8

Write-Step "Uploading Markdown document"
$uploadUri = "$BaseUrl/api/v1/workspaces/$WorkspaceId/documents/files"
$uploadResponse = Invoke-RestMethod `
    -Method Post `
    -Uri $uploadUri `
    -Form @{
        file = Get-Item $markdownPath
        title = "Manual pgvector retrieval note"
        tags = "manual"
    }

$uploadData = $uploadResponse.data
Assert-True ($null -ne $uploadData.documentId) "Upload response did not contain documentId"
Assert-True ($uploadData.status -eq "SUCCEEDED") "Upload did not succeed. Actual status: $($uploadData.status)"
Write-Host "documentId=$($uploadData.documentId), taskId=$($uploadData.taskId), status=$($uploadData.status)"

Write-Step "Reading generated chunks"
$chunksUri = "$BaseUrl/api/v1/workspaces/$WorkspaceId/documents/$($uploadData.documentId)/chunks"
$chunksResponse = Invoke-RestMethod -Method Get -Uri $chunksUri
$chunks = @($chunksResponse.data)
Assert-True ($chunks.Count -gt 0) "No chunks were generated for uploaded document"
Write-Host "chunkCount=$($chunks.Count)"
Write-Host "firstChunk=$($chunks[0].content.Substring(0, [Math]::Min(120, $chunks[0].content.Length)))"

Write-Step "Searching indexed chunks through pgvector-backed API"
$searchUri = "$BaseUrl/api/v1/workspaces/$WorkspaceId/knowledge/search"
$searchBody = @{
    query = "backend worker thread pool queue tuning"
    topK = 5
} | ConvertTo-Json
$searchResponse = Invoke-RestMethod `
    -Method Post `
    -Uri $searchUri `
    -ContentType "application/json" `
    -Body $searchBody

$results = @($searchResponse.data.results)
Assert-True ($results.Count -gt 0) "Search returned no results"
$matchingResult = $results | Where-Object { $_.documentId -eq $uploadData.documentId } | Select-Object -First 1
Assert-True ($null -ne $matchingResult) "Search results did not include uploaded documentId=$($uploadData.documentId)"

Write-Host "topResultChunkId=$($results[0].chunkId)"
Write-Host "topResultScore=$($results[0].score)"
Write-Host "matchedDocumentId=$($matchingResult.documentId)"
Write-Host ""
Write-Host "Manual pgvector retrieval path verified successfully." -ForegroundColor Green
