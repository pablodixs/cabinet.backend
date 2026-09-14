CREATE TABLE franchises (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(180) NOT NULL UNIQUE,
    name VARCHAR(300) NOT NULL,
    original_name VARCHAR(300),
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    poster_url TEXT,
    backdrop_url TEXT,
    parent_id UUID REFERENCES franchises(id) ON DELETE RESTRICT,
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_franchises_parent ON franchises(parent_id);
CREATE INDEX idx_franchises_status_name ON franchises(status, lower(name));

CREATE TABLE collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(180) NOT NULL UNIQUE,
    title VARCHAR(300) NOT NULL,
    original_title VARCHAR(300),
    description TEXT,
    type VARCHAR(30) NOT NULL,
    source_mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    poster_url TEXT,
    backdrop_url TEXT,
    start_date DATE,
    end_date DATE,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_collections_status_title ON collections(status, lower(title));

CREATE TABLE franchise_external_references (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    franchise_id UUID NOT NULL REFERENCES franchises(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    external_url TEXT,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_franchise_external_provider_id UNIQUE(provider, external_id),
    CONSTRAINT uk_franchise_external_owner_provider UNIQUE(franchise_id, provider)
);
CREATE INDEX idx_franchise_external_owner ON franchise_external_references(franchise_id);

CREATE TABLE collection_external_references (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    external_url TEXT,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_collection_external_provider_id UNIQUE(provider, external_id),
    CONSTRAINT uk_collection_external_owner_provider UNIQUE(collection_id, provider)
);
CREATE INDEX idx_collection_external_owner ON collection_external_references(collection_id);

CREATE TABLE collection_sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    section_key VARCHAR(100) NOT NULL,
    title VARCHAR(200),
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_collection_section_key UNIQUE(collection_id, section_key),
    CONSTRAINT uk_collection_section_position UNIQUE(collection_id, position)
);

CREATE TABLE collection_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    section_id UUID REFERENCES collection_sections(id) ON DELETE SET NULL,
    media_id UUID NOT NULL REFERENCES media(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL,
    relation_type VARCHAR(30) NOT NULL DEFAULT 'CORE',
    source_managed BOOLEAN NOT NULL DEFAULT FALSE,
    source_provider VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_collection_item_media UNIQUE(collection_id, media_id)
);
CREATE INDEX idx_collection_items_order ON collection_items(collection_id, section_id, position);
CREATE INDEX idx_collection_items_media ON collection_items(media_id);

CREATE TABLE franchise_collections (
    franchise_id UUID NOT NULL REFERENCES franchises(id) ON DELETE CASCADE,
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    relation_type VARCHAR(30) NOT NULL DEFAULT 'CORE',
    position INTEGER NOT NULL DEFAULT 0,
    source_managed BOOLEAN NOT NULL DEFAULT FALSE,
    source_provider VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(franchise_id, collection_id)
);
CREATE INDEX idx_franchise_collections_collection ON franchise_collections(collection_id, position);

CREATE TABLE franchise_media (
    franchise_id UUID NOT NULL REFERENCES franchises(id) ON DELETE CASCADE,
    media_id UUID NOT NULL REFERENCES media(id) ON DELETE RESTRICT,
    relation_type VARCHAR(30) NOT NULL DEFAULT 'RELATED',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    source_managed BOOLEAN NOT NULL DEFAULT FALSE,
    source_provider VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(franchise_id, media_id)
);
CREATE INDEX idx_franchise_media_media ON franchise_media(media_id);

CREATE TABLE collection_artists (
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    artist_id UUID NOT NULL REFERENCES people(id) ON DELETE RESTRICT,
    role VARCHAR(50) NOT NULL DEFAULT 'PRIMARY',
    PRIMARY KEY(collection_id, artist_id, role)
);
