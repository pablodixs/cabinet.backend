create index if not exists idx_user_media_public_planned_media
    on user_media (media_id)
    where status = 'PLANNED' and private_entry = false;
