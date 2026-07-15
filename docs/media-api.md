# Media API

This API searches external catalogs, retrieves enriched media details, imports media into Cabinet, and lazily loads series episodes.

## Sources and enrichment

- Movies and series: TMDB.
- Albums and tracks: MusicBrainz and Cover Art Archive.
- Additional identifiers, genres, and logos: Wikidata.

Wikidata enrichment is best-effort. A Wikidata timeout or missing item does not fail the request. Results are cached in memory by source, media type, external ID, and language. Primary-provider values take precedence.
For MusicBrainz details, a Wikidata URL relationship is used directly when present; SPARQL is the fallback.

Configure the integrations with:

```text
TMDB_ACCESS_TOKEN or TMDB_API_KEY
MUSICBRAINZ_USER_AGENT
WIKIDATA_USER_AGENT
```

Use an identifiable user agent containing the application name and a contact URL or email.

## App search

The app-facing search endpoint supports external relevance and Cabinet community ratings with cursor pagination:

```http
GET /v1/media/search?query=matrix&type=MOVIE&sort=RELEVANCE&limit=20
GET /v1/media/search?query=matrix&sort=RATING&cursor={opaqueCursor}&limit=20
```

`type` is optional and accepts `MOVIE`, `SERIES`, or `ALBUM`. `sort` defaults to `RELEVANCE`; `RATING` returns only imported media with at least one public review. The query must contain at least three characters. Pass the opaque `nextCursor` unchanged to request the next page.

```json
{
  "items": [
    {
      "id": "6c64fb1f-8af4-4eca-92ec-d086af80b87a",
      "externalId": "603",
      "source": "TMDB",
      "type": "MOVIE",
      "title": "The Matrix",
      "creator": null,
      "description": "...",
      "coverUrl": "https://image.tmdb.org/t/p/w500/...",
      "releaseDate": "1999-03-30",
      "imported": true,
      "averageRating": 4.5,
      "ratingCount": 8
    }
  ],
  "nextCursor": "opaque-value"
}
```

## Search

```http
GET /v1/media/external/search?query=fight%20club&type=MOVIE&language=en-US&startIndex=0&maxResults=20
GET /v1/media/external/search?query=speak%20to%20me&type=TRACK&language=en-US&startIndex=0&maxResults=20
```

`type` is optional. Supported detail types are `MOVIE`, `SERIES`, `TRACK`, `ALBUM`, and `BOOK`. Language defaults to `pt-BR`.
Search responses are summaries. `durationSeconds` is populated for MusicBrainz track searches, and `wikidataId` is populated whenever the primary provider includes the relationship.

```json
{
  "externalId": "recording-uuid",
  "source": "MUSICBRAINZ",
  "type": "TRACK",
  "title": "Speak to Me",
  "creator": "Pink Floyd",
  "durationSeconds": 65,
  "wikidataId": null,
  "imported": false
}
```

## Media details

For media already imported into Cabinet, use the stored-media endpoint. It reads the persisted entity and does not depend on the primary external provider:

```http
GET /v1/media/{mediaId}
```

The response has the same shape as an external detail response. Frontend routes should prefer `/media/{mediaId}` whenever a search or community response includes the internal `id`.

```http
GET /v1/media/external/TMDB/MOVIE/550?language=en-US
GET /v1/media/external/TMDB/SERIES/1396?language=pt-BR
GET /v1/media/external/MUSICBRAINZ/TRACK/{recordingId}?language=en-US
GET /v1/media/external/MUSICBRAINZ/ALBUM/{releaseGroupId}?language=en-US
```

The response contains common fields plus a type-specific `details` object. Optional image fields can be `null`.
`likeCount`, `averageRating`, `listCount`, and `completedCount` contain public Cabinet community aggregates.
Private reviews, non-public lists, and private library entries are excluded. Media that has not been imported yet
returns zero for the counters and `null` for `averageRating`.

## Related works

Relationships are discovered from exact Wikidata statements and identifiers. Cabinet uses `P144` for adaptations
and `P406` for soundtrack releases. The lookup is best-effort and never imports related media automatically.

```http
GET /v1/media/external/TMDB/MOVIE/34584/relations?language=pt-BR&maxResults=12
```

```json
{
  "source": "WIKIDATA",
  "incomplete": false,
  "items": [
    {
      "relationType": "ADAPTATION_OF",
      "type": "BOOK",
      "title": "The Neverending Story",
      "releaseDate": "1979-09-01",
      "coverUrl": null,
      "wikidataId": "Q12345",
      "providerSource": "GOOGLE_BOOKS",
      "providerExternalId": "volume-id",
      "externalUrl": "https://www.wikidata.org/wiki/Q12345",
      "imported": false
    }
  ]
}
```

`relationType` is relative to the media in the request and can be `ADAPTATION_OF`, `ADAPTED_AS`, `SOUNDTRACK`,
or `SOUNDTRACK_OF`. `providerSource` and `providerExternalId` are nullable when Wikidata has the relationship but
does not expose an identifier supported by Cabinet. `incomplete` is `true` when a stale cache entry or an empty
fallback had to be used because Wikidata was unavailable.

### Movie

```json
{
  "id": null,
  "externalId": "550",
  "source": "TMDB",
  "type": "MOVIE",
  "title": "Fight Club",
  "originalTitle": "Fight Club",
  "creator": "David Fincher",
  "description": "...",
  "tagline": "Mischief. Mayhem. Soap.",
  "coverUrl": "https://image.tmdb.org/t/p/w500/...",
  "backdropUrl": "https://image.tmdb.org/t/p/original/...",
  "logoUrl": "https://image.tmdb.org/t/p/original/...",
  "externalUrl": "https://www.themoviedb.org/movie/550",
  "releaseDate": "1999-10-15",
  "originalLanguage": "en",
  "countryCode": "US",
  "wikidataId": "Q190050",
  "externalReferences": { "tmdb": "550", "wikidata": "Q190050" },
  "genres": [{ "id": "18", "name": "Drama", "source": "TMDB" }],
  "imported": false,
  "likeCount": 0,
  "averageRating": null,
  "listCount": 0,
  "completedCount": 0,
  "details": {
    "runtimeMinutes": 139,
    "budget": 63000000,
    "revenue": 100853753,
    "director": "David Fincher"
  }
}
```

### Track

```json
{
  "type": "TRACK",
  "wikidataId": "Q12345",
  "externalReferences": {
    "musicbrainz": "recording-uuid",
    "wikidata": "Q12345"
  },
  "details": {
    "durationSeconds": 65,
    "explicit": null
  }
}
```

### Album

Album details prefer an official MusicBrainz release with the most complete track list. `discNumber` distinguishes multi-disc releases. Track duration falls back to the recording duration when the release track itself has no length.

```json
{
  "type": "ALBUM",
  "genres": [{ "id": "rock", "name": "Rock", "source": "MUSICBRAINZ" }],
  "details": {
    "albumType": "Album",
    "numberOfTracks": 10,
    "tracks": [
      {
        "externalId": "recording-uuid",
        "title": "Speak to Me",
        "discNumber": 1,
        "trackNumber": 1,
        "durationSeconds": 65,
        "explicit": null
      }
    ]
  }
}
```

### Series

The detail response includes season summaries, not every episode. This keeps latency and payload size bounded.

```json
{
  "type": "SERIES",
  "details": {
    "status": "Ended",
    "numberOfSeasons": 5,
    "numberOfEpisodes": 62,
    "lastAirDate": "2013-09-29",
    "seasons": [
      {
        "externalId": "3572",
        "seasonNumber": 1,
        "name": "Season 1",
        "description": "...",
        "coverUrl": "https://image.tmdb.org/t/p/w500/...",
        "episodeCount": 7,
        "airDate": "2008-01-20"
      }
    ]
  }
}
```

## Season episodes

Load episodes only when a season is opened:

```http
GET /v1/media/external/TMDB/SERIES/1396/seasons/1?language=pt-BR
```

```json
{
  "seriesExternalId": "1396",
  "seasonNumber": 1,
  "episodes": [
    {
      "externalId": "62085",
      "episodeNumber": 1,
      "title": "Pilot",
      "description": "...",
      "stillUrl": "https://image.tmdb.org/t/p/w780/...",
      "airDate": "2008-01-20",
      "runtimeMinutes": 59
    }
  ]
}
```

Season `0` is valid and represents specials when supplied by TMDB.

## Import

```http
POST /v1/media/external/import
Content-Type: application/json

{
  "source": "TMDB",
  "externalId": "550",
  "mediaType": "MOVIE"
}
```

Imports persist cover, backdrop, logo, genres, Wikidata ID/reference, type-specific details, album tracks, and series season summaries. Import is idempotent for a source/external-ID pair.

## Likes

Likes are independent from library entries. These endpoints require authentication, and mutations require CSRF.

```http
GET /v1/me/likes/{mediaId}
PUT /v1/me/likes/{mediaId}
DELETE /v1/me/likes/{mediaId}
```

The `GET` and `PUT` responses contain `{ "liked": true|false }`. `PUT` is idempotent, and `DELETE` returns
`204 No Content` even when no like exists. The public `likeCount` in media details is calculated from these likes.

## Manual Wikidata link

Use this authenticated endpoint when automatic matching cannot find a Wikidata item or finds the wrong one. `mediaId` is Cabinet's UUID returned after import.

```http
PUT /v1/media/{mediaId}/wikidata
Content-Type: application/json

{
  "wikidataId": "Q190050",
  "language": "pt-BR"
}
```

`language` is optional and defaults to `pt-BR`. The QID must match `Q` followed by a positive integer. A QID cannot be linked to more than one Cabinet media.

```json
{
  "mediaId": "6c64fb1f-8af4-4eca-92ec-d086af80b87a",
  "wikidataId": "Q190050",
  "externalUrl": "https://www.wikidata.org/wiki/Q190050",
  "enriched": true
}
```

The association is saved even if Wikidata is temporarily unavailable. In that case, `enriched` is `false`; the ID and external reference are still persisted. When enrichment succeeds, missing logo and additional genres are applied without replacing the primary provider's existing data.

## Front-end types

Ready-to-use TypeScript definitions are available in [`media-api.types.ts`](./media-api.types.ts).
