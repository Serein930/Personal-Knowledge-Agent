-- Stage 7 写工具持久化表。
-- 所有用户资产都同时保留用户与知识空间归属，避免仅凭业务编号跨空间读取。

create table if not exists agent_tool_confirmations (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    conversation_id bigint,
    message_id bigint,
    request_id varchar(100) not null,
    tool_name varchar(100) not null,
    arguments_json jsonb not null,
    argument_summary varchar(500) not null,
    token_digest varchar(100) not null,
    status varchar(40) not null,
    execution_response_json jsonb,
    failure_reason varchar(1000),
    created_at timestamptz not null,
    expires_at timestamptz not null,
    updated_at timestamptz not null,
    executed_at timestamptz,
    constraint ck_agent_tool_confirmation_status check (
        status in ('PENDING_CONFIRMATION', 'EXECUTING', 'SUCCEEDED', 'REJECTED', 'EXPIRED', 'FAILED')
    )
);

create index if not exists idx_agent_tool_confirmations_scope
    on agent_tool_confirmations (owner_user_id, workspace_id, id);

create index if not exists idx_agent_tool_confirmations_expiration
    on agent_tool_confirmations (status, expires_at)
    where status = 'PENDING_CONFIRMATION';

create index if not exists idx_agent_tool_confirmations_executing_recovery
    on agent_tool_confirmations (status, updated_at)
    where status = 'EXECUTING';

create table if not exists agent_tool_call_audits (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    conversation_id bigint,
    message_id bigint,
    request_id varchar(100),
    tool_name varchar(100) not null,
    tool_type varchar(20),
    request_summary varchar(1000) not null,
    request_fingerprint varchar(64) not null,
    response_summary varchar(1000),
    status varchar(20) not null,
    error_message varchar(1000),
    latency_ms bigint not null default 0,
    created_at timestamptz not null,
    constraint ck_agent_tool_audit_type check (tool_type is null or tool_type in ('READ', 'WRITE', 'ANALYSIS')),
    constraint ck_agent_tool_audit_status check (status in ('PENDING', 'SUCCEEDED', 'FAILED', 'SKIPPED'))
);

create index if not exists idx_agent_tool_audits_scope_created
    on agent_tool_call_audits (owner_user_id, workspace_id, created_at desc, id desc);

create index if not exists idx_agent_tool_audits_message
    on agent_tool_call_audits (owner_user_id, workspace_id, conversation_id, message_id, created_at, id);

create unique index if not exists uk_agent_tool_audits_success_request
    on agent_tool_call_audits (owner_user_id, workspace_id, tool_name, request_id)
    where status = 'SUCCEEDED' and request_id is not null;

create table if not exists knowledge_notes (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    source_conversation_id bigint,
    request_id varchar(100) not null,
    title varchar(120) not null,
    content text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_knowledge_note_title check (char_length(title) between 1 and 120),
    constraint ck_knowledge_note_content check (char_length(content) between 1 and 20000),
    constraint uk_knowledge_notes_request unique (owner_user_id, workspace_id, request_id)
);

create index if not exists idx_knowledge_notes_scope_created
    on knowledge_notes (owner_user_id, workspace_id, created_at desc, id desc);

create table if not exists study_flashcards (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    source_conversation_id bigint,
    request_id varchar(100) not null,
    question varchar(500) not null,
    answer text not null,
    explanation text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_study_flashcard_question check (char_length(question) between 1 and 500),
    constraint ck_study_flashcard_answer check (char_length(answer) between 1 and 10000),
    constraint uk_study_flashcards_request unique (owner_user_id, workspace_id, request_id)
);

create index if not exists idx_study_flashcards_scope_created
    on study_flashcards (owner_user_id, workspace_id, created_at desc, id desc);
