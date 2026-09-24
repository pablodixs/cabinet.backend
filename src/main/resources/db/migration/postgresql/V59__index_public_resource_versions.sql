CREATE INDEX idx_domain_outbox_media_metadata_version
    ON domain_outbox_events (aggregate_id, created_at DESC, id DESC)
    WHERE aggregate_type = 'MEDIA'
      AND event_type IN ('MEDIA_IMPORTED', 'MEDIA_METADATA_CHANGED');

CREATE INDEX idx_album_release_versions_public_version
    ON album_release_versions (album_media_id, last_synced_at DESC)
    WHERE last_synced_at IS NOT NULL;
