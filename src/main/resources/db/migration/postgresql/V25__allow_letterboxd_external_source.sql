do $$
declare
    target record;
    constraint_name text;
begin
    for target in
        select * from (values
            ('external_references', 'source'),
            ('media_availability_offers', 'data_source'),
            ('media_credits', 'source'),
            ('media_external_ratings', 'provider'),
            ('media_external_ratings', 'source'),
            ('media_reports', 'source'),
            ('media_reports', 'suggested_target_source'),
            ('people', 'external_source'),
            ('person_external_references', 'source'),
            ('media_lists', 'origin_source'),
            ('user_media_activities', 'source')
        ) as external_source_columns(table_name, column_name)
    loop
        constraint_name := target.table_name || '_' || target.column_name || '_check';

        execute format(
            'alter table %I drop constraint if exists %I',
            target.table_name,
            constraint_name
        );
        execute format(
            'alter table %I add constraint %I check (%I in (
                ''TMDB'', ''IMDB'', ''GOOGLE_BOOKS'', ''OPEN_LIBRARY'', ''MUSICBRAINZ'',
                ''SPOTIFY'', ''APPLE_MUSIC'', ''DEEZER'', ''LAST_FM'', ''WIKIDATA'',
                ''JUSTWATCH'', ''OMDB'', ''ROTTEN_TOMATOES'', ''METACRITIC'',
                ''LETTERBOXD'', ''MANUAL''
            ))',
            target.table_name,
            constraint_name,
            target.column_name
        );
    end loop;
end
$$;
