create index if not exists idx_album_tracks_album_cursor
    on album_tracks (album_media_id, disc_number, track_number, id);

create index if not exists idx_album_release_versions_album_cursor
    on album_release_versions (album_media_id, id);
