-- Header and catalog searches use lower(title) LIKE '%term%'. The existing
-- (locale, lower(title)) btree index cannot support this substring predicate.
CREATE INDEX IF NOT EXISTS idx_media_translations_title_trgm
    ON media_translations USING gin (lower(title) gin_trgm_ops);
