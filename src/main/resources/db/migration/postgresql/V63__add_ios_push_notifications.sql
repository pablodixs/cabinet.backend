create table push_installations (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    fcm_token text not null unique,
    platform varchar(20) not null default 'IOS',
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    constraint ck_push_installations_platform check (platform = 'IOS')
);
create index idx_push_installations_user_active on push_installations(user_id, active);

create table push_preferences (
    user_id uuid not null references users(id) on delete cascade,
    notification_type varchar(40) not null,
    enabled boolean not null default true,
    updated_at timestamptz not null default now(),
    primary key (user_id, notification_type)
);

create table notification_deliveries (
    id uuid primary key default gen_random_uuid(),
    notification_id uuid not null references notifications(id) on delete cascade,
    installation_id uuid not null references push_installations(id) on delete cascade,
    status varchar(20) not null default 'PENDING',
    attempt_count integer not null default 0,
    available_at timestamptz not null default now(),
    processing_started_at timestamptz,
    delivered_at timestamptz,
    last_error text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_notification_delivery_target unique(notification_id, installation_id),
    constraint ck_notification_delivery_status check (status in ('PENDING', 'PROCESSING', 'RETRY', 'DELIVERED', 'DEAD'))
);
create index idx_notification_deliveries_claim on notification_deliveries(status, available_at, created_at);

alter table notifications add column media_id uuid references media(id) on delete cascade;
create unique index uk_notifications_follow_actor
    on notifications(recipient_id, actor_id) where type = 'FOLLOWED';
create unique index uk_notifications_media_release
    on notifications(recipient_id, media_id) where type = 'MEDIA_RELEASED';

create table local_release_reminders (
    user_id uuid not null references users(id) on delete cascade,
    media_id uuid not null references media(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, media_id)
);
