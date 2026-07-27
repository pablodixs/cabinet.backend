CREATE INDEX idx_catalog_outbox_processing_lock
    ON catalog_outbox (locked_at)
    WHERE status = 'PROCESSING';
