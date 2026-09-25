-- V42 introduced this table. Recreate it for databases whose schema drifted
-- or where the original migration was applied without the table.
CREATE TABLE IF NOT EXISTS collection_field_ownership (
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    field_name VARCHAR(40) NOT NULL,
    owner VARCHAR(20) NOT NULL DEFAULT 'SOURCE',
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(collection_id, field_name)
);
