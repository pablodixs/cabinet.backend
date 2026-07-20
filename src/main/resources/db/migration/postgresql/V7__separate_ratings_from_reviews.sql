CREATE TABLE ratings (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    media_id uuid NOT NULL,
    rating numeric(2,1) NOT NULL,
    visibility varchar(20) NOT NULL,
    created_at timestamptz,
    updated_at timestamptz,
    CONSTRAINT uk_ratings_user_media UNIQUE (user_id, media_id),
    CONSTRAINT ck_ratings_value CHECK (
        rating >= 0.5 AND rating <= 5.0 AND mod(rating * 10, 5) = 0
    )
);

CREATE INDEX idx_ratings_media_visibility ON ratings(media_id, visibility, rating);

INSERT INTO ratings (id, user_id, media_id, rating, visibility, created_at, updated_at)
SELECT id, user_id, media_id, rating, visibility, created_at, updated_at
FROM reviews;

-- Some installations contain legacy reviews whose media rows were already
-- missing when the guarded, NOT VALID foreign key was introduced in V4.
-- Preserve those records during the split while still enforcing the
-- relationship for every new or updated rating.
ALTER TABLE ratings ADD CONSTRAINT fk_ratings_media
    FOREIGN KEY (media_id) REFERENCES media(id) NOT VALID;

ALTER TABLE reviews ADD COLUMN rating_id uuid;
UPDATE reviews SET rating_id = id;
ALTER TABLE reviews ALTER COLUMN rating_id SET NOT NULL;
ALTER TABLE reviews ADD CONSTRAINT fk_reviews_rating FOREIGN KEY (rating_id) REFERENCES ratings(id);
ALTER TABLE reviews ADD CONSTRAINT uk_reviews_rating UNIQUE (rating_id);

DROP INDEX IF EXISTS idx_reviews_media_created;
DROP INDEX IF EXISTS idx_reviews_media_popular;
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS uk_reviews_user_media;
ALTER TABLE reviews DROP COLUMN user_id;
ALTER TABLE reviews DROP COLUMN media_id;
ALTER TABLE reviews DROP COLUMN rating;
ALTER TABLE reviews DROP COLUMN visibility;

CREATE INDEX idx_reviews_created ON reviews(created_at);
