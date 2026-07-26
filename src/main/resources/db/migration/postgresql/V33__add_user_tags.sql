create table user_tags (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    name varchar(100) not null,
    normalized_name varchar(100) not null,
    created_at timestamptz not null default current_timestamp,
    constraint uk_user_tag_normalized_name unique (user_id, normalized_name)
);

create index idx_user_tags_user_name on user_tags (user_id, name);

create table user_media_tags (
    id uuid primary key,
    tag_id uuid not null references user_tags(id) on delete cascade,
    media_id uuid not null references media(id) on delete cascade,
    created_at timestamptz not null default current_timestamp,
    constraint uk_user_media_tag unique (tag_id, media_id)
);

create index idx_user_media_tags_media on user_media_tags (media_id);
