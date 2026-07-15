-- Stage 7 写工具与 Stage 8 复习调度持久化表。
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
    source_document_id bigint,
    topic varchar(100),
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
    status varchar(20) not null default 'NEW',
    repetition_count integer not null default 0,
    interval_days integer not null default 0,
    ease_factor numeric(5, 2) not null default 2.50,
    lapse_count integer not null default 0,
    due_at timestamptz not null default now(),
    last_reviewed_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_study_flashcard_question check (char_length(question) between 1 and 500),
    constraint ck_study_flashcard_answer check (char_length(answer) between 1 and 10000),
    constraint ck_study_flashcard_status check (status in ('NEW', 'LEARNING', 'REVIEW', 'SUSPENDED')),
    constraint ck_study_flashcard_schedule_numbers check (
        repetition_count >= 0 and interval_days >= 0 and ease_factor >= 1.30 and lapse_count >= 0 and version >= 0
    ),
    constraint uk_study_flashcards_request unique (owner_user_id, workspace_id, request_id),
    constraint uk_study_flashcards_scope_id unique (id, owner_user_id, workspace_id)
);

-- 允许已有本地数据库重复执行本脚本，平滑补齐 Stage 8 调度字段。
alter table study_flashcards add column if not exists status varchar(20) not null default 'NEW';
alter table study_flashcards add column if not exists repetition_count integer not null default 0;
alter table study_flashcards add column if not exists interval_days integer not null default 0;
alter table study_flashcards add column if not exists ease_factor numeric(5, 2) not null default 2.50;
alter table study_flashcards add column if not exists lapse_count integer not null default 0;
alter table study_flashcards add column if not exists due_at timestamptz not null default now();
alter table study_flashcards add column if not exists last_reviewed_at timestamptz;
alter table study_flashcards add column if not exists version bigint not null default 0;
alter table study_flashcards add column if not exists source_document_id bigint;
alter table study_flashcards add column if not exists topic varchar(100);

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'ck_study_flashcard_status') then
        alter table study_flashcards add constraint ck_study_flashcard_status
            check (status in ('NEW', 'LEARNING', 'REVIEW', 'SUSPENDED'));
    end if;
    if not exists (select 1 from pg_constraint where conname = 'ck_study_flashcard_schedule_numbers') then
        alter table study_flashcards add constraint ck_study_flashcard_schedule_numbers
            check (repetition_count >= 0 and interval_days >= 0 and ease_factor >= 1.30
                and lapse_count >= 0 and version >= 0);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'uk_study_flashcards_scope_id') then
        alter table study_flashcards add constraint uk_study_flashcards_scope_id
            unique (id, owner_user_id, workspace_id);
    end if;
end $$;

create index if not exists idx_study_flashcards_scope_created
    on study_flashcards (owner_user_id, workspace_id, created_at desc, id desc);

create index if not exists idx_study_flashcards_due
    on study_flashcards (owner_user_id, workspace_id, due_at, id)
    where status <> 'SUSPENDED';

create index if not exists idx_study_flashcards_topic
    on study_flashcards (owner_user_id, workspace_id, topic)
    where topic is not null;

create index if not exists idx_study_flashcards_source_document
    on study_flashcards (owner_user_id, workspace_id, source_document_id)
    where source_document_id is not null;

create table if not exists study_flashcard_reviews (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    flashcard_id bigint not null,
    request_id varchar(100) not null,
    score integer not null,
    previous_status varchar(20) not null,
    next_status varchar(20) not null,
    previous_interval_days integer not null,
    next_interval_days integer not null,
    previous_ease_factor numeric(5, 2) not null,
    next_ease_factor numeric(5, 2) not null,
    previous_due_at timestamptz not null,
    next_due_at timestamptz not null,
    algorithm varchar(40) not null,
    reviewed_at timestamptz not null,
    created_at timestamptz not null,
    constraint ck_study_flashcard_review_score check (score between 0 and 5),
    constraint ck_study_flashcard_review_status check (
        previous_status in ('NEW', 'LEARNING', 'REVIEW', 'SUSPENDED')
        and next_status in ('NEW', 'LEARNING', 'REVIEW', 'SUSPENDED')
    ),
    constraint ck_study_flashcard_review_intervals check (
        previous_interval_days >= 0 and next_interval_days >= 0
        and previous_ease_factor >= 1.30 and next_ease_factor >= 1.30
    ),
    constraint uk_study_flashcard_reviews_request unique (owner_user_id, workspace_id, request_id),
    constraint fk_study_flashcard_reviews_scope foreign key (flashcard_id, owner_user_id, workspace_id)
        references study_flashcards (id, owner_user_id, workspace_id) on delete cascade
);

create index if not exists idx_study_flashcard_reviews_card
    on study_flashcard_reviews (owner_user_id, workspace_id, flashcard_id, reviewed_at desc, id desc);

create table if not exists study_review_sessions (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    status varchar(20) not null,
    total_cards integer not null,
    reviewed_cards integer not null default 0,
    correct_cards integer not null default 0,
    started_at timestamptz not null,
    paused_at timestamptz,
    completed_at timestamptz,
    abandoned_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_study_review_session_status check (status in ('IN_PROGRESS', 'PAUSED', 'COMPLETED', 'ABANDONED')),
    constraint ck_study_review_session_counts check (
        total_cards > 0 and reviewed_cards >= 0 and correct_cards >= 0
        and correct_cards <= reviewed_cards and reviewed_cards <= total_cards
    ),
    constraint uk_study_review_sessions_scope_id unique (id, owner_user_id, workspace_id)
);

alter table study_review_sessions add column if not exists paused_at timestamptz;
alter table study_review_sessions add column if not exists abandoned_at timestamptz;
alter table study_review_sessions drop constraint if exists ck_study_review_session_status;
alter table study_review_sessions add constraint ck_study_review_session_status
    check (status in ('IN_PROGRESS', 'PAUSED', 'COMPLETED', 'ABANDONED'));

create index if not exists idx_study_review_sessions_scope_started
    on study_review_sessions (owner_user_id, workspace_id, started_at desc, id desc);

create table if not exists study_review_session_items (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    session_id bigint not null,
    flashcard_id bigint not null,
    position integer not null,
    status varchar(20) not null,
    score integer,
    reviewed_at timestamptz,
    created_at timestamptz not null,
    constraint ck_study_review_session_item_status check (status in ('PENDING', 'REVIEWED')),
    constraint ck_study_review_session_item_position check (position > 0),
    constraint ck_study_review_session_item_score check (score is null or score between 0 and 5),
    constraint uk_study_review_session_card unique (owner_user_id, workspace_id, session_id, flashcard_id),
    constraint uk_study_review_session_position unique (owner_user_id, workspace_id, session_id, position),
    constraint fk_study_review_session_items_session foreign key (session_id, owner_user_id, workspace_id)
        references study_review_sessions (id, owner_user_id, workspace_id) on delete cascade,
    constraint fk_study_review_session_items_card foreign key (flashcard_id, owner_user_id, workspace_id)
        references study_flashcards (id, owner_user_id, workspace_id) on delete cascade
);

create index if not exists idx_study_review_session_items_queue
    on study_review_session_items (owner_user_id, workspace_id, session_id, position);

create table if not exists daily_study_plans (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    plan_date date not null,
    daily_review_target integer not null,
    due_card_snapshot integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_daily_study_plan_target check (daily_review_target between 1 and 500),
    constraint ck_daily_study_plan_due_snapshot check (due_card_snapshot >= 0),
    constraint uk_daily_study_plan_date unique (owner_user_id, workspace_id, plan_date),
    constraint uk_daily_study_plan_scope_id unique (id, owner_user_id, workspace_id)
);

create index if not exists idx_daily_study_plans_scope_date
    on daily_study_plans (owner_user_id, workspace_id, plan_date desc);

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'uk_daily_study_plan_scope_id') then
        alter table daily_study_plans add constraint uk_daily_study_plan_scope_id
            unique (id, owner_user_id, workspace_id);
    end if;
end $$;

-- FSRS 卡片状态单独保存，避免通用卡片表绑定第三方算法字段。
create table if not exists study_flashcard_fsrs_states (
    owner_user_id bigint not null,
    workspace_id bigint not null,
    flashcard_id bigint not null,
    algorithm_version varchar(50) not null,
    schema_version integer not null,
    profile_version bigint not null default 0,
    payload jsonb not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (owner_user_id, workspace_id, flashcard_id),
    constraint ck_study_flashcard_fsrs_schema check (schema_version > 0),
    constraint ck_study_flashcard_fsrs_profile_version check (profile_version >= 0),
    constraint fk_study_flashcard_fsrs_scope foreign key (flashcard_id, owner_user_id, workspace_id)
        references study_flashcards (id, owner_user_id, workspace_id) on delete cascade
);

alter table study_flashcard_fsrs_states add column if not exists profile_version bigint not null default 0;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'ck_study_flashcard_fsrs_profile_version') then
        alter table study_flashcard_fsrs_states add constraint ck_study_flashcard_fsrs_profile_version
            check (profile_version >= 0);
    end if;
end $$;

create table if not exists fsrs_user_profiles (
    owner_user_id bigint primary key,
    parameters jsonb not null,
    desired_retention numeric(5, 4) not null,
    version bigint not null,
    source varchar(20) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_fsrs_user_profile_retention check (desired_retention between 0.70 and 0.99),
    constraint ck_fsrs_user_profile_version check (version >= 0),
    constraint ck_fsrs_user_profile_source check (source in ('DEFAULT', 'MANUAL', 'OPTIMIZED')),
    constraint ck_fsrs_user_profile_parameters check (
        jsonb_typeof(parameters) = 'array' and jsonb_array_length(parameters) > 0
    )
);

create table if not exists fsrs_parameter_optimization_jobs (
    id bigserial primary key,
    owner_user_id bigint not null,
    status varchar(20) not null,
    review_count integer not null,
    observed_lapse_rate numeric(6, 2) not null,
    previous_desired_retention numeric(5, 4) not null,
    recommended_desired_retention numeric(5, 4) not null,
    applied boolean not null,
    message varchar(500) not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint ck_fsrs_optimization_status check (status in ('RUNNING', 'SUCCEEDED', 'SKIPPED', 'FAILED')),
    constraint ck_fsrs_optimization_review_count check (review_count >= 0),
    constraint ck_fsrs_optimization_lapse_rate check (observed_lapse_rate between 0 and 100)
);

create index if not exists idx_fsrs_optimization_owner_created
    on fsrs_parameter_optimization_jobs (owner_user_id, created_at desc, id desc);

-- Stage 8 个性化学习增强：为真正的权重拟合补充权重、损失和应用版本证据。
alter table fsrs_parameter_optimization_jobs
    add column if not exists effective_observation_count integer not null default 0;
alter table fsrs_parameter_optimization_jobs
    add column if not exists previous_parameters jsonb not null default '[]'::jsonb;
alter table fsrs_parameter_optimization_jobs
    add column if not exists recommended_parameters jsonb not null default '[]'::jsonb;
alter table fsrs_parameter_optimization_jobs
    add column if not exists training_loss_before numeric(12, 6) not null default 0;
alter table fsrs_parameter_optimization_jobs
    add column if not exists training_loss_after numeric(12, 6) not null default 0;
alter table fsrs_parameter_optimization_jobs
    add column if not exists validation_loss_before numeric(12, 6) not null default 0;
alter table fsrs_parameter_optimization_jobs
    add column if not exists validation_loss_after numeric(12, 6) not null default 0;
alter table fsrs_parameter_optimization_jobs
    add column if not exists accepted boolean not null default false;
alter table fsrs_parameter_optimization_jobs
    add column if not exists applied_version bigint;

alter table fsrs_user_profiles drop constraint if exists ck_fsrs_user_profile_source;
alter table fsrs_user_profiles add constraint ck_fsrs_user_profile_source
    check (source in ('DEFAULT', 'MANUAL', 'OPTIMIZED', 'ROLLBACK'));

create table if not exists fsrs_user_profile_versions (
    owner_user_id bigint not null,
    version bigint not null,
    parameters jsonb not null,
    desired_retention numeric(5, 4) not null,
    source varchar(20) not null,
    change_reason varchar(500) not null,
    created_at timestamptz not null,
    primary key (owner_user_id, version),
    constraint ck_fsrs_profile_version_number check (version >= 0),
    constraint ck_fsrs_profile_version_retention check (desired_retention between 0.70 and 0.99),
    constraint ck_fsrs_profile_version_source check (source in ('DEFAULT', 'MANUAL', 'OPTIMIZED', 'ROLLBACK')),
    constraint ck_fsrs_profile_version_parameters check (
        jsonb_typeof(parameters) = 'array' and jsonb_array_length(parameters) > 0
    )
);

-- 学习任务保存生成时选中的卡片集合，使历史计划可解释且不会随实时排期漂移。
create table if not exists daily_study_tasks (
    id bigserial primary key,
    plan_id bigint not null,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    task_type varchar(30) not null,
    priority varchar(10) not null,
    topic varchar(100),
    source_document_id bigint,
    target_card_count integer not null,
    reason varchar(500) not null,
    created_at timestamptz not null,
    constraint ck_daily_study_task_type check (
        task_type in ('DUE_REVIEW', 'WEAK_POINT_REVIEW', 'TOPIC_REVIEW', 'DOCUMENT_REVIEW')
    ),
    constraint ck_daily_study_task_priority check (priority in ('HIGH', 'MEDIUM', 'LOW')),
    constraint ck_daily_study_task_target check (target_card_count > 0),
    constraint uk_daily_study_task_scope_id unique (id, owner_user_id, workspace_id),
    constraint fk_daily_study_task_plan foreign key (plan_id, owner_user_id, workspace_id)
        references daily_study_plans (id, owner_user_id, workspace_id) on delete cascade
);

create table if not exists daily_study_task_cards (
    task_id bigint not null,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    flashcard_id bigint not null,
    primary key (task_id, flashcard_id),
    constraint fk_daily_study_task_cards_task foreign key (task_id, owner_user_id, workspace_id)
        references daily_study_tasks (id, owner_user_id, workspace_id) on delete cascade,
    constraint fk_daily_study_task_cards_flashcard foreign key (flashcard_id, owner_user_id, workspace_id)
        references study_flashcards (id, owner_user_id, workspace_id) on delete cascade
);

create index if not exists idx_daily_study_tasks_plan
    on daily_study_tasks (owner_user_id, workspace_id, plan_id, priority, id);

-- 学习任务从生成建议升级为可执行工作流，并保留不可变状态事件。
alter table daily_study_tasks add column if not exists status varchar(20) not null default 'PENDING';
alter table daily_study_tasks add column if not exists scheduled_date date;
update daily_study_tasks task
set scheduled_date = plan.plan_date
from daily_study_plans plan
where task.plan_id = plan.id and task.scheduled_date is null;
alter table daily_study_tasks alter column scheduled_date set not null;
alter table daily_study_tasks add column if not exists feedback_score integer;
alter table daily_study_tasks add column if not exists feedback_comment varchar(500);
alter table daily_study_tasks add column if not exists completed_at timestamptz;
alter table daily_study_tasks add column if not exists skipped_at timestamptz;
alter table daily_study_tasks add column if not exists version bigint not null default 0;
alter table daily_study_tasks add column if not exists updated_at timestamptz;
update daily_study_tasks set updated_at = created_at where updated_at is null;
alter table daily_study_tasks alter column updated_at set not null;

alter table daily_study_tasks drop constraint if exists ck_daily_study_task_type;
alter table daily_study_tasks add constraint ck_daily_study_task_type check (
    task_type in (
        'DUE_REVIEW', 'WEAK_POINT_REVIEW', 'TOPIC_REVIEW', 'DOCUMENT_REVIEW',
        'MASTERY_REINFORCEMENT', 'CONVERSATION_REVIEW'
    )
);

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'ck_daily_study_task_status') then
        alter table daily_study_tasks add constraint ck_daily_study_task_status
            check (status in ('PENDING', 'COMPLETED', 'SKIPPED'));
    end if;
    if not exists (select 1 from pg_constraint where conname = 'ck_daily_study_task_feedback') then
        alter table daily_study_tasks add constraint ck_daily_study_task_feedback
            check (feedback_score is null or feedback_score between 1 and 5);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'ck_daily_study_task_version') then
        alter table daily_study_tasks add constraint ck_daily_study_task_version check (version >= 0);
    end if;
end $$;

create index if not exists idx_daily_study_tasks_maintenance
    on daily_study_tasks (status, scheduled_date, id)
    where status = 'PENDING';

create table if not exists daily_study_task_events (
    id bigserial primary key,
    task_id bigint not null,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    action varchar(30) not null,
    previous_status varchar(20) not null,
    next_status varchar(20) not null,
    previous_scheduled_date date not null,
    next_scheduled_date date not null,
    feedback_score integer,
    comment varchar(500),
    created_at timestamptz not null,
    constraint ck_daily_study_task_event_action check (
        action in ('CREATED', 'COMPLETED', 'SKIPPED', 'RESCHEDULED', 'FEEDBACK_RECORDED', 'COMPENSATED')
    ),
    constraint ck_daily_study_task_event_status check (
        previous_status in ('PENDING', 'COMPLETED', 'SKIPPED')
        and next_status in ('PENDING', 'COMPLETED', 'SKIPPED')
    ),
    constraint ck_daily_study_task_event_feedback check (
        feedback_score is null or feedback_score between 1 and 5
    ),
    constraint fk_daily_study_task_event_scope foreign key (task_id, owner_user_id, workspace_id)
        references daily_study_tasks (id, owner_user_id, workspace_id) on delete cascade
);

create index if not exists idx_daily_study_task_events_task
    on daily_study_task_events (owner_user_id, workspace_id, task_id, created_at, id);

-- 主题画像是可重建快照；评分记录仍是唯一事实来源。
create table if not exists learning_topic_profiles (
    owner_user_id bigint not null,
    workspace_id bigint not null,
    topic varchar(100) not null,
    card_count integer not null,
    review_count integer not null,
    success_rate numeric(6, 2) not null,
    lapse_rate numeric(6, 2) not null,
    mastery_score numeric(6, 2) not null,
    level varchar(20) not null,
    last_reviewed_at timestamptz,
    updated_at timestamptz not null,
    primary key (owner_user_id, workspace_id, topic),
    constraint ck_learning_topic_profile_counts check (card_count >= 0 and review_count >= 0),
    constraint ck_learning_topic_profile_rates check (
        success_rate between 0 and 100 and lapse_rate between 0 and 100 and mastery_score between 0 and 100
    ),
    constraint ck_learning_topic_profile_level check (level in ('WEAK', 'AT_RISK', 'STABLE', 'STRONG'))
);

create index if not exists idx_learning_topic_profiles_weakness
    on learning_topic_profiles (owner_user_id, workspace_id, mastery_score, topic);

-- 长期会话摘要独立于 Redis 短期窗口，消息过期后仍保留压缩后的学习信号。
create table if not exists conversation_learning_summaries (
    id bigserial primary key,
    owner_user_id bigint not null,
    workspace_id bigint not null,
    conversation_id bigint not null,
    summary varchar(1000) not null,
    topics jsonb not null,
    weak_topics jsonb not null,
    message_count integer not null,
    version bigint not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_conversation_learning_summary unique (owner_user_id, workspace_id, conversation_id),
    constraint ck_conversation_learning_summary_counts check (message_count > 0 and version >= 0),
    constraint ck_conversation_learning_summary_topics check (
        jsonb_typeof(topics) = 'array' and jsonb_typeof(weak_topics) = 'array'
    )
);

create index if not exists idx_conversation_learning_summaries_scope
    on conversation_learning_summaries (owner_user_id, workspace_id, updated_at desc, id desc);
