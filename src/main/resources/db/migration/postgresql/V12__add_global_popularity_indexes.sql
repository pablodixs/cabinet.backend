CREATE INDEX idx_media_lists_visibility_updated
    ON media_lists (visibility, updated_at DESC, id);
