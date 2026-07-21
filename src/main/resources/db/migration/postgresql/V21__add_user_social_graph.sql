alter table users
    add column followers_count bigint not null default 0,
    add column following_count bigint not null default 0;

alter table users
    add constraint ck_users_followers_count_nonnegative check (followers_count >= 0),
    add constraint ck_users_following_count_nonnegative check (following_count >= 0);

create table user_follows (
    follower_id uuid not null references users(id) on delete cascade,
    followed_id uuid not null references users(id) on delete cascade,
    status varchar(20) not null,
    requested_at timestamptz not null,
    accepted_at timestamptz,
    primary key (follower_id, followed_id),
    constraint ck_user_follows_not_self check (follower_id <> followed_id),
    constraint ck_user_follows_status check (status in ('PENDING', 'ACCEPTED')),
    constraint ck_user_follows_accepted_at check (
        (status = 'PENDING' and accepted_at is null)
        or (status = 'ACCEPTED' and accepted_at is not null)
    )
);

create index idx_user_follows_followers_accepted
    on user_follows(followed_id, accepted_at desc, follower_id desc)
    where status = 'ACCEPTED';

create index idx_user_follows_following_accepted
    on user_follows(follower_id, accepted_at desc, followed_id desc)
    where status = 'ACCEPTED';

create index idx_user_follows_incoming_pending
    on user_follows(followed_id, requested_at desc, follower_id desc)
    where status = 'PENDING';

create index idx_user_follows_outgoing_pending
    on user_follows(follower_id, requested_at desc, followed_id desc)
    where status = 'PENDING';

create table user_blocks (
    blocker_id uuid not null references users(id) on delete cascade,
    blocked_id uuid not null references users(id) on delete cascade,
    created_at timestamptz not null,
    primary key (blocker_id, blocked_id),
    constraint ck_user_blocks_not_self check (blocker_id <> blocked_id)
);

create index idx_user_blocks_reverse
    on user_blocks(blocked_id, blocker_id);

create index idx_user_blocks_list
    on user_blocks(blocker_id, created_at desc, blocked_id desc);

create index idx_notifications_recipient_actor
    on notifications(recipient_id, actor_id)
    where actor_id is not null;

create extension if not exists pg_trgm;

create index idx_users_username_trgm
    on users using gin (lower(username) gin_trgm_ops);

create index idx_users_display_name_trgm
    on users using gin (lower(display_name) gin_trgm_ops);
