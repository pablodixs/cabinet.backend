# API Reference

This is the route inventory for the controllers in the repository. It focuses on discoverability and links related behavior to focused guides. The Java request/response records remain the exact field-level contract.

## Conventions

Access labels used below:

- **Public** — no session required; a session may enrich viewer-specific fields.
- **User** — authenticated `CABINET_SESSION` required.
- **Moderator** — authenticated and effective role `MODERATOR` or `ADMIN`.
- **Admin** — effective role `ADMIN`.

All mutating methods require CSRF, including public registration and login. Call `GET /v1/auth/csrf`, keep the cookies, and send the returned token using its returned header name.

### Pagination

Page-number endpoints return:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Social graph endpoints return `{ "items": [], "nextCursor": null, "hasMore": false }`. Media app search returns `items` and `nextCursor`. Treat every cursor as opaque.

### Errors

Most application errors have this shape:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Revise os campos destacados",
  "fieldErrors": { "field": "message" }
}
```

Security failures use `AUTHENTICATION_REQUIRED` (`401`) or `ACCESS_DENIED` (`403`). External provider and invalid media requests may instead use RFC 9457-style `ProblemDetail`, with `400`, `429`, or `502`.

## Authentication

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /v1/auth/csrf` | Public | Return `{ token, headerName }` and establish CSRF cookie state. |
| `POST /v1/auth/login` | Public | Login with `{ identifier, password }`; identifier is email or username. |
| `POST /v1/auth/register` | Public | Register and log in with `{ username, displayName, email, password }`. |
| `POST /v1/auth/create` | Public | Alias of `/register`. |
| `GET /v1/auth/me` | User | Reload and return the current account/role/tier. |
| `POST /v1/auth/logout` | User | Invalidate the session and clear authentication cookies; returns `204`. |

`POST /v1/users/create` also creates an account and returns `201` without logging in. It is not present in the security public allowlist and therefore requires an existing session; client registration should use `/v1/auth/register`.

## Catalog search, import, and discovery

| Method and path | Access | Query/body and behavior |
| --- | --- | --- |
| `GET /v1/search/header` | Public | Lightweight local search for the header. `query` (2–100), `scope=ALL\|MEDIA\|ARTIST`, optional media `type`; returns at most 5 relevance-ranked media/artists. |
| `GET /v1/media/search` | Public | `query` (min 3), optional `type`, `sort=RELEVANCE\|RATING`, `cursor`, `limit=20` (1–40). App-facing merged search. |
| `GET /v1/media/external/search` | Public | Optional `type`; required `query`; `language=pt-BR`, `startIndex=0`, `maxResults=20` (max 40). Searches provider catalogs. |
| `GET /v1/media/external/{source}/{type}/{externalId}` | Public | `language=pt-BR`. Provider-backed detail preview. |
| `GET /v1/media/external/{source}/{type}/{externalId}/relations` | Public | `language=pt-BR`, `maxResults=12` (max 40). Related/adapted works. |
| `GET /v1/media/external/TMDB/SERIES/{externalId}/seasons/{seasonNumber}` | Public | `language=pt-BR`; direct external season preview. |
| `POST /v1/media/external/import` | User | `{ source, externalId, mediaType }`; imports/upserts a work and returns `201`. |
| `GET /v1/media/rankings/top-rated` | Public | Optional `type`; `page=0`, `limit=20` (max 40). |
| `GET /v1/media/rankings/trending` | Public | Optional `type`; `days=7` (1–30), `limit=12` (max 40). |
| `GET /v1/media/rankings/anticipated` | Public | `limit=6` (max 40). Future movies ranked by public `PLANNED` entries. |

Supported catalog types are `BOOK`, `MOVIE`, `SERIES`, `TRACK`, `ALBUM`, and `EPISODE`, although a provider or endpoint may support only a subset. Full media examples and provider behavior are in [Media API details](media-api.md).

## Persisted media

| Method and path | Access | Query/body and behavior |
| --- | --- | --- |
| `GET /v1/media/{mediaId}` | Public | Stored detail response, optionally viewer-aware artwork/community state. |
| `GET /v1/media/{mediaId}/credits` | Public | Required `role`; `page=0`, `limit=20` (max 40). |
| `GET /v1/media/{mediaId}/more-by` | Public | `language=pt-BR`, `limit=12` (max 40). More work by the primary contributor. |
| `GET /v1/media/{mediaId}/external-info` | Public | `country=BR`; may return `202` plus `Retry-After: 2`. |
| `GET /v1/media/{mediaId}/awards` | Public | Optional `result=WIN\|NOMINATION`; `page=0`, `size=20` (max 100); may return `202`. |
| `GET /v1/media/{seriesId}/seasons/{seasonNumber}/episodes` | Public | `language=pt-BR`; syncs/stores a season when needed and includes viewer progress. |
| `PUT /v1/media/{mediaId}/wikidata` | User | `{ wikidataId, language? }`; links a valid Wikidata QID and enriches metadata. |
| `POST /v1/media/reports` | User | Create a catalog/relation report; returns `201`. |

## Ratings, reviews, and likes

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /v1/me/ratings/{mediaId}` | User | Return the user's rating or `204`. |
| `PUT /v1/me/ratings/{mediaId}` | User | Upsert `{ rating }`, from `0.5` to `5.0`. |
| `DELETE /v1/me/ratings/{mediaId}` | User | Delete rating; returns `204`. |
| `GET /v1/reviews/popular` | Public | Globally popular reviews; `limit=12` (max 40). |
| `GET /v1/reviews/{reviewId}` | Public | One accessible public review. |
| `GET /v1/media/{mediaId}/reviews` | Public | Page of reviews; `page=0`, `size=10` (max 50). |
| `GET /v1/media/{mediaId}/reviews/popular` | Public | Curated short popular set. |
| `GET /v1/media/{mediaId}/reviews/recent` | Public | Curated short recent set. |
| `GET /v1/me/reviews/{mediaId}` | User | User's review or `204`. |
| `PUT /v1/me/reviews/{mediaId}` | User | Upsert rating/text/spoiler/visibility and optional `activityId`. |
| `DELETE /v1/me/reviews/{mediaId}` | User | Delete review; returns `204`. |
| `GET /v1/me/likes/{mediaId}` | User | Current media-like state/count. |
| `PUT /v1/me/likes/{mediaId}` | User | Like media. |
| `DELETE /v1/me/likes/{mediaId}` | User | Unlike media; returns `204`. |
| `GET /v1/me/review-likes/{reviewId}` | User | Current review-like state/count. |
| `PUT /v1/me/review-likes/{reviewId}` | User | Like an accessible review. |
| `DELETE /v1/me/review-likes/{reviewId}` | User | Unlike and return updated state. |

Future/unreleased media reject consumption, rating, and review writes with code `MEDIA_NOT_RELEASED`.

## People and credits

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /v1/people/{personId}` | Public | Person details. |
| `GET /v1/people/{personId}/works` | Public | `page=0`, `size=24` (max 40). |
| `GET /v1/people/{personId}/awards` | Public | Optional `result`; `page=0`, `size=20` (max 100); may return `202`. |

The same three routes exist under `/v1/artists/{artistId}`. They return `Deprecation: true` and a `Link` header pointing to `/v1/people/**`.

## Lists and comments

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /v1/me/lists` | User | List all owned lists. |
| `POST /v1/me/lists` | User | Create `{ name, description?, visibility?, ordered?, coverUrl? }`; returns `201`. |
| `GET /v1/me/lists/{listId}` | User | Owner-only details and items. |
| `GET /v1/me/lists/{listId}/backdrop-options` | Pro | Owner-only backdrop options without language, sourced from movies and series already in the list. |
| `PUT /v1/me/lists/{listId}` | User | Replace editable list metadata. Pro users may also send `backdropMediaId` and `backdropKey` from the backdrop-options response; send both as `null` to clear. |
| `POST /v1/me/lists/{listId}/items` | User | Add `{ mediaId, notes? }`; returns `201`. |
| `DELETE /v1/me/lists/{listId}/items/{itemId}` | User | Remove item; returns `204`. |
| `GET /v1/lists/popular` | Public | `limit=12` (max 40). |
| `GET /v1/lists/search` | Public | `query` (3–80), `page=0`, `size=20` (max 50). |
| `GET /v1/lists/{listId}` | Public | Accessible list detail; session can reveal viewer-like state. |
| `GET /v1/media/{mediaId}/lists` | Public | `page=0`, `size=20` (max 50). |
| `GET /v1/media/{mediaId}/lists/popular` | Public | Short popular set for one media item. |
| `GET /v1/me/list-likes/{listId}` | User | Current list-like state/count. |
| `PUT /v1/me/list-likes/{listId}` | User | Like an accessible list. |
| `DELETE /v1/me/list-likes/{listId}` | User | Unlike and return updated state. |
| `GET /v1/lists/{listId}/comments` | Public | `page=0`, `size=20` (max 50); root threads with replies. |
| `GET /v1/reviews/{reviewId}/comments` | Public | Same pagination for a review. |
| `POST /v1/me/lists/{listId}/comments` | User | `{ content, parentId? }`. |
| `POST /v1/me/reviews/{reviewId}/comments` | User | `{ content, parentId? }`. |
| `PATCH /v1/me/comments/{commentId}` | User | Replace content with `{ content }`. |
| `DELETE /v1/me/comments/{commentId}` | User | Delete/tombstone own comment; returns `204`. |

## Library, diary, and episodes

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /v1/me/library` | User | Optional `status`, optional `type`, `page=0`, `size=20` (max 50). Each item includes the current user's `liked`, `rating`, `hasReview`, and the media's primary `creator`. |
| `GET /v1/me/library/{mediaId}` | User | One entry or `204`. |
| `PUT /v1/me/library/{mediaId}` | User | Upsert `{ status }`. |
| `DELETE /v1/me/library/{mediaId}` | User | Remove entry; returns `204`. |
| `GET /v1/me/diary` | User | `page=0`, `size=20` (max 50). |
| `POST /v1/me/diary` | User | Create occurrence, rating/review, visibility, and tags; returns `201`. |
| `DELETE /v1/me/diary/{entryId}` | User | Delete own activity; returns `204`. |
| `GET /v1/users/{username}/diary` | Public | Visibility-aware page; `page=0`, `size=20`. |
| `GET /v1/me/episodes/agenda` | User | `days=30` (max 90), `overdueLimit=50` (max 100). |
| `PUT /v1/me/episodes/{episodeMediaId}/watched` | User | `{ includePrevious }`. |
| `DELETE /v1/me/episodes/{episodeMediaId}/watched` | User | Unmark and return updated progress. |

## Users and social graph

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /v1/users/search` | Public | `query` (3–80), `page=0`, `size=20` (max 50). |
| `GET /v1/users/{username}/summary` | User | Compact profile summary; unlike other profile reads, requires a session. |
| `GET /v1/users/{username}/profile` | Public | Visibility-aware full profile. |
| `GET /v1/users/{username}/followers` | Public | Optional `cursor`, `size=20` (max 50). |
| `GET /v1/users/{username}/following` | Public | Optional `cursor`, `size=20` (max 50). |
| `GET /v1/users/{username}/activities` | Public | `page=0`, `size=20` (max 50). |
| `PUT /v1/me/following/{targetUserId}` | User | Follow or request follow; returns resulting state. |
| `DELETE /v1/me/following/{targetUserId}` | User | Unfollow; returns `204`. |
| `GET /v1/me/follow-requests/incoming` | User | Cursor page. |
| `GET /v1/me/follow-requests/outgoing` | User | Cursor page. |
| `PUT /v1/me/follow-requests/{requesterId}/accept` | User | Accept request. |
| `DELETE /v1/me/follow-requests/{requesterId}` | User | Reject request; returns `204`. |
| `DELETE /v1/me/followers/{followerId}` | User | Remove follower; returns `204`. |
| `GET /v1/me/blocks` | User | Cursor page of blocked accounts. |
| `PUT /v1/me/blocks/{targetUserId}` | User | Block and sever follow relationships; returns `204`. |
| `DELETE /v1/me/blocks/{targetUserId}` | User | Unblock; returns `204`. |

## Interests and recommendations

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /v1/me/recommendations` | User | Optional `type`; `limit=20` (max 40). |
| `GET /v1/me/interests` | User | Required `targetType`; `page=0`, `size=20` (max 50). |
| `PUT /v1/me/interests` | User | Upsert `{ targetType, targetId, preference }`. |
| `DELETE /v1/me/interests` | User | Required `targetType` and `targetId`; returns `204`. |
| `GET /v1/me/interests/options` | User | Required `targetType`; optional `query`, `mediaType`; `limit=20` (max 40). |

Targets are `GENRE`, `PERSON`, or `MEDIA`; explicit preferences are `POSITIVE` or `NEGATIVE`.

## Letterboxd imports

| Method and path | Access | Purpose |
| --- | --- | --- |
| `POST /v1/me/imports/letterboxd` | User | Multipart part `file`; starts parse/match and returns `202`. |
| `GET /v1/me/imports/letterboxd/active` | User | Returns `{ active, job }`; `job` is null when no import is active. |
| `GET /v1/me/imports/{jobId}` | User | Current job state and counters. |
| `GET /v1/me/imports/{jobId}/items` | User | Optional `state`; `page=0`, `size=50` (max 100). |
| `PUT /v1/me/imports/{jobId}/items/{itemId}/resolution` | User | Select internal `mediaId` or `tmdbId`, ignore, and choose conflict overrides. |
| `POST /v1/me/imports/{jobId}/confirm` | User | Begin application; returns `202`. |
| `POST /v1/me/imports/{jobId}/retry-failures` | User | Requeue failed items; returns `202`. |
| `POST /v1/me/imports/{jobId}/cancel` | User | Cancel an eligible job. |
| `GET /v1/me/imports/{jobId}/failures.csv` | User | Download a CSV failure report. |

## Notifications

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /v1/me/notifications` | User | `page=0`, `size=20` (max 50). |
| `GET /v1/me/notifications/unread-count` | User | Return `{ unreadCount }`. |
| `PATCH /v1/me/notifications/read` | User | `{ ids }`, one to 100 UUIDs; returns `204`. |
| `GET /v1/me/notifications/stream` | User | `text/event-stream` refresh signal connection. |

See [Notifications and iOS push](notifications-and-ios-push.md) for event formats and retention rules.

## Moderator and admin routes

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /v1/moderation/media` | Moderator | Search imported media; `query`, `page=0`, `size=20` (max 50). |
| `PUT /v1/moderation/media/{mediaId}` | Moderator | Replace editable metadata with required optimistic `version`. |
| `GET /v1/moderation/media-reports` | Moderator | Optional `status`; `page=0`, `size=20` (max 50). |
| `POST /v1/moderation/media-reports/{reportId}/review` | Moderator | Approve/reject and optionally create a relation. |
| `GET /v1/moderation/awards` | Moderator | Required `subjectType`, `subjectId`; page options. |
| `POST /v1/moderation/awards` | Moderator | Create a manual award; returns `201`. |
| `PUT /v1/moderation/awards/{awardId}` | Moderator | Update with optimistic `version`. |
| `DELETE /v1/moderation/awards/{awardId}` | Moderator | Hide award; returns `204`. |
| `POST /v1/moderation/awards/{awardId}/reset-to-source` | Moderator | Revert curated fields to source values. |
| `GET /v1/admin/community-users` | Admin | Search accounts; `query`, `page=0`, `size=20`. |
| `PATCH /v1/admin/community-users/{userId}/role` | Admin | `{ role }`; audited. |
| `PATCH /v1/admin/community-users/{userId}/tier` | Admin | `{ accountTier }`; audited. |

## Frequently used request bodies

| Operation | Required and important fields |
| --- | --- |
| Register | `username` 3–30, `displayName` 3–80, valid `email`, `password` 8–100 |
| Create/update list | `name` max 120, `description` max 2000, `visibility`, `ordered`, `coverUrl` max 500 |
| Add list item | `mediaId`; optional `notes` max 2000 |
| Rating | `rating` from 0.5 through 5.0 |
| Review | optional `rating`; required `content` max 20,000; optional `containsSpoilers`, `visibility`, `activityId` |
| Diary entry | `mediaId`, past/present `occurredOn`, `reconsumption`, optional rating/review, required `visibility`, up to 30 tags |
| Comment | required `content` max 2000; optional root `parentId` |
| Media metadata | `version`, `title`; optional localized/images/date/language/country/genres fields |

Enum values and entity relationships are cataloged in [Domain and data model](domain-model.md).
