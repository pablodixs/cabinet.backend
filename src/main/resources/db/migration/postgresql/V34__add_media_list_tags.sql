create table media_list_tags (
    list_id uuid not null references media_lists(id) on delete cascade,
    tag_id uuid not null references user_tags(id) on delete cascade,
    constraint uk_media_list_tag unique (list_id, tag_id)
);

create index idx_media_list_tags_tag on media_list_tags (tag_id);
