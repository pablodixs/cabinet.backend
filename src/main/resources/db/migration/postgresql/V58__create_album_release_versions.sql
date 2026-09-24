CREATE TABLE album_release_versions (
    id UUID PRIMARY KEY,
    album_media_id UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    musicbrainz_release_id UUID NOT NULL,
    title VARCHAR(300),
    country_code VARCHAR(3),
    release_date DATE,
    format VARCHAR(200),
    status VARCHAR(40),
    barcode VARCHAR(80),
    catalog_number VARCHAR(200),
    label_name VARCHAR(300),
    cover_url TEXT,
    track_count INTEGER,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_synced_at TIMESTAMPTZ,
    CONSTRAINT uk_album_release_versions_musicbrainz_release UNIQUE (musicbrainz_release_id)
);

CREATE INDEX idx_album_release_versions_album ON album_release_versions(album_media_id);
CREATE INDEX idx_album_release_versions_barcode ON album_release_versions(barcode) WHERE barcode IS NOT NULL;
