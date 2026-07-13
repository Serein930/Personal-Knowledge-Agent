-- 检索增强生成模型调用观测记录表。
--
-- 当前阶段由本地开发者或容器初始化脚本执行。应用默认使用内存仓库，
-- 只有显式切换 agentmind.rag.observation-store=jdbc 时才写入该表。

create table if not exists rag_model_call_observations (
    id varchar(64) primary key,
    workspace_id bigint not null,
    prompt_version varchar(128) not null,
    answer_generator varchar(64) not null,
    model_name varchar(128) not null,
    citation_count integer not null,
    refused boolean not null,
    status varchar(32) not null,
    elapsed_millis bigint not null,
    answer_length integer not null,
    failure_reason text not null default '',
    created_at timestamp with time zone not null
);

create index if not exists idx_rmco_workspace_created_at
    on rag_model_call_observations (workspace_id, created_at desc);

create index if not exists idx_rmco_workspace_status_created_at
    on rag_model_call_observations (workspace_id, status, created_at desc);
