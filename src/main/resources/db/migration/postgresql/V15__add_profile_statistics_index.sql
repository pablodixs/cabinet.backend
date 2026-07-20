create index idx_user_media_public_profile_stats
    on user_media(user_id, status, media_id)
    where private_entry = false;
