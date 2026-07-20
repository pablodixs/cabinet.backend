alter table users
    add column if not exists account_tier varchar(20) not null default 'FREE';

create table if not exists user_account_tier_changes (
    id uuid primary key,
    target_user_id uuid not null references users(id),
    changed_by_user_id uuid not null references users(id),
    previous_tier varchar(20) not null,
    new_tier varchar(20) not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_user_account_tier_change_target
    on user_account_tier_changes(target_user_id, created_at);

create index if not exists idx_user_account_tier_change_actor
    on user_account_tier_changes(changed_by_user_id, created_at);

create table if not exists user_media_artwork_preferences (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    media_id uuid not null references media(id) on delete cascade,
    cover_provider varchar(40),
    cover_key text,
    cover_url text,
    backdrop_provider varchar(40),
    backdrop_key text,
    backdrop_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_user_media_artwork_preference unique (user_id, media_id)
);

create index if not exists idx_user_media_artwork_preference_user_media
    on user_media_artwork_preferences(user_id, media_id);
