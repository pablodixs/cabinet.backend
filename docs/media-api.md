# Media API

This API searches external catalogs, retrieves enriched media details, imports media into Cabinet, and lazily loads series episodes.

## Localized catalog representations

Stored media has one internal UUID in every language. Localized endpoints currently support `pt-BR` and `en-US`
and choose the requested locale in this order:

1. the `locale` query parameter;
2. the `Accept-Language` header;
3. `pt-BR`.

For example:

```http
GET /v1/media/{mediaId}?locale=en-US
GET /v1/media/{mediaId}
Accept-Language: en-US,pt-BR;q=0.8
```

The same locale rules apply to localized search, rankings, recommendations, external media, people works, episode,
and “more by” endpoints. Use the endpoint's explicit `locale` or existing `language` query parameter when present;
otherwise the backend reads `Accept-Language`, then falls back to `pt-BR`. A stored title can be found in either
supported language, while the returned title uses the requested representation. Unsupported explicit locales
return `400 Bad Request` with code `UNSUPPORTED_LOCALE`.

Localized media details include `requestedLocale`, `resolvedLocale`, and `translationFallback`. The fallback flag
is true when any returned localized field needed another locale. `Content-Language` identifies the locale used
for the displayed title. Responses selected from `Accept-Language` also send `Vary: Accept-Language`.

Translation selection prefers an `AVAILABLE` or `PARTIAL` record in the requested locale, then the other supported
locale, then another usable translation, and finally the canonical fields stored on the media. `MISSING` and
`STALE` records do not replace a usable alternative.

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

### Header search

For typeahead in the application header, use the local, fixed-size endpoint:

```http
GET /v1/search/header?query=matrix&scope=ALL&type=MOVIE
```

`scope` accepts `ALL`, `MEDIA`, or `ARTIST`; `type` optionally limits media and artists with credits in that media
type. The endpoint returns at most five items total, ordered by exact match, prefix, and partial match. It does not
call external catalog providers.

```json
{
  "items": [
    {
      "id": "6c64fb1f-8af4-4eca-92ec-d086af80b87a",
      "entityType": "MEDIA",
      "title": "The Matrix",
      "creator": "Lana Wachowski, Lilly Wachowski",
      "coverUrl": "https://image.tmdb.org/t/p/w500/...",
      "year": 1999
    },
    {
      "id": "e094835b-58fc-468e-b7e8-26aa63a72af7",
      "entityType": "ARTIST",
      "title": "Matrix",
      "creator": null,
      "coverUrl": "https://image.example/matrix.jpg",
      "year": null
    }
  ]
}
```

## Rankings and trending media

Both discovery endpoints are public and return only Cabinet community data. With no `type` filter they include
top-level works (`MOVIE`, `SERIES`, `ALBUM`, and `BOOK`); pass a type explicitly to request a specific category,
including `TRACK` or `EPISODE`.

The top-rated ranking uses public ratings, ordered by average rating, number of ratings, and title. It uses regular
page pagination:

```http
GET /v1/media/rankings/top-rated?type=MOVIE&page=0&limit=20
```

The homepage-oriented trending endpoint combines public rating changes (weight 3), media likes (2), public
completions (2), diary logs (1.5), public-list additions (1), and public reviews (2). Each signal decays
exponentially: its weight is multiplied by `0.5^(age in days / half-life)`. The default half-life is three days,
so an activity three days old contributes half as much as a new activity. The weights and half-life are
configurable with the `MEDIA_TRENDING_*` settings. `days` defaults to 7 and accepts 1 through 30; `limit` defaults
to 12. The window uses UTC calendar days.

```http
GET /v1/media/rankings/trending?type=SERIES&days=7&limit=12
```

The anticipated ranking returns future movies ordered by how many public library entries are marked `PLANNED`.
`limit` defaults to 6 and accepts 1 through 40.

```http
GET /v1/media/rankings/anticipated?limit=6
```

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
  "periodDays": 7
}
```

## Search

```http
GET /v1/media/external/search?query=fight%20club&type=MOVIE&language=en-US&startIndex=0&maxResults=20
GET /v1/media/external/search?query=speak%20to%20me&type=TRACK&language=en-US&startIndex=0&maxResults=20
```

`type` is optional. Supported detail types are `MOVIE`, `SERIES`, `TRACK`, `ALBUM`, and `BOOK`. The optional `language` parameter overrides `Accept-Language`; when neither selects a supported locale, the response defaults to `pt-BR`.
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
GET /v1/media/{mediaId}?detailLevel=SUMMARY
```

The response has the same shape as an external detail response. `detailLevel=FULL` remains the default for older clients. `SUMMARY` preserves the type-specific details shape but leaves album `tracks` and `releaseVersions` empty, allowing clients to load large collections only when needed. Frontend routes should prefer `/media/{mediaId}` whenever a search or community response includes the internal `id`.

Album pages can request their persisted canonical tracklist by cursor:

```http
GET /v1/media/{albumId}/tracks/cursor?limit=40
GET /v1/media/{albumId}/tracks/cursor?cursor={opaqueCursor}&limit=40
GET /v1/media/{albumId}/release-versions?limit=20
```

Both cursor endpoints return `{ "items": [...], "nextCursor": "...", "hasMore": true }`, with `limit` from 1 to 40 (default 20). Track pages include community rating fields and may include viewer-specific rating/like fields, so they use `Cache-Control: private, no-store`. Release-version pages contain public MusicBrainz edition metadata. The existing unpaginated `/tracks` route remains available for older clients.

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
`recentLikers` and `recentCompleters` contain up to three accounts in reverse chronological order, including each
account's `id`, `username`, and `avatarUrl`. Private reviews, non-public lists, and private library entries are
excluded. Media that has not been imported yet returns zero for the counters, empty recent-account arrays, and
`null` for `averageRating`.

For imported albums and series, `GET /v1/media/{mediaId}/community` also returns `childRatings`. The nested
`itemType` is `TRACK` for albums and `EPISODE` for series; `averageRating`, `ratingCount`, and
`ratingDistribution` aggregate every public rating attached to the eligible child media. Series exclude episodes
whose air date is in the future. Other media types return `childRatings: null`.

`GET /v1/media/{mediaId}/community` includes ten public `ratingDistribution` buckets in ascending order from `0.5`
through `5.0`, including zero-count buckets. `GET /v1/media/{mediaId}/me` is the authenticated viewer-state response;
its `logCount` and `lastLoggedOn` include that viewer's `LOGGED`, `RELOGGED`, `WATCHED`, and `REWATCHED` diary
entries for the media, regardless of entry visibility. The legacy `listenCount` and `lastListenedOn` remain limited
to `LOGGED` and `RELOGGED` entries for albums and tracks.

Works with a `releaseDate` after the current date can be added as `PLANNED`, but cannot use any consumption status
(`IN_PROGRESS`, `PAUSED`, `DROPPED`, or `COMPLETED`) and cannot receive a rating or review. Those attempts return
`400 Bad Request` with code `MEDIA_NOT_RELEASED`. A work is available on its release date; a missing release date
does not block it.

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

## People and works

Imported credits create generic person profiles. The people endpoints are public:

```http
GET /v1/people/{personId}
GET /v1/people/{personId}/works?page=0&size=24&language=pt-BR&type=MOVIE
```

The details response contains the person's name, biography and image when available, external identity, distinct work
count, Cabinet's public `averageRating`, and the credit roles found in Cabinet. The person average is the
rating-weighted arithmetic mean of all public Cabinet ratings across the person's distinct imported works; a work
with multiple credits contributes its ratings once, and no public ratings yields `null`. External provider scores are
not used. The works endpoint combines imported media with TMDB and MusicBrainz
catalogs for every external identity attached to the person. Imported media have priority when an external reference
matches `(source, externalId)`, so each work is listed once. Local credits preserve every role and character; external
previews expose the role returned by the provider. `language` overrides `Accept-Language`; if neither is present,
the locale defaults to `pt-BR`. `type` is optional, and `size` accepts values from 1 to 40.

Supported examples are:

```http
GET /v1/people/{personId}/works
GET /v1/people/{personId}/works?type=MOVIE
GET /v1/people/{personId}/works?type=ALBUM&language=en-US
```

Each work has the following shape:

```json
{
  "id": null,
  "externalId": "550",
  "source": "TMDB",
  "type": "MOVIE",
  "title": "Fight Club",
  "coverUrl": null,
  "releaseDate": "1999-10-15",
  "imported": false,
  "credits": [{"role": "ACTOR", "characterName": "Tyler Durden"}]
}
```

The deprecated `/v1/artists/**` alias remains available for existing clients and returns the legacy artist-work
shape. New clients should use `/v1/people/**`.

Artist identities from TMDB and MusicBrainz are reconciled through their exact Wikidata QID. When both providers
point to the same QID, Cabinet keeps one artist profile with references to both providers and combines all imported
works. Names alone are never used to merge people. Identity lookup is best-effort, so an unavailable provider does
not prevent media import; importing an existing media item again also reconciles legacy credits incrementally.

`/v1/artists` is a compatibility alias for the generic people resource. Alias responses include `Deprecation: true`
and a `Link` header pointing to the successor route.

## Awards

Awards and nominations for imported media and people are sourced from Wikidata. Cabinet accepts only film,
television, literary, and music award families. Other `award received` values such as state honours, editorial lists,
and unrelated rankings are ignored.

```http
GET /v1/media/{mediaId}/awards?result=WIN&page=0&size=20
GET /v1/people/{personId}/awards?result=NOMINATION&page=0&size=20
GET /v1/artists/{personId}/awards?page=0&size=20
```

`result` is optional and accepts `WIN` or `NOMINATION`; `size` accepts 1 through 100. A matching win replaces the
corresponding nomination, so `totalNominations` counts only non-winning nominations. Results are sorted by year and
date descending, with undated entries last.

Award lookups use the same persisted stale-while-revalidate behavior as external availability. A first request
returns `202 Accepted`, `Retry-After: 2`, and `state: PENDING`. Existing expired data returns immediately with
`state: STALE` while a refresh runs. `NOT_LINKED` means that the media or person does not have a Wikidata QID.
Successful results are refreshed every seven days; provider failures retry after one hour.

```json
{
  "subjectId": "e14e30d7-4d53-40d7-92f6-12434e82245a",
  "subjectType": "PERSON",
  "state": "READY",
  "fetchedAt": "2026-07-20T18:00:00Z",
  "expiresAt": "2026-07-27T18:00:00Z",
  "totalWins": 1,
  "totalNominations": 2,
  "items": [
    {
      "id": "05c403d8-1e80-4c84-82c8-c908943c6633",
      "result": "WIN",
      "program": { "qid": "Q19020", "name": "Óscar" },
      "category": { "qid": "Q103916", "name": "Óscar de melhor ator" },
      "ceremony": { "qid": "Q20022969", "name": "Oscar 2016" },
      "eventDate": "2016-02-28",
      "eventYear": 2016,
      "datePrecision": "DAY",
      "work": {
        "mediaId": null,
        "wikidataId": "Q18002795",
        "title": "The Revenant"
      },
      "origin": "WIKIDATA",
      "curated": false,
      "sourceUrl": "https://www.wikidata.org/wiki/Q38111"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

Moderators can list, add, correct, hide, and restore award entries. Edits use optimistic `version` checks and are
audited. Corrected imported entries are not overwritten by later Wikidata refreshes.

```http
GET /v1/moderation/awards?subjectType=MEDIA&subjectId={mediaId}&page=0&size=20
POST /v1/moderation/awards
PUT /v1/moderation/awards/{awardId}
DELETE /v1/moderation/awards/{awardId}
POST /v1/moderation/awards/{awardId}/reset-to-source
```

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
  "recentLikers": [],
  "averageRating": null,
  "listCount": 0,
  "completedCount": 0,
  "recentCompleters": [],
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

Album identity remains the MusicBrainz Release Group. Album details prefer an official MusicBrainz release with the most complete track list for the canonical `tracks` array. `discNumber` distinguishes multi-disc releases. Track duration falls back to the recording duration when the release track itself has no length. Imported album responses may also include `releaseVersions`, populated asynchronously with edition metadata; external previews return an empty list. The array is ordered with the selected primary version first. These records do not include edition-specific tracks.

```json
{
  "type": "ALBUM",
  "genres": [{ "id": "rock", "name": "Rock", "source": "MUSICBRAINZ" }],
  "details": {
    "albumType": "Album",
    "numberOfTracks": 10,
    "animatedCoverUrl": "https://example.com/animated-cover.gif",
    "releaseVersions": [
      {
        "id": "cabinet-release-version-uuid",
        "musicBrainzReleaseId": "musicbrainz-release-uuid",
        "title": "Album title",
        "countryCode": "GB",
        "releaseDate": "1997-09-29",
        "format": "CD",
        "status": "Official",
        "barcode": "1234567890123",
        "catalogNumber": "CAT-001",
        "labelName": "Example Records",
        "coverUrl": "https://coverartarchive.org/release/musicbrainz-release-uuid/front-500",
        "trackCount": 10,
        "primary": true
      }
    ],
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

For an imported series, the authenticated response also includes `watched` and
`unwatchedPreviousCount` for each episode. Tracking uses the episode media UUID returned in `id`:

```http
PUT /v1/me/episodes/{episodeMediaId}/watched
Content-Type: application/json

{ "includePrevious": true }

DELETE /v1/me/episodes/{episodeMediaId}/watched
```

Marking a future episode returns `400 EPISODE_NOT_RELEASED`. `includePrevious` only includes regular seasons and
episodes that are already eligible to watch.

The personalized agenda includes unwatched past episodes and confirmed releases for the requested window. It only
uses series whose library status is `IN_PROGRESS`:

```http
GET /v1/me/episodes/agenda?days=30&overdueLimit=50
```

The response contains `syncPending`, `lastSyncedAt`, `overdueCount`, `overdue`, and `upcoming`. When `syncPending`
is true, the client may poll while the background TMDB synchronization finishes.

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

Ratings and reviews are independent. A member has at most one current rating and one current review per media item,
but a review no longer requires a rating. Deleting `/v1/me/ratings/{mediaId}` keeps the review, and deleting
`/v1/me/reviews/{mediaId}` keeps the rating. An optional `activityId` in the review request links the current review
to a diary entry owned by the same member for the same media item.

Pro review authors may choose a backdrop independently for each review. The selected provider key is stored on that
review and does not change the viewer-specific artwork preference for the media:

```http
PUT /v1/me/reviews/{reviewId}/backdrop
Content-Type: application/json

{"backdropKey":"/provider-backdrop-key.jpg"}
```

Send `{"backdropKey":null}` to restore the canonical media backdrop. The selected key must be present in the
media's artwork-options response. Review responses include `backdropKey` and a resolved `backdropUrl`; when no review
backdrop is selected, `backdropUrl` is the canonical media backdrop.

The global popular-review endpoint is intended for discovery and homepage sections. It returns public reviews with
non-empty text, ordered by like count and recency. Each item includes both the regular review object and a media
summary, avoiding an additional media lookup per card:

```http
GET /v1/reviews/popular?limit=12
```

The public highlight endpoints return at most three public reviews as a JSON array:

```http
GET /v1/media/{mediaId}/reviews/popular
GET /v1/media/{mediaId}/reviews/recent
```

`popular` orders reviews by their number of likes, then by creation date and ID. `recent` orders reviews by creation
date and ID (newest first). Responses include `likeCount`, `liked`, and `recentLikers`; `liked` is `false` for
anonymous requests. `recentLikers` contains at most the five most recent accounts, each with `id`, `username`, and
`avatarUrl`. Responses also include `likedByAuthor` and `reconsumedByAuthor` to indicate whether the review author
liked or reconsumed the reviewed media. Both endpoints return `404 MEDIA_NOT_FOUND` when the media does not exist.

Review likes require authentication, mutations require CSRF, and private reviews are not accessible through these
endpoints:

```http
GET /v1/me/review-likes/{reviewId}
PUT /v1/me/review-likes/{reviewId}
DELETE /v1/me/review-likes/{reviewId}
```

All three responses contain `liked`, `likeCount`, and `recentLikers` using the same five-account format. `PUT` and
`DELETE` are idempotent.

## Diary and consumption logs

A diary entry is an append-only consumption occurrence. Creating another entry for the same media item preserves
the previous entry and uses `RELOGGED` when `reconsumption` is true. Its rating, review and tags are historical
snapshots; a supplied rating also updates the member's canonical rating, and supplied review text updates the
canonical review while linking it to the new entry.

```http
POST /v1/me/diary
Content-Type: application/json

{
  "mediaId": "6c64fb1f-8af4-4eca-92ec-d086af80b87a",
  "occurredOn": "2026-07-19",
  "reconsumption": true,
  "rating": 4.5,
  "review": "Funcionou ainda melhor na segunda vez.",
  "containsSpoilers": false,
  "visibility": "PUBLIC",
  "tags": ["cinema", "com:amigos"]
}
```

```http
GET /v1/me/diary?page=0&size=20
GET /v1/users/{username}/diary?page=0&size=20
DELETE /v1/me/diary/{entryId}
```

The personal endpoint includes private entries. The public endpoint only includes public entries unless the member
is reading their own profile. Deleting an entry removes only the historical occurrence; canonical ratings and
reviews are preserved, and a linked review is detached from the deleted entry.

## Lists

The global popular-list endpoint returns public lists ordered by like count and update date. Items include owner,
item and like counts, plus up to four preview covers:

```http
GET /v1/lists/popular?limit=12
```

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

## Pro artwork preferences

The account tier is independent from the community role. Existing and newly registered accounts default to `FREE`;
administrators can assign `PRO` with `PATCH /v1/admin/community-users/{userId}/tier` and
`{ "accountTier": "PRO" }`.

Pro members can select provider-owned artwork for imported media. Movies and series use TMDB posters and backdrops;
albums use front covers from Cover Art Archive and do not support backdrops.

```http
GET /v1/me/media/{mediaId}/artwork-options
PUT /v1/me/media/{mediaId}/artwork
DELETE /v1/me/media/{mediaId}/artwork
```

The `PUT` body is a full replacement. A null key restores that slot to the canonical artwork. Keys must come from
the options response; arbitrary image URLs are rejected.

```json
{
  "coverKey": "/provider-cover-key.jpg",
  "backdropKey": null
}
```

Preferences never modify canonical media metadata. Authenticated reads resolve them for the viewer in media details,
search, library, lists and profiles. Anonymous and Free viewers receive canonical URLs. Downgrading an account keeps
the preference stored but inactive.

## Interest graph and recommendations

The authenticated interest graph is private to its owner. It combines ratings, media likes, library states and
explicit preferences. Explicit changes and new activity are reflected on the next request; no background refresh is
required.

```http
GET /v1/me/recommendations?type=MOVIE&limit=20
GET /v1/me/interests?targetType=GENRE&page=0&size=20
GET /v1/me/interests/options?targetType=PERSON&query=spielberg&limit=20
```

Recommendations support `MOVIE`, `SERIES`, `ALBUM` and `BOOK`. Without `type`, those formats are mixed. Items already
present in the member's library, ratings, likes or explicit media preferences are excluded. Personalized items include
up to three genre, person or related-work reasons; when the graph cannot fill the requested limit, seven-day trending
items are appended with source `TRENDING`.

Explicit preferences are idempotently upserted and target a Cabinet genre UUID, person UUID, or imported-work UUID.
`GET /v1/me/interests/options?targetType=GENRE` returns genre UUIDs and labels in the requested locale
(`locale=pt-BR|en-US` or `Accept-Language`). During migration, an old genre name is accepted only when it
resolves to exactly one genre:

```http
PUT /v1/me/interests
Content-Type: application/json

{
  "targetType": "GENRE",
  "targetId": "<genre-uuid>",
  "preference": "POSITIVE"
}
```

```http
DELETE /v1/me/interests?targetType=GENRE&targetId=<genre-uuid>
```

Imported media details expose the Cabinet genre UUID in `genres[].id`; external previews retain the provider's
`id` and `source`. Library filter options return `{ "id": "<genre-uuid>", "name": "Ficção científica" }`
entries, and `GET /v1/users/{username}/library?genre=<genre-uuid>` filters by identity.
Legacy name filters are accepted only when the name is unambiguous.

Deleting removes only the explicit override. An interest inferred from existing activity may remain visible. Interest
pages expose a normalized `strength` from 0 to 1, but recommendation responses do not expose the internal score.

## Front-end types

Ready-to-use TypeScript definitions are available in [`media-api.types.ts`](./media-api.types.ts).
