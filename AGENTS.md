# AGENTS.md

This file is the development charter for this repository. Every AI assistant or human contributor should read it before making code changes.

## Project Identity

Personal Knowledge Agent is a Java-first personal knowledge management Agent platform.

The project is not a generic chatbot. It is designed to help users collect, organize, retrieve, review, and reuse personal learning materials such as PDFs, Markdown notes, web articles, technical blogs, official documentation, code snippets, and interview notes.

The long-term goal is to build a resume-quality Agent project with practical value and meaningful engineering depth.

## Product Goals

- Support personal knowledge ingestion from files and web links.
- Convert raw materials into searchable knowledge assets.
- Provide RAG-based question answering with source citations.
- Generate notes, summaries, flashcards, and study plans through Agent tools.
- Track user learning history, weak knowledge areas, and review progress.
- Provide observability and evaluation for RAG quality and Agent behavior.
- Keep the system usable as a real personal learning tool, not only a demo.

## Non-Goals

- Do not build a simple LLM chat wrapper.
- Do not make model calls directly from controllers.
- Do not mix business logic, persistence, and AI orchestration in one class.
- Do not introduce large infrastructure unless it supports a planned project stage.
- Do not store API keys, tokens, passwords, or private credentials in Git.
- Do not add unrelated features just because they are easy to implement.

## Primary Technology Stack

Backend:

- Java 21
- Spring Boot 3.x
- Spring AI as the primary AI framework
- Spring Web for REST APIs
- Spring Security for authentication and authorization
- Spring Data JPA or MyBatis-Plus for persistence
- Bean Validation for request validation

AI and Agent:

- Spring AI Chat Model abstraction
- Spring AI Embedding Model abstraction
- Spring AI Vector Store abstraction
- Tool Calling for Agent actions
- Chat Memory for conversation context
- RAG pipeline with source citations
- Model evaluation and observability where applicable

Data and Storage:

- PostgreSQL for structured data
- pgvector for vector similarity search
- Redis for cache, session, and lightweight task state
- MinIO for original file storage
- Optional Elasticsearch or OpenSearch for later hybrid search

Document and Web Ingestion:

- Apache Tika for document text extraction
- PDFBox for PDF-specific parsing when needed
- Jsoup for HTML parsing
- Readability-style extraction for web article body extraction
- Optional Playwright/Selenium fallback for JavaScript-rendered pages

Frontend:

- React
- TypeScript
- Vite
- Ant Design or another consistent component library
- Markdown rendering
- PDF preview
- ECharts or Recharts for analytics views
- SSE or WebSocket for streaming responses

DevOps:

- Docker Compose for local development dependencies
- Maven Wrapper for backend builds
- GitHub Actions for CI after the project structure is stable
- OpenAPI/Swagger or Knife4j for API documentation

## Architecture Principles

- Use layered architecture with clear module boundaries.
- Controllers should only handle HTTP concerns and delegate to application services.
- Application services coordinate use cases and transactions.
- Domain services should contain business rules that are independent of transport details.
- Infrastructure adapters should hide external systems such as LLMs, object storage, vector stores, and web crawlers.
- AI orchestration should be explicit and testable.
- Long-running ingestion and indexing tasks should run asynchronously.
- Every RAG answer should be traceable to retrieved sources when the answer depends on user knowledge.
- Agent tool execution must be auditable and permission-aware.

## Suggested Backend Package Structure

Use this structure as the default unless a future design decision changes it:

```text
com.agentmind
  AgentMindApplication
  common
    config
    exception
    response
    security
    validation
  user
    controller
    service
    model
    repository
  workspace
    controller
    service
    model
    repository
  document
    controller
    service
    model
    repository
    parser
    chunk
  ingestion
    service
    task
    web
  knowledge
    service
    model
    repository
    vector
  chat
    controller
    service
    memory
    model
  agent
    service
    tool
    planner
    audit
  study
    controller
    service
    flashcard
    plan
  evaluation
    controller
    service
    metric
```

## Core Functional Modules

### 1. User and Workspace

- User registration and login.
- Personal knowledge spaces.
- Workspace-level document isolation.
- Basic role and permission model.
- User preferences for model, language, and learning goals.

### 2. Document Ingestion

- Upload PDF, Markdown, TXT, Word, HTML, and code files.
- Store original files in object storage.
- Extract text and metadata.
- Clean noisy content.
- Split content into semantic chunks.
- Generate embeddings.
- Store chunk metadata and vectors.
- Track ingestion status and failure reasons.

### 3. Web Article Ingestion

- Submit URLs such as CSDN, blogs, official docs, GitHub README pages, and technical articles.
- Validate URL safety.
- Fetch HTML content.
- Extract title, author, publish time, source site, and main body.
- Remove navigation, ads, comments, and recommendation noise where possible.
- Save raw HTML snapshot when useful.
- Detect duplicate URL and duplicate content by hash.
- Support content versioning when a page changes.

### 4. RAG Question Answering

- Search only within the user's selected workspace by default.
- Support vector retrieval.
- Add keyword or hybrid retrieval in later stages.
- Support metadata filtering by document type, tag, source, and time.
- Return cited sources with document title, chunk id, score, and excerpt.
- Refuse or clarify when no reliable source is found.
- Record prompt, retrieved chunks, model output, token usage, latency, and feedback.

### 5. Agent Tools

Initial tools should be practical and bounded:

- Search documents.
- Read document excerpts.
- Create a note.
- Generate flashcards.
- Generate a study plan.
- Analyze weak knowledge areas.
- Schedule review tasks.
- Summarize a topic.

Tool execution must be logged. Tools that mutate data must validate user permission and workspace ownership.

### 6. Long-Term Memory

- Store conversation summaries.
- Track frequently asked topics.
- Track learned and weak knowledge points.
- Store user learning goals and preferences.
- Avoid sending full historical conversations to the model.
- Use short-term context plus long-term summarized memory.

### 7. Study System

- Generate flashcards from documents and web articles.
- Support review status and mastery level.
- Generate study plans by topic and timeline.
- Track progress over time.
- Recommend review content based on weak areas.

### 8. Evaluation and Observability

- Record each RAG run.
- Record retrieval strategy, topK, scores, prompt version, model name, latency, and token usage.
- Maintain a small evaluation dataset.
- Compare chunking, topK, hybrid retrieval, and reranking strategies.
- Track Recall@K, citation coverage, answer usefulness, average latency, and user feedback.

## RAG Design Rules

- Preserve source metadata during chunking.
- Chunk text by semantic structure where possible.
- Markdown should prefer heading-based splitting.
- PDF parsing should remove repeated headers and footers when possible.
- Code documents should avoid cutting in the middle of classes or methods when possible.
- Retrieval must filter by user and workspace.
- Answers based on private knowledge must include citations.
- If retrieval confidence is low, the assistant should say that available materials are insufficient.
- Do not claim facts from user documents unless retrieved context supports them.

## Web Ingestion Rules

- Validate URL scheme. Only `http` and `https` are allowed.
- Block local, private, and loopback network addresses to avoid SSRF.
- Set request timeout and maximum response size.
- Respect robots, rate limits, and personal-use boundaries.
- Store source URL and crawl time.
- Keep extraction logic replaceable because different sites need different handling.
- Do not rely on one site's DOM structure as a global rule.

## Security Rules

- Never commit secrets.
- Use environment variables or local ignored config files for API keys.
- Validate all request DTOs.
- Enforce user and workspace ownership on every document, vector, chat, note, flashcard, and plan.
- Any Agent tool that writes data must check authorization.
- Any future external tool integration must have an allowlist and audit log.
- Uploaded files must be checked for size, extension, and content type.
- URL ingestion must prevent SSRF.

## API Design Rules

- Use REST APIs for normal CRUD and task management.
- Use SSE or WebSocket for streaming chat responses.
- Use consistent response envelopes.
- Use clear request DTOs and response DTOs.
- Do not expose persistence entities directly from controllers.
- Use pagination for list APIs.
- Use stable error codes for common failures.

## Database Design Rules

- Use explicit primary keys.
- Include `created_at` and `updated_at` for important entities.
- Include user and workspace ownership fields where applicable.
- Use status fields for asynchronous tasks.
- Keep vector chunk metadata queryable.
- Deleting a document must also delete or mark inactive its chunks and vectors.
- Prefer soft delete for user-facing knowledge assets.

## Coding Standards

- Prefer readable, explicit code over clever abstractions.
- Keep methods small enough to test and understand.
- Use constructor injection.
- Use transactions at service boundaries.
- Use domain-specific exceptions and centralized exception handling.
- Log important state transitions, not noisy implementation details.
- Do not swallow exceptions silently.
- Write tests for business rules, parsers, chunking logic, and security-sensitive paths.
- Avoid introducing a new library without a clear project need.

## Testing Strategy

- Unit tests for chunking, parsing, URL validation, and permission checks.
- Integration tests for repositories and core service flows.
- API tests for important controller endpoints.
- RAG evaluation tests should be deterministic where possible by using fixed datasets and mock models.
- Do not require real paid model calls for normal CI.

## Git and Commit Rules

Use conventional commits:

- `chore: initialize project structure`
- `feat: add document ingestion pipeline`
- `fix: prevent SSRF in web ingestion`
- `docs: update architecture guide`
- `test: add chunking strategy tests`
- `refactor: extract vector search adapter`

Commit by meaningful development stage or feature. Avoid mixing unrelated changes in one commit.

## Development Stages

### Stage 1: Project Foundation

- Spring Boot backend scaffold.
- Basic configuration.
- Health check endpoint.
- Common response and exception handling.
- Initial README and architecture documentation.
- Docker Compose for PostgreSQL, Redis, and MinIO if practical.

### Stage 2: User, Workspace, and Document Metadata

- User model.
- Workspace model.
- Document metadata model.
- Basic authentication and authorization.
- Workspace isolation rules.

### Stage 3: File Document Ingestion

- File upload.
- Object storage integration.
- Text extraction.
- Chunking.
- Asynchronous ingestion task status.

### Stage 4: Web Article Ingestion

- URL submission.
- URL safety validation.
- HTML fetch and parsing.
- Main body extraction.
- Duplicate detection and versioning.

### Stage 5: Embedding and Vector Search

- Embedding model integration.
- pgvector setup.
- Chunk vector storage.
- Workspace-filtered semantic search.

### Stage 6: RAG Chat

- Chat endpoint.
- Retrieval-augmented prompt construction.
- Streaming responses.
- Source citations.
- Chat history and short-term memory.

### Stage 7: Agent Tools

- Tool calling integration.
- Note generation.
- Flashcard generation.
- Study plan generation.
- Tool audit log.

### Stage 8: Long-Term Memory and Study System

- Conversation summarization.
- Learning profile.
- Knowledge gap analysis.
- Review scheduling.
- Flashcard review workflow.

### Stage 9: Evaluation and Observability

- RAG trace logs.
- Evaluation dataset.
- Retrieval metrics.
- Token and latency metrics.
- Admin or developer-facing evaluation panel.

## Required Pre-Development Checklist

Before making code changes:

1. Read this `AGENTS.md`.
2. Check the current Git status.
3. Identify the current development stage.
4. Keep changes scoped to the requested feature or stage.
5. Avoid changing public contracts unless the change is intentional.
6. Do not add secrets or machine-local configuration to Git.
7. Run relevant tests or explain why they were not run.

## Resume Positioning

The project should be presented as:

```text
Personal Knowledge Agent Platform
Built a Java and Spring Boot based personal knowledge management Agent platform with document ingestion, web article extraction, RAG question answering, source citation, Agent tool calling, learning plan generation, flashcards, long-term memory, and RAG evaluation.
```

Strong resume keywords:

- Java 21
- Spring Boot
- Spring AI
- RAG
- Tool Calling
- pgvector
- PostgreSQL
- Redis
- MinIO
- Document ingestion
- Web article extraction
- Long-term memory
- RAG evaluation
- Observability
- Agent engineering

## Final Rule

Always keep the project useful first. Technical depth should come from solving real problems with clear architecture, measurable quality, and maintainable code.
