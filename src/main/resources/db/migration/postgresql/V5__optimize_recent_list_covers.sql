create index if not exists idx_media_list_items_recent_cover
    on media_list_items (list_id, created_at desc, position desc, id desc)
    include (media_id);
