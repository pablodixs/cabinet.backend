create table user_profile_favorites (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    media_id uuid not null references media(id) on delete cascade,
    position integer not null check (position between 0 and 3),
    created_at timestamptz not null default current_timestamp,
    constraint uk_profile_favorite_user_media unique (user_id, media_id),
    constraint uk_profile_favorite_user_position unique (user_id, position)
);

create index idx_profile_favorites_user_position
    on user_profile_favorites (user_id, position);
