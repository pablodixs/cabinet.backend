# Feature Modules

## Authentication (`auth`)

`AuthController` exposes login, registration aliases, current-user lookup, CSRF token lookup, and logout. `AuthService` authenticates with Spring's `AuthenticationManager`, stores the resulting `SecurityContext` in the HTTP session, rotates the session ID on login, and clears both the security context and session cookie on logout. Registration delegates account creation to `UserService` and immediately signs the new account in.

Identifiers can be either email or username. Passwords are stored as BCrypt hashes. See [Security](security.md).

## Users and profiles (`user`)

### Accounts and profiles

`UserService` creates accounts and enforces normalized uniqueness for username and email. `UserProfileService` provides search, profile summaries, full public profiles, recent items, statistics, and activity feeds. Profiles have their own `Visibility`; access is filtered by viewer identity, accepted follows, and blocks.

Roles (`USER`, `MODERATOR`, `ADMIN`) and account tiers (`FREE`, `PRO`) are stored separately. Admin operations audit role and tier transitions in dedicated change tables.

### Library

`UserMediaService` stores one `UserMedia` row per user/media pair with a status of `PLANNED`, `IN_PROGRESS`, `COMPLETED`, `PAUSED`, or `DROPPED`. Status changes update timestamps and create profile activity records. A series entering `IN_PROGRESS` emits an after-commit request to refresh its seasons and episodes.

Future media may be planned but cannot be consumed, rated, or reviewed until its release date. This rule is centralized in `MediaConsumptionPolicy` and currently compares against the JVM's local calendar date. Episode-release scheduling separately uses the São Paulo business date through `CabinetTime`.

### Diary

The diary is backed by `UserMediaActivity`, not by `Review`. A diary entry records an occurrence date, consumption/rewatch activity type, optional rating and review text, spoiler flag, visibility, tags, and source metadata. Creation can update the user's rating/review and completion state. Deleting a diary entry removes the activity, while ratings and reviews are maintained as their own resources according to service rules.

Public diary access honors profile and entry visibility.

### Episode tracking and agenda

Every persisted series episode has its own `Media` row of type `EPISODE`. `UserEpisodeWatch` records watched episodes. `includePrevious=true` marks eligible earlier episodes in the same series as watched. Removing a watch can update series progress.

The agenda combines in-progress series with persisted air dates, returning upcoming items and a bounded number of overdue items. Series data older than 24 hours is scheduled for refresh.

### Social graph

The social graph consists of `UserFollow` and `UserBlock` edges:

- Public profiles accept a follow immediately.
- Restricted profiles can leave the relationship in `PENDING` until accepted.
- Following yourself is invalid.
- Blocking removes follow relationships in both directions and prevents social visibility.
- Counts are denormalized on `User` and updated with graph transitions.
- Follower, following, request, and block lists use cursor pagination ordered by relationship timestamp and user UUID.

`SocialAccessPolicy` is the shared gate for profile and followers-only content.

### Interest graph and recommendations

`UserInterestPreference` stores explicit positive or negative preferences for genres, people, or media. `InterestGraphService` combines those explicit nodes with implicit signals from ratings, library status, credits, genres, and media relations. The scoring policy reserves 55% of inferred media signal for genres and 35% for people, propagates half-weight through relations, and caps implicit seed and inferred-node strength.

`RecommendationService` generates personalized candidates for supported top-level media types and falls back to trending results when personalization is insufficient. It excludes media the user has already interacted with and returns human-readable reason objects identifying the contributing genre, person, media, or trending fallback.

### Letterboxd import

The multipart importer parses a Letterboxd export, groups data by film, and creates a durable job plus item rows. A background matcher searches TMDB and classifies items as automatic matches or requiring review. The user may resolve/skip conflicts before confirmation. Application occurs item by item in independent transactions so one failure does not roll back successful items. Completed payloads are retained for seven days and then redacted; ready jobs also expire.

## Media catalog (`media`)

### Catalog and type details

`Media` stores fields common to books, movies, series, albums, tracks, and episodes. One-to-one detail records hold type-specific fields. External identities live in `ExternalReference`; `(source, externalId)` is globally unique, and each media row can have at most one reference for a given source. A boolean marks references treated as primary by import logic.

External results are normalized into `ExternalMedia`. Provider-specific clients are hidden behind registries. Search previews indicate whether the work is already imported. After import, clients should use `/v1/media/{mediaId}` so normal details do not depend on an external provider.

### Credits and people

Credits connect media to canonical `Person` records with roles and ordering. `PersonIdentityService` merges identities using external references, preferring Wikidata when available. A bounded number of identities are enriched per import to limit provider traffic. People endpoints expose details, local works, provider-assisted works, and awards. `/v1/artists/**` remains as a deprecated alias for `/v1/people/**`.

### Ratings, reviews, and likes

- A `Rating` is one numeric value from 0.5 to 5.0 per user/media, with visibility.
- A `Review` is text attached one-to-one to a rating record and can optionally reference the diary activity that published it.
- Ratings can exist without reviews; review operations can create or update the linked rating.
- `MediaLike` and `ReviewLike` are independent binary community signals.
- Public aggregates exclude non-public ratings/reviews and private library entries.
- Popular review ordering uses community interaction and recency queries; viewer-specific responses include whether the current user liked the review.

### Discovery

App search supports `RELEVANCE` and community `RATING` ordering with opaque cursor pagination. Top-rated rankings use page pagination. Trending ranks daily snapshots of public ratings, likes, completions, diary logs, public-list additions, and reviews with configurable weights and exponential time decay.

`MoreByService` uses the primary contributor for a work, combines locally imported works with provider results, de-duplicates identities, and exposes whether the provider type is supported.

### Series, seasons, and episodes

TMDB series details persist seasons lazily. Fetching a season synchronizes episode metadata and creates/updates episode-level `Media` identities, enabling episode ratings, diary entries, library activity, and watched state. A daily scheduler refreshes series currently in progress.

### Relations

Directed `MediaRelation` edges represent adaptation pairs, soundtrack pairs, and re-recording pairs. Services return the inverse relation where appropriate. Reports can propose missing or incorrect relations, and approved moderation can create the selected link.

### External information

Availability offers and external ratings are persisted separately from catalog metadata. Reads implement stale-while-revalidate with regional video availability, global music links, and metric-specific OMDb ratings. See [External integrations](external-integrations.md).

### Awards

Award data is linked to media or people, normally sourced from Wikidata. `AwardSyncState` tracks pending/ready/empty/error states and expiry. Curators can create, edit, hide, or reset awards; every change creates an audit revision. Manual edits use optimistic versions and are preserved separately from source refresh behavior.

### Moderation and reports

Authenticated users can report external or imported media. Moderators review reports, manage catalog metadata, and curate awards. Metadata edits record before/after JSON snapshots. A resolved report generates a notification for the reporter. Moderator endpoints use method security and reload current role state from the database.

### Artwork preferences

Global catalog artwork remains on `Media`; PRO users can store personal cover/backdrop selections in `UserMediaArtworkPreference`. Artwork options come from TMDB or Cover Art Archive depending on type. Response assemblers use `UserArtworkResolver` so personal preferences are applied only for the current viewer.

## Lists (`lists`)

Users create named media lists with a description, cover, visibility, and an `ordered` flag. Membership is unique per list/media and may contain notes and a numeric position. Only the owner can mutate a list. Public discovery supports search, globally popular lists, lists containing a media item, and per-media popular lists.

List likes are allowed only when the viewer can access the list and cannot be used to bypass private content. Like notifications are grouped by list and recomputed when a like is removed.

There is currently no HTTP endpoint that deletes an entire list or explicitly reorders items; updates cover metadata and item addition/removal.

## Comments (`comments`)

Comments attach to either a media list or a review, never both. Only accessible/public content is commentable. Threads have one reply level: a comment may reply to a root, but a reply cannot itself receive a reply. Authors may edit their own active comments.

Deletion is soft when a root has replies so the thread remains readable as a tombstone; otherwise it can be removed. Related notifications are cleaned up. Comment reads return roots with nested reply arrays and viewer-specific `canEdit`.

## Notifications (`notifications`)

Notifications are durable rows scoped to a recipient. Current event types cover list/review likes, list/review comments, comment replies, resolved reports, released episodes, and Letterboxd imports ready for review or completed. Like notifications aggregate actors; comment, report, and import notifications are individual.

REST is the source of truth. SSE emits `connected`, `notifications-changed`, and heartbeat events only as invalidation signals. Retention and episode-release notifications are scheduled. Detailed behavior is in [Notifications and iOS push](notifications-and-ios-push.md).

## Common utilities (`common`)

`ApiException` carries an HTTP status and stable machine code. `GlobalExceptionHandler` produces consistent JSON errors for API, validation, malformed body, registration conflict, and authentication failures. `PageResponse` and `CursorPageResponse` are shared pagination envelopes. `CabinetTime` centralizes the `America/Sao_Paulo` business date.
