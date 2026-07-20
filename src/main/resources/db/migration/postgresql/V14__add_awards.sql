CREATE TABLE award_entries (
    id uuid PRIMARY KEY,
    media_id uuid REFERENCES media(id),
    person_id uuid REFERENCES people(id),
    result varchar(20) NOT NULL,
    program_qid varchar(30),
    program_name varchar(300),
    category_qid varchar(30),
    category_name varchar(300) NOT NULL,
    ceremony_qid varchar(30),
    ceremony_name varchar(300),
    event_date date,
    event_year integer,
    date_precision varchar(10),
    work_qid varchar(30),
    work_name varchar(300),
    work_media_id uuid REFERENCES media(id),
    origin varchar(20) NOT NULL,
    source_statement_id varchar(500),
    source_url text,
    curated boolean NOT NULL DEFAULT false,
    hidden boolean NOT NULL DEFAULT false,
    curated_by_user_id uuid REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_award_entry_subject CHECK (
        (media_id IS NOT NULL AND person_id IS NULL)
        OR (media_id IS NULL AND person_id IS NOT NULL)
    ),
    CONSTRAINT uk_award_entry_source_statement UNIQUE (source_statement_id)
);

CREATE INDEX idx_award_entries_media_page
    ON award_entries(media_id, hidden, event_year DESC, event_date DESC, category_name, id);
CREATE INDEX idx_award_entries_person_page
    ON award_entries(person_id, hidden, event_year DESC, event_date DESC, category_name, id);
CREATE INDEX idx_award_entries_media_result ON award_entries(media_id, hidden, result);
CREATE INDEX idx_award_entries_person_result ON award_entries(person_id, hidden, result);
CREATE INDEX idx_award_entries_source_statement ON award_entries(source_statement_id);

CREATE TABLE award_sync_states (
    id uuid PRIMARY KEY,
    media_id uuid REFERENCES media(id),
    person_id uuid REFERENCES people(id),
    status varchar(20) NOT NULL,
    fetched_at timestamptz,
    expires_at timestamptz,
    error_code varchar(80),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_award_sync_subject CHECK (
        (media_id IS NOT NULL AND person_id IS NULL)
        OR (media_id IS NULL AND person_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_award_sync_media ON award_sync_states(media_id) WHERE media_id IS NOT NULL;
CREATE UNIQUE INDEX uk_award_sync_person ON award_sync_states(person_id) WHERE person_id IS NOT NULL;

CREATE TABLE award_entry_revisions (
    id uuid PRIMARY KEY,
    award_entry_id uuid NOT NULL REFERENCES award_entries(id),
    edited_by_user_id uuid NOT NULL REFERENCES users(id),
    action varchar(30) NOT NULL,
    before_state text,
    after_state text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_award_revision_entry ON award_entry_revisions(award_entry_id, created_at DESC);
CREATE INDEX idx_award_revision_editor ON award_entry_revisions(edited_by_user_id, created_at DESC);
