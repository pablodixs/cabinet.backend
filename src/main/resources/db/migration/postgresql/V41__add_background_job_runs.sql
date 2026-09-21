create table background_job_runs (
    id uuid primary key,
    job_key varchar(50) not null,
    status varchar(32) not null,
    started_at timestamptz not null,
    finished_at timestamptz,
    processed_count integer not null default 0,
    updated_count integer not null default 0,
    success_count integer not null default 0,
    failure_count integer not null default 0,
    summary varchar(300),
    error_code varchar(50),
    created_at timestamptz not null default now()
);

create index idx_background_job_runs_latest_by_key
    on background_job_runs(job_key, started_at desc);
create index idx_background_job_runs_recent
    on background_job_runs(finished_at desc) where finished_at is not null;
create index idx_background_job_runs_retention
    on background_job_runs(created_at);
