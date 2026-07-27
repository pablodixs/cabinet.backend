ALTER TABLE media_translations
    ADD COLUMN cover_url VARCHAR(2000);

UPDATE media_translations translation
SET cover_url = media.cover_url
FROM media
WHERE translation.media_id = media.id
  AND translation.locale = media.default_locale;
