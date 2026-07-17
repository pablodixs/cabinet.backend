create table comments (
    id uuid primary key,
    author_id uuid not null references users(id) on delete cascade,
    media_list_id uuid references media_lists(id) on delete cascade,
    review_id uuid references reviews(id) on delete cascade,
    parent_id uuid references comments(id) on delete cascade,
    content varchar(2000),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz,
    constraint ck_comments_single_target check (
        (media_list_id is not null and review_id is null)
        or (media_list_id is null and review_id is not null)
    ),
    constraint ck_comments_content check (
        deleted_at is not null or (content is not null and length(trim(content)) > 0)
    )
);

create index idx_comments_list_root_created
    on comments(media_list_id, created_at, id)
    where parent_id is null;
create index idx_comments_review_root_created
    on comments(review_id, created_at, id)
    where parent_id is null;
create index idx_comments_parent_created
    on comments(parent_id, created_at, id)
    where parent_id is not null;

create table notifications (
    id uuid primary key,
    recipient_id uuid not null references users(id) on delete cascade,
    actor_id uuid references users(id) on delete set null,
    type varchar(40) not null,
    actor_count bigint not null default 1,
    media_list_id uuid references media_lists(id) on delete cascade,
    review_id uuid references reviews(id) on delete cascade,
    comment_id uuid references comments(id) on delete cascade,
    report_id uuid references media_reports(id) on delete cascade,
    read_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_notifications_recipient_updated
    on notifications(recipient_id, updated_at desc, id desc);
create index idx_notifications_recipient_unread
    on notifications(recipient_id, updated_at desc)
    where read_at is null;
create index idx_notifications_retention on notifications(updated_at);
create unique index uk_notifications_list_like_group
    on notifications(recipient_id, media_list_id)
    where type = 'LIST_LIKED';
create unique index uk_notifications_review_like_group
    on notifications(recipient_id, review_id)
    where type = 'REVIEW_LIKED';
create unique index uk_notifications_comment_recipient
    on notifications(recipient_id, comment_id)
    where comment_id is not null;
create unique index uk_notifications_report_recipient
    on notifications(recipient_id, report_id)
    where report_id is not null;
