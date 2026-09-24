create table hq_reviews (
    id uuid primary key default gen_random_uuid(),
    hq_profile_id uuid not null references hq_profiles(id) on delete cascade,
    media_id uuid not null references media(id),
    content text not null,
    contains_spoilers boolean not null default false,
    rating numeric(2,1),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (hq_profile_id, media_id),
    constraint chk_hq_review_rating check (rating is null or (rating between 0.5 and 5.0 and rating * 2 = floor(rating * 2)))
);
create index idx_hq_reviews_profile on hq_reviews (hq_profile_id, created_at desc);
