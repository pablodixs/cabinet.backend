alter table reviews add column if not exists published_at timestamptz;
update reviews set published_at = created_at where published_at is null;

alter table ratings add column if not exists rated_at timestamptz;
update ratings set rated_at = coalesce(updated_at, created_at) where rated_at is null;

alter table media_likes add column if not exists liked_at timestamptz;
update media_likes set liked_at = created_at where liked_at is null;

alter table letterboxd_import_items
    add column if not exists status_conflict boolean not null default false,
    add column if not exists rating_conflict boolean not null default false,
    add column if not exists review_conflict boolean not null default false;
