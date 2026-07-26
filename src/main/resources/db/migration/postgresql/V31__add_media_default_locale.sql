ALTER TABLE media
    ADD COLUMN default_locale VARCHAR(10);

UPDATE media
SET default_locale = 'pt-BR'
WHERE default_locale IS NULL;

ALTER TABLE media
    ALTER COLUMN default_locale SET NOT NULL;

CREATE INDEX idx_media_translation_media_locale
    ON media_translations (media_id, locale);
