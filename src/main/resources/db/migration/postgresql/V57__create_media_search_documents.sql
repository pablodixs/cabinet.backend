CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE media_search_documents (
    media_id UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    locale VARCHAR(10) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    title TEXT NOT NULL,
    title_normalized TEXT NOT NULL,
    original_title TEXT,
    original_title_normalized TEXT NOT NULL,
    creator_names TEXT NOT NULL DEFAULT '',
    alternative_titles TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    normalized_text TEXT NOT NULL,
    search_vector TSVECTOR NOT NULL,
    external_source VARCHAR(30),
    external_id TEXT,
    description TEXT,
    release_date DATE,
    popularity_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (media_id, locale)
);

CREATE INDEX idx_media_search_documents_vector
    ON media_search_documents USING GIN (search_vector);

CREATE INDEX idx_media_search_documents_title_trgm
    ON media_search_documents USING GIN (title_normalized gin_trgm_ops);

CREATE INDEX idx_media_search_documents_original_title_trgm
    ON media_search_documents USING GIN (original_title_normalized gin_trgm_ops);

CREATE INDEX idx_media_search_documents_locale_type
    ON media_search_documents (locale, media_type, media_id);

CREATE INDEX idx_media_search_documents_updated_at
    ON media_search_documents (updated_at, media_id);
