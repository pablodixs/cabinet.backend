create table if not exists user_interest_preferences (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    target_type varchar(20) not null,
    preference varchar(20) not null,
    genre_key varchar(100),
    person_id uuid references people(id) on delete cascade,
    media_id uuid references media(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_user_interest_target_type
        check (target_type in ('GENRE', 'PERSON', 'MEDIA')),
    constraint ck_user_interest_preference
        check (preference in ('POSITIVE', 'NEGATIVE')),
    constraint ck_user_interest_exact_target check (
        (target_type = 'GENRE' and genre_key is not null and person_id is null and media_id is null)
        or (target_type = 'PERSON' and genre_key is null and person_id is not null and media_id is null)
        or (target_type = 'MEDIA' and genre_key is null and person_id is null and media_id is not null)
    )
);

create index if not exists idx_user_interest_preferences_user
    on user_interest_preferences(user_id, target_type);

create unique index if not exists uk_user_interest_preferences_genre
    on user_interest_preferences(user_id, genre_key)
    where target_type = 'GENRE';

create unique index if not exists uk_user_interest_preferences_person
    on user_interest_preferences(user_id, person_id)
    where target_type = 'PERSON';

create unique index if not exists uk_user_interest_preferences_media
    on user_interest_preferences(user_id, media_id)
    where target_type = 'MEDIA';

create index if not exists idx_media_genres_normalized
    on media_genres(lower(btrim(genre)), media_id);
