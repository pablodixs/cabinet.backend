alter table series_seasons add column episodes_synced_at timestamptz;

create table user_episode_watches (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    series_episode_id uuid not null references series_episodes(id) on delete cascade,
    watched_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_user_episode_watches_user_episode unique (user_id, series_episode_id)
);

create index idx_user_episode_watches_user_watched
    on user_episode_watches(user_id, watched_at desc);
create index idx_user_episode_watches_episode
    on user_episode_watches(series_episode_id);

alter table notifications add column series_episode_id uuid
    references series_episodes(id) on delete cascade;

create unique index uk_notifications_episode_release
    on notifications(recipient_id, series_episode_id)
    where type = 'EPISODE_RELEASED';
