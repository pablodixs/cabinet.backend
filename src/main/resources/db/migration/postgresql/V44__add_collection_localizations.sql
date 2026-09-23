ALTER TABLE collections
    ADD COLUMN default_locale VARCHAR(10) NOT NULL DEFAULT 'pt-BR';

CREATE TABLE collection_translations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    locale VARCHAR(10) NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    poster_url TEXT,
    backdrop_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_collection_translation_locale UNIQUE (collection_id, locale)
);

CREATE INDEX idx_collection_translation_locale_title
    ON collection_translations (locale, lower(title));

INSERT INTO collection_translations (
    collection_id, locale, title, description, poster_url, backdrop_url
)
SELECT id, default_locale, title, description, poster_url, backdrop_url
FROM collections
ON CONFLICT (collection_id, locale) DO NOTHING;
