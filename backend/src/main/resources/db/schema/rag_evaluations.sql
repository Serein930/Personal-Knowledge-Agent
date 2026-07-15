-- Stage 9：固定检索增强生成评估集、不可变版本与任务结果。
-- JSONB 保存评估题目和逐题证据快照，关系字段负责知识空间隔离、版本唯一性和基线追溯。

create table if not exists rag_evaluation_datasets (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    name varchar(120) not null,
    description varchar(1000) not null default '',
    latest_version integer not null default 0 check (latest_version >= 0),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (id, owner_user_id, workspace_id)
);

create unique index if not exists uk_rag_evaluation_dataset_scope_name
    on rag_evaluation_datasets (owner_user_id, workspace_id, lower(name));

create index if not exists idx_rag_evaluation_dataset_scope_updated
    on rag_evaluation_datasets (owner_user_id, workspace_id, updated_at desc, id desc);

create table if not exists rag_evaluation_dataset_versions (
    id bigserial primary key,
    dataset_id bigint not null,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    version integer not null check (version > 0),
    change_note varchar(500) not null default '',
    cases jsonb not null check (jsonb_typeof(cases) = 'array'),
    created_at timestamptz not null,
    unique (dataset_id, version),
    unique (dataset_id, version, owner_user_id, workspace_id),
    constraint fk_rag_evaluation_version_dataset_scope
        foreign key (dataset_id, owner_user_id, workspace_id)
        references rag_evaluation_datasets (id, owner_user_id, workspace_id)
        on delete cascade
);

create index if not exists idx_rag_evaluation_version_scope
    on rag_evaluation_dataset_versions (owner_user_id, workspace_id, dataset_id, version desc);

create table if not exists rag_evaluation_jobs (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    dataset_id bigint not null,
    dataset_version integer not null,
    status varchar(20) not null check (status in ('RUNNING', 'SUCCEEDED', 'FAILED')),
    retrieval_strategy varchar(100) not null,
    top_k integer not null check (top_k between 1 and 20),
    prompt_version varchar(100) not null,
    model_name varchar(200) not null,
    baseline_job_id bigint,
    metrics jsonb,
    case_results jsonb not null default '[]'::jsonb check (jsonb_typeof(case_results) = 'array'),
    failure_reason varchar(1000) not null default '',
    created_at timestamptz not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    unique (id, owner_user_id, workspace_id),
    constraint fk_rag_evaluation_job_version_scope
        foreign key (dataset_id, dataset_version, owner_user_id, workspace_id)
        references rag_evaluation_dataset_versions (dataset_id, version, owner_user_id, workspace_id),
    constraint fk_rag_evaluation_job_baseline
        foreign key (baseline_job_id)
        references rag_evaluation_jobs (id)
        on delete set null
);

create index if not exists idx_rag_evaluation_job_scope_created
    on rag_evaluation_jobs (owner_user_id, workspace_id, created_at desc, id desc);

create index if not exists idx_rag_evaluation_job_baseline_lookup
    on rag_evaluation_jobs (
        owner_user_id, workspace_id, dataset_id, dataset_version, status, completed_at desc, id desc
    );
