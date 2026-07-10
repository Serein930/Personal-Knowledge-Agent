# pgvector Retrieval Runbook

This runbook verifies the Stage 5 real retrieval path:

```text
Markdown upload -> text extraction -> chunking -> embedding -> pgvector write -> /knowledge/search
```

The current embedding implementation is deterministic and local. It is not a production-quality semantic model, but it
is enough to verify that the storage and retrieval path works against PostgreSQL + pgvector.

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop
- PowerShell
- Port `5432` available locally

## 1. Start pgvector

From the repository root:

```powershell
cd D:\Program\AgentMind
docker compose up -d agentmind-postgres
```

Check container health:

```powershell
docker compose ps
```

The first startup initializes the `knowledge_vector_chunks` table through:

```text
backend/src/main/resources/db/schema/knowledge_vector_chunks.sql
```

If you previously started the container before this schema existed, recreate the volume:

```powershell
docker compose down -v
docker compose up -d agentmind-postgres
```

## 2. Start Backend With Local Profile

Use Java 21 for this terminal session:

```powershell
$env:JAVA_HOME='D:\Tools\Java21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Start the backend:

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile switches the vector store to pgvector:

```yaml
agentmind:
  vector-store:
    type: pgvector
```

## 3. Run The Manual Retrieval Script

Open a second PowerShell window:

```powershell
cd D:\Program\AgentMind
powershell -ExecutionPolicy Bypass -File .\scripts\manual-pgvector-retrieval.ps1
```

The script does the following:

1. Creates a temporary Markdown file.
2. Uploads it to `POST /api/v1/workspaces/1/documents/files`.
3. Reads the generated `documentId` and `taskId`.
4. Calls `GET /api/v1/workspaces/1/documents/{documentId}/chunks`.
5. Calls `POST /api/v1/workspaces/1/knowledge/search`.

Expected result:

- Upload response status is `SUCCEEDED`.
- At least one chunk is returned.
- Search results include the uploaded document id.
- The top result content mentions `thread pool` or `backend worker`.

## 4. Optional Database Check

You can verify pgvector rows directly:

```powershell
docker exec -it agentmind-postgres psql -U agentmind -d agentmind
```

Then run:

```sql
select workspace_id, document_id, chunk_id, chunk_sequence, left(content, 80)
from knowledge_vector_chunks
order by created_at desc
limit 5;
```

## 5. Cleanup

Stop services but keep data:

```powershell
docker compose down
```

Remove the local database volume:

```powershell
docker compose down -v
```

## Troubleshooting

- If backend startup says `spring.datasource.url is required`, confirm the `local` profile is active.
- If the backend cannot connect to PostgreSQL, check `docker compose ps` and make sure port `5432` is not occupied.
- If search returns no results, call the chunks endpoint first. If chunks are empty, parsing/chunking did not produce indexable content.
- If `mvn` uses Java 17, set `JAVA_HOME` to Java 21 in the current terminal before running Maven.
