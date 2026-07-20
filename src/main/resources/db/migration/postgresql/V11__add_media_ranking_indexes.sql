CREATE INDEX idx_ratings_public_activity
    ON ratings (visibility, (COALESCE(updated_at, created_at)) DESC, media_id);

CREATE INDEX idx_media_likes_activity
    ON media_likes (created_at DESC, media_id);

CREATE INDEX idx_user_media_public_activity
    ON user_media ((COALESCE(last_interaction_at, updated_at, created_at)) DESC, media_id)
    WHERE private_entry = false;
