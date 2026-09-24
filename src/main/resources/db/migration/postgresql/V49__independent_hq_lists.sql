create table hq_lists (
    id uuid primary key default gen_random_uuid(),
    hq_profile_id uuid not null references hq_profiles(id) on delete cascade,
    name varchar(120) not null,
    description text,
    visibility varchar(20) not null default 'PUBLIC',
    ordered boolean not null default true,
    cover_url varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_hq_lists_profile on hq_lists (hq_profile_id, updated_at desc);

create table hq_list_editors (
    list_id uuid not null references hq_lists(id) on delete cascade,
    operator_id uuid not null references hq_operators(id) on delete cascade,
    primary key (list_id, operator_id)
);

create table hq_list_items (
    id uuid primary key default gen_random_uuid(),
    list_id uuid not null references hq_lists(id) on delete cascade,
    media_id uuid not null references media(id),
    position integer not null,
    notes text,
    unique (list_id, media_id)
);
create index idx_hq_list_items_position on hq_list_items (list_id, position);
