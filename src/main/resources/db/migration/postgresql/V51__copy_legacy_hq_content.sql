-- Copy content authored through the old member-account routes into the HQ-owned
-- tables. The original rows remain in place for historical references.
insert into hq_lists (id, hq_profile_id, name, description, visibility, ordered, cover_url, created_at, updated_at)
select list.id, list.hq_profile_id, list.name, list.description, list.visibility,
       list.ordered, list.cover_url, coalesce(list.created_at, now()), coalesce(list.updated_at, now())
from media_lists list
where list.hq_profile_id is not null
on conflict (id) do nothing;

insert into hq_list_items (id, list_id, media_id, position, notes)
select item.id, item.list_id, item.media_id, item.position, item.notes
from media_list_items item
join hq_lists list on list.id = item.list_id
join media work on work.id = item.media_id
on conflict (id) do nothing;

insert into hq_reviews (id, hq_profile_id, media_id, content, contains_spoilers, rating, created_at, updated_at)
select review.id, hq.id, review.media_id, review.content, review.contains_spoilers,
       null, coalesce(review.created_at, now()), coalesce(review.updated_at, now())
from reviews review
join hq_profiles hq on hq.profile_id = review.author_profile_id
join media work on work.id = review.media_id
where review.content is not null
on conflict (hq_profile_id, media_id) do nothing;
