-- 生产身份、知识空间成员和文档元数据。

create table if not exists app_user (
    id bigserial primary key,
    username varchar(64) not null,
    display_name varchar(100) not null,
    email varchar(254) not null,
    password_hash varchar(200) not null,
    role varchar(20) not null,
    status varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_app_user_role check (role in ('USER', 'ADMIN')),
    constraint ck_app_user_status check (status in ('ACTIVE', 'DISABLED'))
);

create unique index if not exists uk_app_user_username_lower on app_user (lower(username));
create unique index if not exists uk_app_user_email_lower on app_user (lower(email));

create table if not exists knowledge_workspace (
    id bigserial primary key,
    owner_user_id bigint not null references app_user(id),
    name varchar(120) not null,
    description varchar(1000) not null default '',
    visibility varchar(20) not null default 'PRIVATE',
    default_model varchar(120),
    default_embedding_model varchar(120),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz,
    constraint ck_workspace_visibility check (visibility in ('PRIVATE'))
);

create index if not exists idx_workspace_owner on knowledge_workspace (owner_user_id, created_at desc);

create table if not exists workspace_member (
    workspace_id bigint not null references knowledge_workspace(id),
    user_id bigint not null references app_user(id),
    member_role varchar(20) not null,
    created_at timestamptz not null,
    primary key (workspace_id, user_id),
    constraint ck_workspace_member_role check (member_role in ('OWNER', 'EDITOR', 'VIEWER'))
);

create index if not exists idx_workspace_member_user on workspace_member (user_id, workspace_id);

create table if not exists knowledge_document (
    id bigserial primary key,
    owner_user_id bigint not null references app_user(id),
    workspace_id bigint not null references knowledge_workspace(id),
    title varchar(300) not null,
    source_type varchar(30) not null,
    source_uri text,
    original_filename varchar(500),
    storage_key varchar(1000),
    content_type varchar(200),
    content_size bigint not null default 0,
    content_hash varchar(128),
    tags jsonb not null default '[]'::jsonb,
    ingestion_status varchar(30) not null,
    chunk_count integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz,
    constraint ck_document_content_size check (content_size >= 0),
    constraint ck_document_chunk_count check (chunk_count >= 0)
);

create index if not exists idx_document_workspace_updated
    on knowledge_document (workspace_id, updated_at desc) where deleted_at is null;
create index if not exists idx_document_owner_workspace
    on knowledge_document (owner_user_id, workspace_id) where deleted_at is null;
create index if not exists idx_document_tags on knowledge_document using gin (tags);
