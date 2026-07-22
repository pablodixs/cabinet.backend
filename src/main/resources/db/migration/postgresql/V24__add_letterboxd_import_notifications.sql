alter table notifications
    add column letterboxd_import_job_id uuid
        references letterboxd_import_jobs(id) on delete cascade;

create unique index uk_notifications_letterboxd_import_type
    on notifications(recipient_id, letterboxd_import_job_id, type)
    where letterboxd_import_job_id is not null;
