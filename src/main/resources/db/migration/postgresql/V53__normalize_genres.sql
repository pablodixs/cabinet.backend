create table genre (
    id uuid primary key,
    canonical_key varchar(150) not null unique,
    provisional boolean not null default false
);
create table genre_translation (
    genre_id uuid not null references genre(id) on delete cascade,
    locale varchar(10) not null,
    name varchar(100) not null,
    normalized_name varchar(100) not null,
    primary key (genre_id, locale)
);
create index idx_genre_translation_name on genre_translation using gin (normalized_name gin_trgm_ops);
create index idx_genre_translation_lookup on genre_translation (locale, normalized_name, genre_id);
create table genre_external_ref (
    genre_id uuid not null references genre(id) on delete cascade,
    source varchar(30) not null,
    external_id varchar(150) not null,
    primary key (source, external_id)
);
create index idx_genre_external_ref_genre on genre_external_ref (genre_id);
create table media_genre (
    media_id uuid not null references media(id) on delete cascade,
    genre_id uuid not null references genre(id) on delete cascade,
    primary key (media_id, genre_id)
);
create index idx_media_genre_genre_media on media_genre (genre_id, media_id);

-- Only exact, curated equivalences are collapsed across languages.
with labels(locale, name, canonical_key) as (values
    ('en-US', 'Drama', 'drama'), ('pt-BR', 'Drama', 'drama'),
    ('en-US', 'Science Fiction', 'science-fiction'), ('pt-BR', 'Ficção científica', 'science-fiction'),
    ('en-US', 'Comedy', 'comedy'), ('pt-BR', 'Comédia', 'comedy'),
    ('en-US', 'Action', 'action'), ('pt-BR', 'Ação', 'action'),
    ('en-US', 'Horror', 'horror'), ('pt-BR', 'Terror', 'horror'),
    ('en-US', 'Romance', 'romance'), ('pt-BR', 'Romance', 'romance'),
    ('en-US', 'Documentary', 'documentary'), ('pt-BR', 'Documentário', 'documentary'),
    ('en-US', 'Fantasy', 'fantasy'), ('pt-BR', 'Fantasia', 'fantasy'),
    ('en-US', 'Animation', 'animation'), ('pt-BR', 'Animação', 'animation'),
    ('en-US', 'Thriller', 'thriller'), ('pt-BR', 'Suspense', 'thriller')
), input as (
    select distinct m.default_locale as locale, btrim(mg.genre) as name,
           lower(regexp_replace(btrim(mg.genre), '\s+', ' ', 'g')) as normalized_name
    from media_genres mg join media m on m.id = mg.media_id
    where btrim(mg.genre) <> ''
), resolved as (
    select i.*, coalesce(l.canonical_key, 'legacy:' || i.locale || ':' || i.normalized_name) as canonical_key,
           l.canonical_key is null as provisional
    from input i left join labels l on l.locale = i.locale and lower(l.name) = i.normalized_name
)
insert into genre (id, canonical_key, provisional)
select (substring(md5(canonical_key), 1, 8) || '-' || substring(md5(canonical_key), 9, 4) || '-' ||
        substring(md5(canonical_key), 13, 4) || '-' || substring(md5(canonical_key), 17, 4) || '-' ||
        substring(md5(canonical_key), 21, 12))::uuid, canonical_key, bool_and(provisional)
from resolved group by canonical_key;

with labels(locale, name, canonical_key) as (values
    ('en-US', 'Drama', 'drama'), ('pt-BR', 'Drama', 'drama'),
    ('en-US', 'Science Fiction', 'science-fiction'), ('pt-BR', 'Ficção científica', 'science-fiction'),
    ('en-US', 'Comedy', 'comedy'), ('pt-BR', 'Comédia', 'comedy'),
    ('en-US', 'Action', 'action'), ('pt-BR', 'Ação', 'action'),
    ('en-US', 'Horror', 'horror'), ('pt-BR', 'Terror', 'horror'),
    ('en-US', 'Romance', 'romance'), ('pt-BR', 'Romance', 'romance'),
    ('en-US', 'Documentary', 'documentary'), ('pt-BR', 'Documentário', 'documentary'),
    ('en-US', 'Fantasy', 'fantasy'), ('pt-BR', 'Fantasia', 'fantasy'),
    ('en-US', 'Animation', 'animation'), ('pt-BR', 'Animação', 'animation'),
    ('en-US', 'Thriller', 'thriller'), ('pt-BR', 'Suspense', 'thriller')
), source_labels as (
    select distinct m.default_locale as locale, btrim(mg.genre) as name,
           lower(regexp_replace(btrim(mg.genre), '\s+', ' ', 'g')) as normalized_name
    from media_genres mg join media m on m.id = mg.media_id where btrim(mg.genre) <> ''
), ranked as (
    select coalesce(l.canonical_key, 'legacy:' || s.locale || ':' || s.normalized_name) as canonical_key,
           s.locale, s.name, s.normalized_name,
           row_number() over (partition by coalesce(l.canonical_key, 'legacy:' || s.locale || ':' || s.normalized_name), s.locale order by s.name) as rn
    from source_labels s left join labels l on l.locale = s.locale and lower(l.name) = s.normalized_name
)
insert into genre_translation (genre_id, locale, name, normalized_name)
select g.id, r.locale, r.name, r.normalized_name from ranked r join genre g on g.canonical_key = r.canonical_key where r.rn = 1;

with labels(locale, name, canonical_key) as (values
    ('en-US', 'Drama', 'drama'), ('pt-BR', 'Drama', 'drama'),
    ('en-US', 'Science Fiction', 'science-fiction'), ('pt-BR', 'Ficção científica', 'science-fiction'),
    ('en-US', 'Comedy', 'comedy'), ('pt-BR', 'Comédia', 'comedy'),
    ('en-US', 'Action', 'action'), ('pt-BR', 'Ação', 'action'),
    ('en-US', 'Horror', 'horror'), ('pt-BR', 'Terror', 'horror'),
    ('en-US', 'Romance', 'romance'), ('pt-BR', 'Romance', 'romance'),
    ('en-US', 'Documentary', 'documentary'), ('pt-BR', 'Documentário', 'documentary'),
    ('en-US', 'Fantasy', 'fantasy'), ('pt-BR', 'Fantasia', 'fantasy'),
    ('en-US', 'Animation', 'animation'), ('pt-BR', 'Animação', 'animation'),
    ('en-US', 'Thriller', 'thriller'), ('pt-BR', 'Suspense', 'thriller')
)
insert into media_genre (media_id, genre_id)
select distinct mg.media_id, g.id from media_genres mg join media m on m.id = mg.media_id
left join labels l on l.locale = m.default_locale and lower(btrim(mg.genre)) = lower(l.name)
join genre g on g.canonical_key = coalesce(l.canonical_key,
    'legacy:' || m.default_locale || ':' || lower(regexp_replace(btrim(mg.genre), '\s+', ' ', 'g')))
where btrim(mg.genre) <> '' on conflict do nothing;

-- Curated aliases are the editable cross-provider and cross-language equivalence map.
create table genre_alias (
    locale varchar(10) not null,
    normalized_name varchar(100) not null,
    genre_id uuid not null references genre(id) on delete cascade,
    primary key (locale, normalized_name)
);
create index idx_genre_alias_genre on genre_alias (genre_id);
create temporary table genre_curated_seed (
    locale varchar(10), name varchar(100), canonical_key varchar(150)
) on commit drop;
insert into genre_curated_seed values
    ('en-US', 'Drama', 'drama'), ('pt-BR', 'Drama', 'drama'),
    ('en-US', 'Science Fiction', 'science-fiction'), ('pt-BR', 'Ficção científica', 'science-fiction'),
    ('en-US', 'Comedy', 'comedy'), ('pt-BR', 'Comédia', 'comedy'),
    ('en-US', 'Action', 'action'), ('pt-BR', 'Ação', 'action'),
    ('en-US', 'Horror', 'horror'), ('pt-BR', 'Terror', 'horror'),
    ('en-US', 'Romance', 'romance'), ('pt-BR', 'Romance', 'romance'),
    ('en-US', 'Documentary', 'documentary'), ('pt-BR', 'Documentário', 'documentary'),
    ('en-US', 'Fantasy', 'fantasy'), ('pt-BR', 'Fantasia', 'fantasy'),
    ('en-US', 'Animation', 'animation'), ('pt-BR', 'Animação', 'animation'),
    ('en-US', 'Thriller', 'thriller'), ('pt-BR', 'Suspense', 'thriller');
insert into genre(id, canonical_key, provisional)
select distinct (substring(md5(canonical_key), 1, 8) || '-' ||
        substring(md5(canonical_key), 9, 4) || '-' ||
        substring(md5(canonical_key), 13, 4) || '-' ||
        substring(md5(canonical_key), 17, 4) || '-' ||
        substring(md5(canonical_key), 21, 12))::uuid, canonical_key, false
from genre_curated_seed on conflict (canonical_key) do nothing;
insert into genre_translation(genre_id, locale, name, normalized_name)
select g.id, s.locale, s.name, lower(s.name)
from genre_curated_seed s join genre g on g.canonical_key = s.canonical_key
on conflict (genre_id, locale) do nothing;
insert into genre_alias(locale, normalized_name, genre_id)
select s.locale, lower(s.name), g.id
from genre_curated_seed s join genre g on g.canonical_key = s.canonical_key;

alter table user_interest_preferences add column genre_id uuid references genre(id) on delete cascade;
update user_interest_preferences p set genre_id = g.id
from genre_translation t join genre g on g.id = t.genre_id
where p.target_type = 'GENRE' and p.genre_id is null
  and t.normalized_name = p.genre_key
  and (select count(distinct t2.genre_id) from genre_translation t2 where t2.normalized_name = p.genre_key) = 1;
-- Ambiguous or unmatched legacy preferences keep their polarity under a provisional ID.
insert into genre(id, canonical_key, provisional)
select distinct (substring(md5('preference:' || genre_key), 1, 8) || '-' ||
        substring(md5('preference:' || genre_key), 9, 4) || '-' ||
        substring(md5('preference:' || genre_key), 13, 4) || '-' ||
        substring(md5('preference:' || genre_key), 17, 4) || '-' ||
        substring(md5('preference:' || genre_key), 21, 12))::uuid,
        'preference:' || genre_key, true
from user_interest_preferences where target_type = 'GENRE' and genre_id is null
on conflict (canonical_key) do nothing;
insert into genre_translation(genre_id, locale, name, normalized_name)
select distinct on (g.id) g.id, 'pt-BR', coalesce(p.genre_label, p.genre_key), p.genre_key
from user_interest_preferences p join genre g on g.canonical_key = 'preference:' || p.genre_key
where p.target_type = 'GENRE' and p.genre_id is null
order by g.id, p.updated_at desc
on conflict (genre_id, locale) do nothing;
update user_interest_preferences p set genre_id = g.id
from genre g where p.target_type = 'GENRE' and p.genre_id is null
and g.canonical_key = 'preference:' || p.genre_key;

-- Keep the most recent preference if formerly distinct text keys resolve to one concept.
delete from user_interest_preferences p using (
    select id, row_number() over (partition by user_id, genre_id order by updated_at desc, id) as rank
    from user_interest_preferences where target_type = 'GENRE' and genre_id is not null
) duplicates where p.id = duplicates.id and duplicates.rank > 1;
update user_interest_preferences set genre_key = genre_id::text
where target_type = 'GENRE' and genre_id is not null;
create unique index uk_user_interest_preferences_genre_id on user_interest_preferences(user_id, genre_id)
    where target_type = 'GENRE' and genre_id is not null;
