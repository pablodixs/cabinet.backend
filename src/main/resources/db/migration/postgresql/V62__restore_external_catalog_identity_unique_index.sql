-- Both TMDB catalog upserts target this identity. V42 creates the equivalent
-- unique constraint on fresh databases; this repairs databases where that
-- constraint was lost or omitted.
CREATE UNIQUE INDEX IF NOT EXISTS uk_external_catalog_identity
    ON external_catalog_entities (provider, entity_type, external_id);
