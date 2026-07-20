alter table reviews
    add column if not exists user_id uuid references users(id),
    add column if not exists media_id uuid,
    add column if not exists visibility varchar(20),
    add column if not exists activity_id uuid references user_media_activities(id);

update reviews review
set user_id = rating.user_id,
    media_id = rating.media_id,
    visibility = rating.visibility
from ratings rating
where review.rating_id = rating.id
  and (review.user_id is null or review.media_id is null or review.visibility is null);

alter table reviews
    alter column user_id set not null,
    alter column media_id set not null,
    alter column visibility set not null,
    alter column rating_id drop not null;

-- V7 deliberately preserved legacy ratings whose media had already been
-- removed. Keep their reviews readable in the historical data while
-- enforcing the relationship for every new or updated row.
alter table reviews add constraint FKe8ceqthv8hlm8uo2aag7q11e0
    foreign key (media_id) references media(id) not valid;

alter table reviews add constraint uk_reviews_user_media unique (user_id, media_id);
alter table reviews add constraint uk_reviews_activity unique (activity_id);

alter table user_media_activities
    add column if not exists contains_spoilers boolean not null default false;

create index if not exists idx_reviews_media_visibility_created
    on reviews (media_id, visibility, created_at desc);
