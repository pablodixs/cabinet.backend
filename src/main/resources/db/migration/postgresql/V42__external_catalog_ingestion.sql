CREATE TABLE external_catalog_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(30) NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(500),
    source_metadata JSONB,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    last_index_run_id UUID,
    state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    removed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_external_catalog_identity UNIQUE(provider, entity_type, external_id)
);
CREATE INDEX idx_external_catalog_state ON external_catalog_entities(provider, entity_type, state);
CREATE INDEX idx_external_catalog_external_id ON external_catalog_entities(provider, entity_type, external_id);
CREATE INDEX idx_external_catalog_last_seen ON external_catalog_entities(last_seen_at);
CREATE INDEX idx_external_catalog_index_run ON external_catalog_entities(last_index_run_id);

CREATE TABLE catalog_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(50) NOT NULL,
    provider VARCHAR(30),
    trigger VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by UUID,
    root_entity_type VARCHAR(30),
    root_external_id VARCHAR(255),
    root_collection_id UUID REFERENCES collections(id) ON DELETE SET NULL,
    total_items BIGINT NOT NULL DEFAULT 0,
    processed_items BIGINT NOT NULL DEFAULT 0,
    created_items BIGINT NOT NULL DEFAULT 0,
    updated_items BIGINT NOT NULL DEFAULT 0,
    unchanged_items BIGINT NOT NULL DEFAULT 0,
    failed_items BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_catalog_operations_created ON catalog_operations(created_at DESC);
CREATE INDEX idx_catalog_operations_status_created ON catalog_operations(status, created_at DESC);
CREATE INDEX idx_catalog_operations_root ON catalog_operations(root_entity_type, root_external_id);

CREATE TABLE catalog_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID REFERENCES catalog_operations(id) ON DELETE SET NULL,
    job_type VARCHAR(60) NOT NULL,
    provider VARCHAR(30),
    entity_type VARCHAR(30),
    external_id VARCHAR(255),
    collection_id UUID REFERENCES collections(id) ON DELETE SET NULL,
    media_id UUID REFERENCES media(id) ON DELETE SET NULL,
    priority INTEGER NOT NULL DEFAULT 10,
    trigger VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    deduplication_key VARCHAR(300),
    payload JSONB,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(160),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_catalog_jobs_claim ON catalog_jobs(status, available_at, priority DESC, created_at);
CREATE INDEX idx_catalog_jobs_operation ON catalog_jobs(operation_id, created_at);
CREATE INDEX idx_catalog_jobs_collection ON catalog_jobs(collection_id, status);
CREATE INDEX idx_catalog_jobs_external ON catalog_jobs(provider, entity_type, external_id);
CREATE UNIQUE INDEX uk_catalog_jobs_active_dedup
    ON catalog_jobs(deduplication_key)
    WHERE deduplication_key IS NOT NULL AND status IN ('PENDING', 'PROCESSING', 'RETRY');

CREATE TABLE catalog_job_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES catalog_jobs(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    worker_id VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT,
    error_class VARCHAR(300),
    error_message TEXT,
    metrics JSONB,
    CONSTRAINT uk_catalog_job_attempt_number UNIQUE(job_id, attempt_number)
);
CREATE INDEX idx_catalog_job_attempts_job ON catalog_job_attempts(job_id, attempt_number DESC);

CREATE TABLE catalog_operation_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID NOT NULL REFERENCES catalog_operations(id) ON DELETE CASCADE,
    job_id UUID REFERENCES catalog_jobs(id) ON DELETE SET NULL,
    event_type VARCHAR(60) NOT NULL,
    severity VARCHAR(12) NOT NULL DEFAULT 'INFO',
    entity_type VARCHAR(30),
    entity_id UUID,
    external_id VARCHAR(255),
    message VARCHAR(1000) NOT NULL,
    metadata JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_catalog_operation_events_timeline ON catalog_operation_events(operation_id, occurred_at);

CREATE TABLE collection_source_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    name VARCHAR(500),
    overview TEXT,
    poster_url TEXT,
    backdrop_url TEXT,
    remote_item_count INTEGER NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_collection_source_snapshot UNIQUE(collection_id, provider)
);

CREATE TABLE collection_source_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    external_media_type VARCHAR(30) NOT NULL,
    position INTEGER NOT NULL,
    title VARCHAR(500),
    original_title VARCHAR(500),
    release_date DATE,
    poster_url TEXT,
    backdrop_url TEXT,
    original_language VARCHAR(20),
    content_hash VARCHAR(64),
    resolved_media_id UUID REFERENCES media(id) ON DELETE SET NULL,
    resolution_status VARCHAR(20) NOT NULL DEFAULT 'UNRESOLVED',
    source_present BOOLEAN NOT NULL DEFAULT TRUE,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    removed_at TIMESTAMPTZ,
    last_materialization_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_collection_source_item UNIQUE(collection_id, provider, external_id)
);
CREATE INDEX idx_collection_source_items_position ON collection_source_items(collection_id, source_present, position);
CREATE INDEX idx_collection_source_items_identity ON collection_source_items(provider, external_id);
CREATE INDEX idx_collection_source_items_resolution ON collection_source_items(resolution_status);

ALTER TABLE collection_external_references
    ADD COLUMN last_successful_sync_at TIMESTAMPTZ,
    ADD COLUMN last_sync_attempt_at TIMESTAMPTZ,
    ADD COLUMN sync_status VARCHAR(20) NOT NULL DEFAULT 'NEVER_SYNCED',
    ADD COLUMN sync_priority VARCHAR(12) NOT NULL DEFAULT 'WARM',
    ADD COLUMN next_sync_at TIMESTAMPTZ,
    ADD COLUMN last_error TEXT,
    ADD COLUMN last_operation_id UUID REFERENCES catalog_operations(id) ON DELETE SET NULL,
    ADD COLUMN last_demand_at TIMESTAMPTZ;
CREATE INDEX idx_collection_reference_due_sync ON collection_external_references(next_sync_at)
    WHERE provider = 'TMDB';

CREATE TABLE collection_field_ownership (
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    field_name VARCHAR(40) NOT NULL,
    owner VARCHAR(20) NOT NULL DEFAULT 'SOURCE',
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(collection_id, field_name)
);
