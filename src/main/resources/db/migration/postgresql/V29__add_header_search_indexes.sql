create index if not exists idx_media_title_trgm
    on media using gin (lower(title) gin_trgm_ops);

create index if not exists idx_media_original_title_trgm
    on media using gin (lower(original_title) gin_trgm_ops);

create index if not exists idx_people_name_trgm
    on people using gin (lower(name) gin_trgm_ops);
