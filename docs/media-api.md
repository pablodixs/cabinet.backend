# Media API

This API searches external catalogs, retrieves enriched media details, imports media into Cabinet, and lazily loads series episodes.

## Sources and enrichment

- Movies and series: TMDB.
- Albums and tracks: MusicBrainz and Cover Art Archive.
- Additional identifiers, genres, and logos: Wikidata.
- Movie and series ratings: OMDb (IMDb rating, Tomatometer, and Metascore when available).
- Watch providers: TMDB watch-provider data, attributed to JustWatch.
- Listen and purchase links: MusicBrainz URL relationships.

Wikidata enrichment is best-effort. A Wikidata timeout or missing item does not fail the request. Results are cached in memory by source, media type, external ID, and language. Primary-provider values take precedence.
For MusicBrainz details, a Wikidata URL relationship is used directly when present; SPARQL is the fallback.

Configure the integrations with:

```text
TMDB_ACCESS_TOKEN or TMDB_API_KEY
OMDB_API_KEY
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
`credits` contains structured work contributors. For imported media, `personId` identifies the persisted person;
for external previews it is `null`. The current roles are `AUTHOR`, `CREATOR`, `DIRECTOR`, `ACTOR`, `ARTIST`,
`COMPOSER`, `PRODUCER`, and `SCREENWRITER`. `creator` remains as a compatibility summary built from the primary
role for each media type.
Calling the import endpoint again for media imported before credit persistence was introduced performs a best-effort
credit backfill and does not duplicate existing credits.
`likeCount`, `averageRating`, `listCount`, and `completedCount` contain public Cabinet community aggregates.
Private reviews, non-public lists, and private library entries are excluded. Media that has not been imported yet
returns zero for the counters and `null` for `averageRating`.

### Where to watch or listen and external ratings

External availability and ratings are exposed for imported media:

```http
GET /v1/media/{mediaId}/external-info?country=BR
```

`country` is an ISO 3166-1 alpha-2 country code and defaults to `BR`. Movie and series availability is regional;
album and track links are currently global MusicBrainz relationships returned in the requested country section.
Books return `NOT_SUPPORTED` for both sections. Ratings are supported only for movies and series.

The endpoint uses a persisted stale-while-revalidate cache. A first request with no cached data schedules provider
lookups and returns `202 Accepted`, `Retry-After: 2`, and a `PENDING` section. The frontend should retry after the
indicated interval. Existing data is returned immediately as `STALE` while it is refreshed in the background, so
provider latency does not block normal reads. Typical refresh intervals are 24 hours for video availability, seven
days for music links, 12 hours for recently released movie/series ratings, and seven days for older ratings.

```json
{
  "mediaId": "6c64fb1f-8af4-4eca-92ec-d086af80b87a",
  "countryCode": "BR",
  "availability": {
    "state": "READY",
    "fetchedAt": "2026-07-16T18:00:00Z",
    "expiresAt": "2026-07-17T18:00:00Z",
    "attributions": ["JustWatch"],
    "offers": [
      {
        "dataSource": "JUSTWATCH",
        "providerId": "8",
        "providerName": "Netflix",
        "logoUrl": "https://image.tmdb.org/t/p/w92/example.jpg",
        "type": "SUBSCRIPTION",
        "url": "https://www.themoviedb.org/movie/603/watch?locale=BR",
        "sourceUrl": "https://www.themoviedb.org/movie/603/watch?locale=BR"
      }
    ]
  },
  "ratings": {
    "state": "READY",
    "fetchedAt": "2026-07-16T18:00:00Z",
    "expiresAt": "2026-07-23T18:00:00Z",
    "items": [
      {
        "provider": "OMDB",
        "source": "ROTTEN_TOMATOES",
        "metric": "TOMATOMETER",
        "value": 88.0,
        "scale": 100,
        "displayValue": "88%",
        "externalId": "tt0133093"
      }
    ]
  }
}
```

Section states are `READY`, `EMPTY`, `PENDING`, `STALE`, `ERROR`, `NOT_CONFIGURED`, and `NOT_SUPPORTED`.
`EMPTY` is a successful provider lookup with no matching data. `NOT_CONFIGURED` commonly means that
`OMDB_API_KEY` is absent. `provider: OMDB` identifies the API that supplied a rating; `source` identifies the
original metric owner. Cabinet does not query Rotten Tomatoes or Metacritic directly.

OMDb states that its content is licensed under CC BY-NC 4.0. Confirm that the product's use and attribution are
compatible with that license before enabling `OMDB_API_KEY` in a commercial environment.

### Filtered credits

Use the public credits endpoint when the frontend only needs contributors with a specific role:

```http
GET /v1/media/{mediaId}/credits?role=ACTOR&limit=20&page=0
```

`role` is required and accepts any `CreditRole`. `page` defaults to `0`; `limit` defaults to `20` and accepts values
from 1 to 40. Credits are filtered in the database by media and role, then ordered by `position`, so the endpoint does
not load the complete crew to return the requested page.

```json
{
  "items": [
    {
      "personId": "e14e30d7-4d53-40d7-92f6-12434e82245a",
      "name": "Brad Pitt",
      "role": "ACTOR",
      "characterName": "Tyler Durden",
      "position": 0,
      "imageUrl": "https://image.tmdb.org/t/p/w500/example.jpg",
      "source": "TMDB",
      "externalId": "287"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Artists

Imported credits create artist profiles. Both artist endpoints are public:

```http
GET /v1/artists/{artistId}
GET /v1/artists/{artistId}/works?page=0&size=24
```

The details response contains the artist name, biography and image when available, external identity, distinct work
count, and the credit roles found in Cabinet. The works endpoint is paginated, lists each imported media item once,
and includes every role and character associated with the artist in that work. `size` accepts values from 1 to 40.

Artist identities from TMDB and MusicBrainz are reconciled through their exact Wikidata QID. When both providers
point to the same QID, Cabinet keeps one artist profile with references to both providers and combines all imported
works. Names alone are never used to merge people. Identity lookup is best-effort, so an unavailable provider does
not prevent media import; importing an existing media item again also reconciles legacy credits incrementally.

### More by this artist or director

The media details screen can request a hybrid Cabinet and external-catalog highlight:

```http
GET /v1/media/{mediaId}/more-by?language=pt-BR&limit=12
```

Movies use the first `DIRECTOR` credit and return movies from TMDB. Albums and tracks use the first `ARTIST` credit
and return MusicBrainz release groups as albums. Books and series return `UNSUPPORTED`. The current media item is
excluded, imported and external copies are deduplicated, and imported cards preserve their Cabinet ID and community
rating. `limit` accepts values from 1 to 40.

```json
{
  "state": "READY",
  "role": "DIRECTOR",
  "person": {
    "id": "e14e30d7-4d53-40d7-92f6-12434e82245a",
    "name": "David Fincher",
    "imageUrl": "https://image.tmdb.org/t/p/w500/example.jpg"
  },
  "incomplete": false,
  "items": [
    {
      "id": null,
      "externalId": "807",
      "source": "TMDB",
      "type": "MOVIE",
      "title": "Se7en",
      "creator": "David Fincher",
      "description": null,
      "coverUrl": "https://image.tmdb.org/t/p/w500/example.jpg",
      "releaseDate": "1995-09-22",
      "imported": false,
      "averageRating": null,
      "ratingCount": 0
    }
  ]
}
```

`EMPTY` means the media type is supported but no other known work is available. `UNSUPPORTED` is returned for books
and series. `incomplete` is `true` when only local data or a stale external cache could be returned, or when the
MusicBrainz catalog exceeded the 100-item lookup cap. Provider failures never discard matching local works.

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
`SOUNDTRACK_OF`, `RE_RECORDING_OF`, or `RE_RECORDED_AS`. `providerSource` and `providerExternalId` are nullable when Wikidata has the relationship but
does not expose an identifier supported by Cabinet. `incomplete` is `true` when a stale cache entry or an empty
fallback had to be used because Wikidata was unavailable.

Re-recordings are curated album-to-album relationships. On the newer recording, use `RE_RECORDING_OF`; Cabinet
automatically stores the inverse `RE_RECORDED_AS` on the original album. Reissues, deluxe editions, and remasters are
not re-recordings. Authenticated users can suggest a missing relationship through the media-report workflow:

```http
POST /v1/media/reports
Content-Type: application/json

{
  "mediaId": "uuid-for-1989-taylors-version",
  "source": "MUSICBRAINZ",
  "externalId": "taylors-version-release-group-id",
  "mediaType": "ALBUM",
  "mediaTitle": "1989 (Taylor's Version)",
  "category": "MISSING_RELATION",
  "description": "Este álbum é uma regravação de 1989.",
  "suggestedTargetSource": "MUSICBRAINZ",
  "suggestedTargetExternalId": "original-1989-release-group-id",
  "suggestedTargetType": "ALBUM",
  "suggestedTargetTitle": "1989",
  "suggestedRelationType": "RE_RECORDING_OF"
}
```

After moderator approval, both directions appear in the related-works response. This follows the MusicBrainz
release-group definition of a studio re-recording by the same artist; moderator review handles identity confirmation
when provider credits are incomplete.

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
  "credits": [
    {
      "personId": null,
      "name": "David Fincher",
      "role": "DIRECTOR",
      "characterName": null,
      "position": 0,
      "imageUrl": "https://image.tmdb.org/t/p/w500/...",
      "source": "TMDB",
      "externalId": "7467"
    }
  ],
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

## Reviews

The public highlight endpoints return at most three public reviews as a JSON array:

```http
GET /v1/media/{mediaId}/reviews/popular
GET /v1/media/{mediaId}/reviews/recent
```

`popular` orders reviews by their number of likes, then by creation date and ID. `recent` orders reviews by creation
date and ID (newest first). Responses include `likeCount`, `liked`, and `recentLikers`; `liked` is `false` for
anonymous requests. `recentLikers` contains at most the five most recent accounts, each with `id`, `username`, and
`avatarUrl`. Both endpoints return `404 MEDIA_NOT_FOUND` when the media does not exist.

Review likes require authentication, mutations require CSRF, and private reviews are not accessible through these
endpoints:

```http
GET /v1/me/review-likes/{reviewId}
PUT /v1/me/review-likes/{reviewId}
DELETE /v1/me/review-likes/{reviewId}
```

All three responses contain `liked`, `likeCount`, and `recentLikers` using the same five-account format. `PUT` and
`DELETE` are idempotent.

## Lists

The popular-lists highlight endpoint returns at most three public lists containing the media as a JSON array:

```http
GET /v1/media/{mediaId}/lists/popular
```

Lists are ordered by like count (highest first), then by list update date and membership creation date.

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
