create table user_feed_activities (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    media_id uuid not null references media(id) on delete cascade,
    action_type varchar(30) not null,
    occurred_at timestamptz not null,
    visibility varchar(20) not null default 'PUBLIC',
    rating numeric(2,1),
    review text,
    contains_spoilers boolean not null default false,
    created_at timestamptz not null default now(),
    constraint uk_user_feed_activity_action unique (user_id, media_id, action_type),
    constraint ck_user_feed_activity_action check (action_type in ('ADDED_TO_WATCHLIST', 'LIKED', 'RATED', 'REVIEWED'))
);

create index idx_user_feed_activity_date on user_feed_activities (occurred_at desc, id desc);
create index idx_user_feed_activity_user_date on user_feed_activities (user_id, occurred_at desc, id desc);

insert into user_feed_activities (user_id, media_id, action_type, occurred_at, visibility)
select distinct on (activity.user_id, activity.media_id)
       activity.user_id, activity.media_id, 'ADDED_TO_WATCHLIST',
       coalesce(activity.created_at, activity.occurred_on::timestamptz), activity.visibility
from user_media_activities activity
join media m on m.id = activity.media_id
where activity.type = 'ADDED_TO_LIBRARY'
order by activity.user_id, activity.media_id, activity.occurred_on asc, activity.created_at asc;

insert into user_feed_activities (user_id, media_id, action_type, occurred_at, visibility)
select likes.user_id, likes.media_id, 'LIKED', coalesce(likes.liked_at, likes.created_at), 'PUBLIC'
from media_likes likes
join media m on m.id = likes.media_id;

insert into user_feed_activities (user_id, media_id, action_type, occurred_at, visibility, rating)
select ratings.user_id, ratings.media_id, 'RATED', coalesce(ratings.rated_at, ratings.updated_at, ratings.created_at), ratings.visibility, ratings.rating
from ratings
join media m on m.id = ratings.media_id;

insert into user_feed_activities (user_id, media_id, action_type, occurred_at, visibility, rating, review, contains_spoilers)
select review.user_id, review.media_id, 'REVIEWED', coalesce(review.published_at, review.updated_at, review.created_at), review.visibility,
       rating.rating, review.content, review.contains_spoilers
from reviews review
join media m on m.id = review.media_id
left join ratings rating on rating.id = review.rating_id;
