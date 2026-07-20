ALTER TABLE media
    DROP CONSTRAINT IF EXISTS media_type_check;

ALTER TABLE media
    ADD CONSTRAINT media_type_check
    CHECK (type IN ('BOOK', 'MOVIE', 'SERIES', 'TRACK', 'ALBUM', 'EPISODE'));
