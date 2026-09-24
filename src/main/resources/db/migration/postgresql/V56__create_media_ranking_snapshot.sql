create table media_ranking_snapshot (
    media_id uuid not null references media(id) on delete cascade,
    activity_day date not null,
    rating_activity bigint not null default 0,
    like_activity bigint not null default 0,
    completion_activity bigint not null default 0,
    log_activity bigint not null default 0,
    list_addition_activity bigint not null default 0,
    review_activity bigint not null default 0,
    updated_at timestamptz not null default current_timestamp,
    constraint pk_media_ranking_snapshot primary key (media_id, activity_day),
    constraint ck_media_ranking_snapshot_counts check (
        rating_activity >= 0
        and like_activity >= 0
        and completion_activity >= 0
        and log_activity >= 0
        and list_addition_activity >= 0
        and review_activity >= 0
    )
);

create index idx_media_ranking_snapshot_activity_day
    on media_ranking_snapshot (activity_day desc, media_id)
    include (
        rating_activity,
        like_activity,
        completion_activity,
        log_activity,
        list_addition_activity,
        review_activity
    );

create table media_ranking_snapshot_state (
    media_id uuid primary key references media(id) on delete cascade,
    rebuilt_at timestamptz,
    dirty boolean not null default true,
    dirty_version bigint not null default 0,
    dirty_marked_at timestamptz not null default current_timestamp
);

create index idx_media_ranking_snapshot_state_dirty
    on media_ranking_snapshot_state (dirty_marked_at, media_id)
    where dirty = true;

create index idx_media_ranking_snapshot_state_rebuilt
    on media_ranking_snapshot_state (rebuilt_at, media_id)
    where dirty = false and rebuilt_at is not null;

create table media_ranking_snapshot_backfill_state (
    singleton_id smallint primary key default 1 check (singleton_id = 1),
    last_media_id uuid,
    completed_at timestamptz
);

insert into media_ranking_snapshot_backfill_state (singleton_id)
values (1);
