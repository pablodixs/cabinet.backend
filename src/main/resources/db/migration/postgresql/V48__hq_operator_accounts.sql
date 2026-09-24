create table hq_operators (
    id uuid primary key default gen_random_uuid(),
    hq_profile_id uuid not null references hq_profiles(id) on delete cascade,
    email varchar(254) not null,
    display_name varchar(120) not null,
    password_hash varchar(255) not null,
    role varchar(20) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    unique (hq_profile_id, email)
);
create index idx_hq_operators_email on hq_operators (lower(email));
