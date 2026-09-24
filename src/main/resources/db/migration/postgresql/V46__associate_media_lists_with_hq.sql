alter table media_lists
    add column hq_profile_id uuid references hq_profiles(id) on delete cascade;

create index idx_media_lists_hq_profile_updated
    on media_lists(hq_profile_id, updated_at desc)
    where hq_profile_id is not null;
