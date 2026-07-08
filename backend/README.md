# AgentMind Backend

Personal Knowledge Agent backend service.

## Current Stage

The backend is now in the document parsing and chunking preparation stage.

Implemented:

- Java 21 + Spring Boot 3.x Maven project.
- Unified API response, page response and global exception handling.
- Health check endpoint.
- Document and ingestion task DTO contracts.
- File upload validation and local object storage adapter.
- URL safety validation and raw HTML fetch skeleton.
- Markdown, TXT/code and HTML text extraction.
- Basic deterministic chunking with Markdown heading awareness.
- Temporary in-memory chunk preview endpoint for development verification.

Not implemented yet:

- Database persistence.
- Authentication and authorization.
- Real MinIO deployment adapter.
- PDF/Word text extraction.
- Embedding model integration.
- pgvector semantic retrieval.
- Spring AI RAG chat.

## Run

Requirements:

- JDK 21
- Maven 3.9+

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run
```

Health check:

```text
GET http://localhost:8080/api/v1/health
```

## Test

```powershell
cd D:\Program\AgentMind\backend
mvn test
```

## Ingestion APIs

List documents:

```text
GET http://localhost:8080/api/v1/workspaces/1/documents?page=1&pageSize=20
```

Upload a Markdown/TXT/HTML file:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/documents/files" `
  -Form @{ file = Get-Item ".\README.md"; title = "README document"; tags = "docs" }
```

Capture a web page:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/documents/web-pages" `
  -ContentType "application/json" `
  -Body '{"url":"https://example.com/article","title":"Example article","tags":["Web","Test"]}'
```

Query ingestion task:

```text
GET http://localhost:8080/api/v1/workspaces/1/ingestion-tasks/{taskId}
```

Preview generated chunks:

```text
GET http://localhost:8080/api/v1/workspaces/1/documents/{documentId}/chunks
```

Notes:

- Uploaded files and fetched HTML snapshots are stored under `.agentmind-storage`, which is ignored by Git.
- Markdown, TXT/code and HTML can generate chunks in the current stage.
- PDF and Word files can still be stored, but parser support is reserved for a later stage.
- Document, task and chunk data are currently stored in memory and reset when the service restarts.
