-- 知识索引事务消息表。
-- pgvector 更新和消息写入处于同一数据库事务，OpenSearch 由后台消费者最终同步。

create table if not exists knowledge_index_outbox (
    id bigserial primary key,
    event_key varchar(200) not null,
    workspace_id bigint not null,
    document_id bigint not null,
    operation varchar(20) not null,
    payload jsonb not null,
    status varchar(20) not null default 'PENDING',
    attempts integer not null default 0 check (attempts >= 0),
    available_at timestamptz not null,
    lease_owner varchar(200) not null default '',
    lease_expires_at timestamptz,
    last_error varchar(2000) not null default '',
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_knowledge_index_outbox_event_key unique (event_key),
    constraint ck_knowledge_index_outbox_operation check (operation in ('UPSERT', 'DELETE')),
    constraint ck_knowledge_index_outbox_status
        check (status in ('PENDING', 'PROCESSING', 'RETRY', 'COMPLETED', 'DEAD'))
);

create index if not exists idx_knowledge_index_outbox_dispatch
    on knowledge_index_outbox (available_at, id)
    where status in ('PENDING', 'RETRY');

create index if not exists idx_knowledge_index_outbox_expired_lease
    on knowledge_index_outbox (lease_expires_at, id)
    where status = 'PROCESSING';

create index if not exists idx_knowledge_index_outbox_scope
    on knowledge_index_outbox (workspace_id, document_id, created_at desc);
