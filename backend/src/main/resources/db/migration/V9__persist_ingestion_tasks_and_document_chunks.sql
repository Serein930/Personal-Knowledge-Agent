-- 持久化摄取任务和文档解析片段，替代进程内 Map。

create table if not exists ingestion_task (
    id bigserial primary key,
    owner_user_id bigint not null references app_user(id),
    workspace_id bigint not null references knowledge_workspace(id),
    document_id bigint not null references knowledge_document(id),
    task_type varchar(40) not null,
    status varchar(30) not null,
    progress integer not null,
    source text,
    error_message text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    started_at timestamptz,
    finished_at timestamptz,
    constraint ck_ingestion_task_progress check (progress between 0 and 100)
);

create index if not exists idx_ingestion_task_workspace_updated
    on ingestion_task (workspace_id, updated_at desc);
create index if not exists idx_ingestion_task_document
    on ingestion_task (document_id, created_at desc);

create table if not exists document_chunk (
    id varchar(160) primary key,
    owner_user_id bigint not null references app_user(id),
    workspace_id bigint not null references knowledge_workspace(id),
    document_id bigint not null references knowledge_document(id) on delete cascade,
    chunk_sequence integer not null,
    heading_path text,
    content text not null,
    char_start integer not null,
    char_end integer not null,
    created_at timestamptz not null default now(),
    constraint uk_document_chunk_sequence unique (document_id, chunk_sequence),
    constraint ck_document_chunk_range check (char_start >= 0 and char_end >= char_start)
);

create index if not exists idx_document_chunk_workspace_document
    on document_chunk (workspace_id, document_id, chunk_sequence);
