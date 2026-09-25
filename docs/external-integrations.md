# External Integrations

## Provider routing

External media clients normalize provider responses into `ExternalMedia`. `ExternalMediaProviderRegistry` selects a client by `(ExternalSource, MediaType)`. Artwork and person-work lookups use separate interfaces and registries.

| Provider | Source enum | Supported catalog types | Main uses | Credential/configuration |
| --- | --- | --- | --- | --- |
| TMDB | `TMDB` | `MOVIE`, `SERIES` | Search, details, credits, seasons/episodes, images, external IMDb ID, watch providers, person works | `TMDB_ACCESS_TOKEN` or `TMDB_API_KEY` |
| MusicBrainz | `MUSICBRAINZ` | `ALBUM`, `TRACK` | Search, release/recording details, tracks, artist works, URL relationships | Identifiable `MUSICBRAINZ_USER_AGENT` |
| Google Books | `GOOGLE_BOOKS` | `BOOK` | Book search/details | Optional `GOOGLE_BOOKS_API_KEY` |
| Wikidata | `WIKIDATA` | Enrichment across catalog identities | QID lookup, genres/images/logos, cross-provider identities, relations, awards | Identifiable `WIKIDATA_USER_AGENT`; configurable timeout |
| OMDb | `OMDB` | Imported `MOVIE`, `SERIES` | IMDb rating, Tomatometer, Metascore | `OMDB_API_KEY` |
| Cover Art Archive | represented as artwork provider | `ALBUM`, `TRACK` where MusicBrainz identity exists | User-selectable covers | No key |

TheAudioDB properties are present in configuration, but there is no TheAudioDB client in the current source tree. Other values in `ExternalSource` represent identities, data attribution, or future integrations and do not necessarily have a direct client.

## HTTP behavior

The primary `RestClient.Builder` uses Java's HTTP client with:

- 3-second connection timeout;
- 10-second read timeout;
- normal redirect following.

Wikidata has its own builder and defaults to a 30-second read timeout because SPARQL queries can be slower. Provider base URLs are configuration properties, allowing a test stub or gateway to replace the public endpoint.

Provider exceptions are translated as:

- rate limit: HTTP `429` with `ProblemDetail`;
- provider failure: HTTP `502` with `ProblemDetail`;
- invalid provider/type/ID combination: usually HTTP `400`.

Wikidata enrichment is best-effort on broader media flows. Failure to enrich a valid primary-provider response should not normally fail the whole search/detail request.

## Authentication details

TMDB supports a bearer access token and an API key. At least one should be configured for useful movie/series behavior. OMDb explicitly exposes `isConfigured`; missing OMDb configuration produces a persisted `NOT_CONFIGURED` ratings section instead of application startup failure.

MusicBrainz and Wikidata require polite, identifiable user-agent values. Replace the repository default with an application name and monitored contact URL or email in production.

## Search and import

The external search endpoint can query a specific media type or fan out through relevant providers. Provider pagination is normalized through `startIndex` and `maxResults`. Returned entries are previews, may contain incomplete credits/details, and include `imported` when an existing external reference resolves to internal media.

Import performs an authoritative detail fetch and persists:

- the common `Media` row;
- the relevant type-specific detail row;
- the primary external reference and discovered secondary references;
- genres and artwork URLs;
- credits and canonical people;
- album tracks or series structures where included;
- discoverable Wikidata identity and relations on a best-effort basis.

The unique external identity makes repeat import an update/backfill operation rather than a duplicate insert.

For albums, MusicBrainz Release Group remains the canonical Cabinet album identity and continues to drive Cover Art Archive's release-group artwork. Release editions are fetched asynchronously from the MusicBrainz Release Group browse endpoint and stored as `AlbumReleaseVersion` metadata; this does not block import. The first release-version read queues a sync for older albums with no stored editions unless a sync is already active or has succeeded. The artwork picker uses the same paginated MusicBrainz release snapshots as edition sync, so it can offer every release with a front cover without making an individual Cover Art Archive request for each edition. The canonical `AlbumTrack` list still comes from the selected representative release, and edition-specific tracklists are not persisted in this stage.

## Availability and external ratings

`GET /v1/media/{mediaId}/external-info` reads persistent snapshots and schedules refreshes.

| Media | Availability | Ratings |
| --- | --- | --- |
| Movie/series | TMDB regional watch providers, attributed to JustWatch; 24-hour TTL | OMDb; 12-hour TTL for releases within roughly 60 days, otherwise 7 days |
| Album/track | MusicBrainz listen/buy URL relationships, stored under global region; 7-day TTL | Not supported |
| Book/episode | Not supported by the current worker | Not supported |

Provider failures and missing required references are cached for one hour before retry. Video offers use the requested two-letter country. Music offers are global even though the response section retains the requested country context.

Response section states are `READY`, `EMPTY`, `PENDING`, `STALE`, `ERROR`, `NOT_CONFIGURED`, and `NOT_SUPPORTED`. A first miss returns pending and the controller responds `202` with `Retry-After: 2`. Expired data is served as `STALE` while refresh occurs.

## Awards

Awards are fetched from Wikidata only when the media/person has a QID. Successful or empty results have a seven-day TTL. Missing linkage is `NOT_LINKED`; provider errors retry after one hour. The returned `incomplete` flag is treated as an error rather than replacing a complete persisted snapshot with partial data.

Source synchronization must coexist with curator edits. The persistence service owns replacement rules so source refresh does not silently discard manual/curated intent.

## Wikidata and person-work caches

Wikidata enrichment and relation lookups use in-memory caches:

- hit TTL: 6 hours;
- miss TTL: 15 minutes;
- stale fallback: up to 7 days;
- maximum enrichment cache entries: 2,000;
- bounded retries for transient failures and rate limits, with at most 30 seconds of rate-limit backoff.

`PersonWorksCatalogService` uses the same hit/miss/stale durations with a maximum of 500 entries. These caches are process-local and empty after restart.

## Artwork

TMDB artwork catalogs support movie/series image choices; Cover Art Archive supports music artwork. Assets have a stable provider key and URL. A PRO user's selected keys and resolved URLs are stored in `UserMediaArtworkPreference`, while global catalog URLs remain unchanged.

Provider URLs can expire or change. The key is the durable selection identity; the service resolves available options and validates submitted keys rather than accepting arbitrary replacement URLs.

## Adding a provider

1. Add or reuse an `ExternalSource` value.
2. Implement `ExternalMediaProvider` and declare exact supported media types.
3. Return normalized `ExternalMedia`, including primary identity and type-specific details.
4. Add artwork/person-work interfaces only when supported.
5. Add typed configuration properties and safe timeouts.
6. Add client tests using a stub HTTP server; cover error, empty, pagination, and localization behavior.
7. Update provider routing, import assumptions, [Media API details](media-api.md), and this matrix.
8. Decide whether failures should fail the request, degrade enrichment, or become a persisted stale-while-revalidate section.

## Firebase Cloud Messaging

O módulo de notificações usa o Firebase Admin SDK para entregar push iOS. Configure `FIREBASE_MESSAGING_ENABLED`, `FIREBASE_PROJECT_ID` e Application Default Credentials (ou `GOOGLE_APPLICATION_CREDENTIALS`) no ambiente; a chave de serviço nunca deve entrar no repositório. O gateway envia alerta genérico com `notificationId` e `type`, sem preview/comentário. Tokens inválidos são desativados e falhas transitórias são tentadas novamente pela fila PostgreSQL. A dependência/capacidade iOS está preparada, mas o app não registra tokens nem recebe push enquanto a ativação estiver adiada; para ativar futuramente, incluir `GoogleService-Info.plist` no bundle e configurar a credencial APNs no projeto Firebase.
