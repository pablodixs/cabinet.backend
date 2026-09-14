create table user_album_rotation (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    album_id uuid not null references media(id) on delete cascade,
    position integer not null,
    added_at timestamptz not null default current_timestamp,
    constraint uk_user_album_rotation_user_album unique (user_id, album_id),
    constraint uk_user_album_rotation_user_position unique (user_id, position)
);

create index idx_user_album_rotation_user_position
    on user_album_rotation (user_id, position);

create index idx_user_media_activity_user_media_type_date
    on user_media_activities (user_id, media_id, type, occurred_on);
