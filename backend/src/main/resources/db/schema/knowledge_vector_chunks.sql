-- PostgreSQL + pgvector schema for AgentMind semantic retrieval.
--
-- This script is intentionally separated from application startup for the current stage.
-- Run it manually after PostgreSQL and the pgvector extension are available.

create extension if not exists vector;

create table if not exists knowledge_vector_chunks (
    id varchar(128) primary key,
    workspace_id bigint not null,
    document_id bigint not null,
    chunk_id varchar(128) not null,
    chunk_sequence integer not null,
    heading_path varchar(512),
    content text not null,
    embedding vector(128) not null,
    created_at timestamp with time zone not null default now()
);

create index if not exists idx_kvc_workspace_document
    on knowledge_vector_chunks (workspace_id, document_id);

create index if not exists idx_kvc_workspace_chunk
    on knowledge_vector_chunks (workspace_id, chunk_id);

-- ivfflat is suitable once the table has enough rows. Lists can be tuned after evaluation data is available.
create index if not exists idx_kvc_embedding_ivfflat
    on knowledge_vector_chunks
    using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);
