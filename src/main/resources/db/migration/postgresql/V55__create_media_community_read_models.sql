create table media_community_stats (
    media_id uuid primary key references media(id) on delete cascade,
    rating_count bigint not null default 0,
    rating_sum numeric not null default 0,
    average_rating numeric,
    like_count bigint not null default 0,
    list_count bigint not null default 0,
    completed_count bigint not null default 0,
    updated_at timestamptz not null
);

create index idx_media_community_stats_updated
    on media_community_stats (updated_at, media_id);

create table media_rating_distribution (
    media_id uuid not null references media(id) on delete cascade,
    rating numeric(2,1) not null,
    rating_count bigint not null,
    constraint pk_media_rating_distribution primary key (media_id, rating),
    constraint ck_media_rating_distribution_bucket check (
        rating in (0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0)
    ),
    constraint ck_media_rating_distribution_count check (rating_count > 0)
);

create table media_community_stats_dirty (
    media_id uuid primary key references media(id) on delete cascade,
    dirty_version bigint not null default 1,
    marked_at timestamptz not null default current_timestamp
);

create index idx_media_community_stats_dirty_marked
    on media_community_stats_dirty (marked_at, media_id);
