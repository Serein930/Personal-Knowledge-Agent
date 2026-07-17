-- 将早期 V1 Java 基线中的向量表固化为可审计的 SQL 迁移。
-- 所有语句保持幂等，以兼容已经执行过旧基线的数据库。
-- PostgreSQL 必须已安装 pgvector 扩展，Flyway 会在迁移事务中启用该扩展。

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

-- ivfflat 适合已有一定数据量的向量表，lists 参数应在积累评估数据后继续调优。
create index if not exists idx_kvc_embedding_ivfflat
    on knowledge_vector_chunks
    using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);
