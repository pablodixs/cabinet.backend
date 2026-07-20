ALTER TABLE album_tracks ADD COLUMN track_media_id uuid;
ALTER TABLE series_episodes ADD COLUMN episode_media_id uuid;

CREATE TEMP TABLE track_identity (
    identity_key text PRIMARY KEY,
    external_id varchar(200),
    media_id uuid NOT NULL
) ON COMMIT DROP;

INSERT INTO track_identity(identity_key, external_id, media_id)
SELECT
       CASE WHEN at.external_id IS NULL THEN 'local:' || at.id::text ELSE 'mb:' || at.external_id END,
       at.external_id,
       COALESCE(min(existing_media.id::text)::uuid, gen_random_uuid())
FROM album_tracks at
LEFT JOIN external_references er
       ON er.source = 'MUSICBRAINZ' AND er.external_id = at.external_id
LEFT JOIN media existing_media ON existing_media.id = er.media_id AND existing_media.type = 'TRACK'
GROUP BY CASE WHEN at.external_id IS NULL THEN 'local:' || at.id::text ELSE 'mb:' || at.external_id END,
         at.external_id;

INSERT INTO media(id, type, title, description, created_at, updated_at, version)
SELECT ti.media_id, 'TRACK', min(at.title), NULL, now(), now(), 0
FROM track_identity ti
JOIN album_tracks at ON ti.identity_key = CASE
    WHEN at.external_id IS NULL THEN 'local:' || at.id::text ELSE 'mb:' || at.external_id END
LEFT JOIN media m ON m.id = ti.media_id
WHERE m.id IS NULL
GROUP BY ti.media_id;

INSERT INTO track_details(media_id, duration_seconds, explicit, created_at, updated_at)
SELECT ti.media_id, max(at.duration_seconds), coalesce(bool_or(at.explicit), false), now(), now()
FROM track_identity ti
JOIN album_tracks at ON ti.identity_key = CASE
    WHEN at.external_id IS NULL THEN 'local:' || at.id::text ELSE 'mb:' || at.external_id END
LEFT JOIN track_details td ON td.media_id = ti.media_id
WHERE td.media_id IS NULL
GROUP BY ti.media_id;

INSERT INTO external_references(id, media_id, source, external_id, primary_reference, last_synced_at,
                                created_at, updated_at)
SELECT gen_random_uuid(), ti.media_id, 'MUSICBRAINZ', ti.external_id, true, now(), now(), now()
FROM track_identity ti
LEFT JOIN external_references er
       ON er.source = 'MUSICBRAINZ' AND er.external_id = ti.external_id
WHERE ti.external_id IS NOT NULL AND er.id IS NULL;

UPDATE album_tracks at
SET track_media_id = ti.media_id
FROM track_identity ti
WHERE ti.identity_key = CASE
    WHEN at.external_id IS NULL THEN 'local:' || at.id::text ELSE 'mb:' || at.external_id END;

CREATE TEMP TABLE episode_identity (
    episode_id uuid PRIMARY KEY,
    media_id uuid NOT NULL
) ON COMMIT DROP;

INSERT INTO episode_identity(episode_id, media_id)
SELECT id, gen_random_uuid() FROM series_episodes WHERE episode_media_id IS NULL;

INSERT INTO media(id, type, title, description, cover_url, release_date, created_at, updated_at, version)
SELECT ei.media_id, 'EPISODE', se.title, se.description, se.still_url, se.air_date, now(), now(), 0
FROM episode_identity ei JOIN series_episodes se ON se.id = ei.episode_id;

UPDATE series_episodes se
SET episode_media_id = ei.media_id
FROM episode_identity ei
WHERE se.id = ei.episode_id;

ALTER TABLE album_tracks ALTER COLUMN track_media_id SET NOT NULL;
ALTER TABLE album_tracks ADD CONSTRAINT fk_album_tracks_track_media
    FOREIGN KEY (track_media_id) REFERENCES media(id);
CREATE INDEX idx_album_tracks_track_media ON album_tracks(track_media_id);

ALTER TABLE series_episodes ALTER COLUMN episode_media_id SET NOT NULL;
ALTER TABLE series_episodes ADD CONSTRAINT fk_series_episodes_media
    FOREIGN KEY (episode_media_id) REFERENCES media(id);
ALTER TABLE series_episodes ADD CONSTRAINT uk_series_episodes_media UNIQUE (episode_media_id);
