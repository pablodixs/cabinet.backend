alter table media_lists
    add column if not exists origin_source varchar(30),
    add column if not exists origin_key varchar(700);

create unique index if not exists uk_media_lists_owner_origin
    on media_lists (owner_id, origin_source, origin_key)
    where origin_source is not null and origin_key is not null;

create table if not exists user_media_activities (
    id uuid primary key,
    user_id uuid not null references users(id),
    media_id uuid not null references media(id),
    type varchar(30) not null,
    occurred_on date not null,
    logged_on date,
    rating numeric(2,1),
    review_content text,
    visibility varchar(20) not null default 'PUBLIC',
    source varchar(30),
    source_key varchar(700),
    created_at timestamptz not null default now(),
    constraint uk_user_media_activity_source_key unique (user_id, source, source_key)
);

create index if not exists idx_user_media_activity_user_date
    on user_media_activities (user_id, occurred_on desc);
create index if not exists idx_user_media_activity_media
    on user_media_activities (media_id);

create table if not exists user_media_activity_tags (
    activity_id uuid not null references user_media_activities(id) on delete cascade,
    tag varchar(100) not null,
    primary key (activity_id, tag)
);

insert into user_media_activities (
    id, user_id, media_id, type, occurred_on, visibility, source_key, created_at
)
select gen_random_uuid(),
       um.user_id,
       um.media_id,
       case um.status
           when 'PLANNED' then 'ADDED_TO_LIBRARY'
           when 'IN_PROGRESS' then 'STARTED'
           when 'COMPLETED' then 'COMPLETED'
           when 'PAUSED' then 'PAUSED'
           when 'DROPPED' then 'DROPPED'
       end,
       (coalesce(um.last_interaction_at, um.updated_at, um.created_at, now()) at time zone 'America/Sao_Paulo')::date,
       case when um.private_entry then 'PRIVATE' else 'PUBLIC' end,
       'migration:user-media:' || um.id,
       coalesce(um.last_interaction_at, um.updated_at, um.created_at, now())
from user_media um
where not exists (
    select 1 from user_media_activities activity
    where activity.user_id = um.user_id
      and activity.source_key = 'migration:user-media:' || um.id
);

create table if not exists letterboxd_import_jobs (
    id uuid primary key,
    user_id uuid not null references users(id),
    state varchar(40) not null,
    total_items integer not null default 0,
    matched_items integer not null default 0,
    review_items integer not null default 0,
    imported_items integer not null default 0,
    preserved_items integer not null default 0,
    skipped_items integer not null default 0,
    failed_items integer not null default 0,
    error_message text,
    expires_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_letterboxd_import_job_user_state
    on letterboxd_import_jobs (user_id, state);

create table if not exists letterboxd_import_items (
    id uuid primary key,
    job_id uuid not null references letterboxd_import_jobs(id) on delete cascade,
    source_key varchar(700) not null,
    letterboxd_uri varchar(700),
    title varchar(300) not null,
    release_year integer,
    state varchar(30) not null,
    selected_media_id uuid references media(id),
    selected_tmdb_id varchar(100),
    payload text not null,
    match_candidates text,
    override_status boolean not null default false,
    override_rating boolean not null default false,
    override_review boolean not null default false,
    error_message text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_letterboxd_import_item_job_source unique (job_id, source_key)
);

create index if not exists idx_letterboxd_import_item_job_state
    on letterboxd_import_items (job_id, state);
