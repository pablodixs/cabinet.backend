alter table user_interest_preferences
    add column if not exists genre_label varchar(100);

update user_interest_preferences
set genre_label = genre_key
where target_type = 'GENRE'
  and genre_label is null;

alter table user_interest_preferences
    drop constraint if exists ck_user_interest_exact_target;

alter table user_interest_preferences
    add constraint ck_user_interest_exact_target check (
        (target_type = 'GENRE' and genre_key is not null and genre_label is not null
            and person_id is null and media_id is null)
        or (target_type = 'PERSON' and genre_key is null and genre_label is null
            and person_id is not null and media_id is null)
        or (target_type = 'MEDIA' and genre_key is null and genre_label is null
            and person_id is null and media_id is not null)
    );
