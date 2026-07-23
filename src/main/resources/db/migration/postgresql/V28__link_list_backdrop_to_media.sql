alter table media_lists
    add column if not exists backdrop_media_id uuid references media(id) on delete set null,
    add column if not exists backdrop_key text;

create index if not exists idx_media_lists_backdrop_media
    on media_lists(backdrop_media_id);
