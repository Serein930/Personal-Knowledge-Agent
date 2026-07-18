-- 用户在知识空间内的模型与回答偏好，不保存任何供应商密钥。

create table if not exists user_workspace_preference (
    user_id bigint not null references app_user(id) on delete cascade,
    workspace_id bigint not null references knowledge_workspace(id) on delete cascade,
    chat_model varchar(120) not null,
    embedding_model varchar(120) not null,
    citation_policy varchar(30) not null,
    default_top_k smallint not null,
    version bigint not null default 1,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (user_id, workspace_id),
    constraint ck_user_workspace_preference_citation
        check (citation_policy in ('REQUIRED', 'WHEN_AVAILABLE')),
    constraint ck_user_workspace_preference_top_k
        check (default_top_k between 1 and 20),
    constraint ck_user_workspace_preference_version
        check (version >= 0)
);

create index if not exists idx_user_workspace_preference_workspace
    on user_workspace_preference (workspace_id, updated_at desc);
