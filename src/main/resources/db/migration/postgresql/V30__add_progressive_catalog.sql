ALTER TABLE media
    ADD COLUMN catalog_status VARCHAR(20) NOT NULL DEFAULT 'READY',
    ADD COLUMN core_synced_at TIMESTAMPTZ,
    ADD COLUMN enrichment_synced_at TIMESTAMPTZ,
    ADD COLUMN sync_version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_sync_error TEXT;

CREATE TABLE media_translations (
    id UUID PRIMARY KEY,
    media_id UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    locale VARCHAR(10) NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    tagline VARCHAR(500),
    source VARCHAR(30) NOT NULL,
    source_language VARCHAR(10),
    translation_status VARCHAR(20) NOT NULL,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_media_translation_locale UNIQUE (media_id, locale)
);

CREATE INDEX idx_media_translation_locale_title
    ON media_translations (locale, lower(title));

CREATE TABLE catalog_outbox (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(120),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_catalog_outbox_claim
    ON catalog_outbox (status, available_at, created_at);

CREATE UNIQUE INDEX uk_catalog_outbox_active_core
    ON catalog_outbox (aggregate_id, event_type)
    WHERE status IN ('PENDING', 'PROCESSING', 'RETRY');

INSERT INTO media_translations (
    id, media_id, locale, title, description, tagline, source, source_language,
    translation_status, last_synced_at, created_at, updated_at
)
SELECT
    gen_random_uuid(), m.id, 'pt-BR', m.title, m.description, m.tagline, 'MANUAL',
    m.original_language,
    CASE WHEN m.description IS NULL THEN 'PARTIAL' ELSE 'AVAILABLE' END,
    COALESCE(m.updated_at, m.created_at, now()),
    now(), now()
FROM media m
ON CONFLICT (media_id, locale) DO NOTHING;
