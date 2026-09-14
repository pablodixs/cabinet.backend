ALTER TABLE external_references
    ADD COLUMN provider_availability VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN provider_unavailable_at TIMESTAMPTZ;

CREATE TABLE catalog_sync_checkpoint (
    id UUID PRIMARY KEY,
    source VARCHAR(30) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    last_completed_end_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    CONSTRAINT uk_catalog_sync_checkpoint_source_type UNIQUE (source, media_type)
);
