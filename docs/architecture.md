# Architecture

## System shape

Cabinet is a layered modular monolith. A single Spring Boot process owns HTTP handling, business rules, persistence, scheduling, provider calls, and SSE connections. PostgreSQL is the only durable infrastructure dependency represented in this repository.

```mermaid
flowchart LR
    Client["Web or mobile client"] --> MVC["Spring MVC controllers"]
    MVC --> Security["Session, CSRF, method authorization"]
    MVC --> Services["Transactional application services"]
    Services --> Repositories["Spring Data JPA repositories"]
    Repositories --> DB[(PostgreSQL)]
    Services --> Registries["External provider registries"]
    Registries --> APIs["TMDB, MusicBrainz, Google Books, Wikidata, OMDb"]
    Services --> Events["After-commit domain events"]
    Events --> Workers["In-process executors and schedulers"]
    Workers --> Repositories
    Workers --> APIs
```

There is no message broker, distributed cache, object store, or separate worker deployment. Asynchronous work is executed inside the API process.

## Package boundaries

| Package | Responsibility |
| --- | --- |
| `auth` | Login, registration handoff, logout, current session, CSRF response |
| `security` | Filter chain, authenticated principal, role resolution, CORS |
| `user` | Accounts, profiles, library, diary, episode tracking, social and interest graphs, Letterboxd import |
| `media` | Catalog and type details, external lookup/import, credits, people, ratings, reviews, likes, awards, relations, moderation |
| `lists` | Owned/public media lists, ordered membership, likes, discovery |
| `comments` | One-level comment threads on lists and reviews |
| `notifications` | Persistent notification inbox and SSE refresh signals |
| `common` | API exceptions/page wrappers and the São Paulo business date |

Modules reference each other's entities and services directly. The boundaries are organizational rather than independently deployable. In particular, community actions in `lists`, `comments`, and `media` call `NotificationService`, while `user` services depend on the media catalog.

## Layering

### Controllers

Controllers define `/v1` HTTP contracts, validate request parameters and bodies, obtain the `AuthenticatedUser`, and translate a few service outcomes into status codes. They should not contain domain logic. Public controllers accept a nullable principal so responses can include viewer-specific state when a session exists.

### Services

Services implement authorization at the resource level, business state transitions, orchestration, response assembly, and transactional boundaries. Read paths generally use `@Transactional(readOnly = true)`; writes use `@Transactional`. Domain failures are typically raised as `ApiException` with a stable code and HTTP status.

### Repositories

Spring Data JPA repositories contain derived queries and JPQL/native projections for rankings, public aggregates, search, and social pagination. Some performance behavior depends on PostgreSQL indexes added by Flyway.

### External clients and registries

Provider clients implement `ExternalMediaProvider`, `ExternalPersonWorksProvider`, or artwork provider interfaces. Registries select providers by `ExternalSource` and media type, keeping controllers and orchestration services provider-neutral.

## Core request flows

### External media to persisted catalog

```mermaid
sequenceDiagram
    participant C as Client
    participant MC as MediaController
    participant ES as ExternalMediaService
    participant P as Provider registry/client
    participant DB as PostgreSQL

    C->>MC: Search or request external details
    MC->>ES: source, type, externalId, language
    ES->>P: provider lookup
    P-->>ES: normalized ExternalMedia
    ES-->>C: preview with imported flag
    C->>MC: POST /v1/media/external/import
    MC->>ES: import request
    ES->>P: fetch authoritative details
    ES->>DB: upsert Media, type details, references, credits, relations
    ES-->>C: internal/external identifiers
```

The internal UUID becomes the preferred identifier after import. Re-import is idempotent around the external reference and also reconciles credits for older data.

### Community interaction

A typical write loads the authenticated user and target resource, checks visibility and release policy, then upserts or deletes a row. Likes and comments synchronize persistent notifications in the same business transaction. `NotificationChangedEvent` is emitted and handled after commit so SSE never advertises uncommitted data.

### Stale-while-revalidate read

External availability, external ratings, and awards use persisted snapshots. A cache miss schedules a provider fetch and returns a pending response, normally as `202 Accepted` with `Retry-After: 2`. Expired data is returned as stale while an in-process worker refreshes it. Per-process in-flight sets coalesce duplicate work.

### Visibility-aware public reads

`Visibility` can be `PUBLIC`, `FOLLOWERS`, or `PRIVATE`. Public profile, diary, review, and list reads combine the resource visibility with follow/block state through services such as `SocialAccessPolicy`. Aggregate community numbers deliberately exclude private data.

## Consistency and concurrency

- Transactions are local database transactions; no distributed transaction exists around provider calls.
- Several entities use uniqueness constraints to make upserts safe, including user/media ratings, likes, list membership, external references, and episode identity.
- `Media` and `AwardEntry` use optimistic `@Version` fields for moderator edits.
- Import application processes each Letterboxd item in `REQUIRES_NEW`, allowing partial progress and per-item failures.
- In-memory de-duplication and SSE registries are process-local. Multiple API replicas may duplicate background provider work and cannot broadcast SSE signals to connections held by other replicas.
- Episode release logic uses `America/Sao_Paulo` through `CabinetTime`; some schedulers explicitly use that zone, while media release checks and schedules without an explicit zone use the JVM's local/default time semantics.

## API response conventions

- Persisted IDs are UUIDs.
- `PageResponse<T>` is page-number based.
- `CursorPageResponse<T>` is used for social lists; its cursor is an opaque URL-safe encoding of timestamp and user ID.
- Media search has its own opaque cursor contract because relevance and rating sorts paginate differently.
- `ApiErrorResponse` contains `code`, `message`, and `fieldErrors`.
- Validation errors use `VALIDATION_ERROR`; invalid JSON uses `INVALID_REQUEST_BODY`.
- A few feature-specific exception handlers provide additional media/provider error mappings.

## Known architectural constraints

- The executable has no Actuator dependency, so it exposes no standard health/readiness/metrics endpoints.
- Swagger paths are permitted by security but no OpenAPI library is declared.
- Hibernate schema update and Flyway are enabled together in production configuration. This weakens migration-only schema reproducibility.
- Provider caches in `PersonWorksCatalogService` and in-flight work maps are in-memory and reset at restart.
- SSE is a refresh hint, not a durable event stream; REST and PostgreSQL remain the source of truth.
- Background task queues are bounded but not durable. The Letterboxd scheduler recovers jobs in `MATCHING` or `IMPORTING` at startup; other queued work is recreated only by subsequent reads or scheduled scans.
