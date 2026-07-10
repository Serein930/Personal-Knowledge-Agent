# AgentMind Backend

Personal Knowledge Agent backend service.

## Current Stage

The backend is now in Stage 5 preparation: local embeddings and workspace-scoped mock vector search.

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
- Deterministic local embedding client for development.
- In-memory vector store with workspace-scoped semantic search.
- PostgreSQL + pgvector schema and `VectorStore` adapter skeleton.

Not implemented yet:

- Database persistence.
- Authentication and authorization.
- Real MinIO deployment adapter.
- PDF/Word text extraction.
- Real Spring AI embedding model integration.
- Production validation of PostgreSQL + pgvector retrieval quality.
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

Search indexed chunks in the current workspace:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/knowledge/search" `
  -ContentType "application/json" `
  -Body '{"query":"thread pool worker threads","topK":5}'
```

## PostgreSQL + pgvector Adapter

The default vector store is still memory:

```yaml
agentmind:
  vector-store:
    type: memory
    embedding-dimensions: 128
```

To switch to PostgreSQL + pgvector later:

1. Start the local pgvector database from the repository root:

```powershell
cd D:\Program\AgentMind
docker compose up -d agentmind-postgres
```

2. The container automatically runs `backend/src/main/resources/db/schema/knowledge_vector_chunks.sql` on first
   database initialization.
3. Start the backend with the `local` profile:

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The committed `application-local.yml` uses the local Docker database:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/agentmind
    username: agentmind
    password: your-local-password

agentmind:
  vector-store:
    type: pgvector
    embedding-dimensions: 128
```

The application service still depends on the `VectorStore` interface, so the memory adapter and pgvector adapter can
be swapped by configuration.

Manual pgvector integration test:

- Start Docker Compose first.
- Open `PgVectorStoreIntegrationTests`.
- Temporarily remove or override `@Disabled`.
- Run that test class from IDEA or Maven.

The test is intentionally disabled by default so normal CI and local unit tests do not require Docker.

End-to-end pgvector retrieval runbook:

```text
docs/PGVECTOR_RETRIEVAL_RUNBOOK.md
```

The runbook covers Docker startup, `local` profile startup, Markdown upload, chunk preview, `/knowledge/search`, and
optional direct database inspection.

Notes:

- Uploaded files and fetched HTML snapshots are stored under `.agentmind-storage`, which is ignored by Git.
- Markdown, TXT/code and HTML can generate chunks in the current stage.
- Generated chunks are indexed into the current in-memory vector store.
- The current embedding implementation is deterministic and dependency-free; it is only for verifying retrieval flow.
- The pgvector adapter is available behind a configuration switch, but it is not enabled by default.
- PDF and Word files can still be stored, but parser support is reserved for a later stage.
- Document, task, chunk and vector data are currently stored in memory and reset when the service restarts.
