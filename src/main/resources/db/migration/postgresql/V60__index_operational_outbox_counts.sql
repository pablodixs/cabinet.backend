CREATE INDEX idx_domain_outbox_operational_status
    ON domain_outbox_events (status, created_at)
    WHERE status IN ('PENDING', 'RETRY', 'PROCESSING', 'DEAD');

CREATE INDEX idx_catalog_outbox_operational_status
    ON catalog_outbox (status, created_at)
    WHERE status IN ('PENDING', 'RETRY', 'PROCESSING', 'DEAD');
