ALTER TABLE catalog_outbox
    DROP CONSTRAINT IF EXISTS catalog_outbox_event_type_check;

ALTER TABLE catalog_outbox
    ADD CONSTRAINT catalog_outbox_event_type_check
        CHECK (event_type IN (
            'MEDIA_CORE_MATERIALIZED',
            'MEDIA_TRANSLATION_REQUESTED',
            'MEDIA_REFRESH_REQUESTED',
            'ALBUM_RELEASE_VERSIONS_SYNC_REQUESTED'
        ));
