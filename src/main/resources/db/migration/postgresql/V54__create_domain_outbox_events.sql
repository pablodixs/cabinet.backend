create table domain_outbox_events (
    id uuid primary key,
    event_type varchar(80) not null,
    aggregate_type varchar(50) not null,
    aggregate_id uuid not null,
    payload jsonb not null,
    status varchar(20) not null,
    attempt_count integer not null default 0,
    available_at timestamptz not null,
    created_at timestamptz not null default current_timestamp,
    processing_started_at timestamptz,
    processed_at timestamptz,
    last_error text,
    locked_by varchar(120),
    constraint ck_domain_outbox_status
        check (status in ('PENDING', 'PROCESSING', 'RETRY', 'COMPLETED', 'DEAD')),
    constraint ck_domain_outbox_attempt_count
        check (attempt_count >= 0)
);

create index idx_domain_outbox_active_claim
    on domain_outbox_events (available_at, created_at, id)
    where status in ('PENDING', 'RETRY');

create index idx_domain_outbox_aggregate
    on domain_outbox_events (aggregate_type, aggregate_id);

create index idx_domain_outbox_created_at
    on domain_outbox_events (created_at);

create index idx_domain_outbox_stale_processing
    on domain_outbox_events (processing_started_at)
    where status = 'PROCESSING';
