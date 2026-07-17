alter table notifications
    add column activity_at timestamptz;

update notifications
set activity_at = updated_at
where activity_at is null;

alter table notifications
    alter column activity_at set not null;

drop index idx_notifications_recipient_updated;
drop index idx_notifications_recipient_unread;
drop index idx_notifications_retention;

create index idx_notifications_recipient_updated
    on notifications(recipient_id, activity_at desc, id desc);
create index idx_notifications_recipient_unread
    on notifications(recipient_id, activity_at desc)
    where read_at is null;
create index idx_notifications_retention on notifications(activity_at);
