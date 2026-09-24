alter table reviews add column if not exists rich_content text;
alter table profiles add column if not exists rich_bio text;
alter table media_lists add column if not exists rich_description text;
alter table reviews add column if not exists author_profile_id uuid references profiles(id);
alter table reviews drop constraint if exists uk_reviews_user_media;
create unique index if not exists uk_reviews_user_media_personal
    on reviews(user_id, media_id) where author_profile_id is null;
create unique index if not exists uk_reviews_profile_media
    on reviews(author_profile_id, media_id) where author_profile_id is not null;
