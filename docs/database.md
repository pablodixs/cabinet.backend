# Database and Persistence

## Runtime configuration

The production application uses PostgreSQL through HikariCP:

- maximum pool size: 5;
- minimum idle: 1;
- connection timeout: 30 seconds;
- idle timeout: 10 minutes;
- maximum connection lifetime: 5 minutes;
- JPA Open Session in View: disabled;
- Hibernate SQL formatting: enabled;
- Hibernate DDL mode: `update`;
- Flyway: enabled by default, baseline-on-migrate at version `0`.

With Open Session in View disabled, services must load everything required by response mapping within their transaction. Repository entity graphs, projections, and explicit service assembly are used to avoid lazy-loading in controllers.

## Flyway and Hibernate caveat

Both Flyway and `hibernate.ddl-auto=update` are active. The migration directory begins with incremental changes that assume an older base schema already exists; it is not a complete hand-written V1 baseline for every entity. Hibernate may create missing base tables before/around lifecycle initialization and may alter mapped columns.

Consequences:

- A schema created from migrations alone may not match a schema created by application startup.
- Destructive/renaming changes are not safely represented by Hibernate update.
- Production schema review must include both migration SQL and generated Hibernate expectations.
- Long term, prefer a complete Flyway baseline and `ddl-auto=validate` for reproducible deployments.

Do not rewrite applied migrations. Add a new versioned migration and update the JPA mapping together.

## Migration history

| Version | Purpose |
| --- | --- |
| `V1` | Extend relation/report check constraints with re-recording types, guarded for legacy schemas. |
| `V2` | Add comments and persistent notifications with target/grouping indexes. |
| `V3` | Add notification `activityAt` and change ordering/retention indexes. |
| `V4` | Add `NOT VALID` foreign keys to tolerate historical orphan rows while protecting new writes. |
| `V5–V6` | Optimize stable recent list-cover lookup with a covering index. |
| `V7` | Split numeric ratings from review text and migrate legacy reviews. |
| `V8` | Convert album tracks and series episodes into independently rateable `Media` identities. |
| `V9` | Rename the external-rating value column. |
| `V10` | Permit `EPISODE` in the media type check constraint. |
| `V11–V12` | Add ranking, trending, and global-list popularity indexes. |
| `V13` | Add episode sync timestamps, watched state, and episode-release notifications. |
| `V14` | Add awards, sync states, revision audit, constraints, and indexes. |
| `V15` | Optimize public profile statistics. |
| `V16` | Add activity/diary, list import identity, and Letterboxd jobs/items; backfill activity. |
| `V17` | Separate reviews from diary activities and add review publication relationships. |
| `V18` | Add animated album cover URL. |
| `V19` | Add rating/review/like activity timestamps and import conflict flags. |
| `V20` | Add account tiers and PRO artwork preferences with audit table. |
| `V21` | Add follows, blocks, denormalized counts, social/search indexes, and `pg_trgm`. |
| `V22` | Add explicit interest preferences and normalized genre index. |

## PostgreSQL-specific features

The schema uses features not faithfully reproduced by H2:

- partial indexes for public/unread/type-specific subsets;
- covering indexes with `INCLUDE`;
- expression indexes using `COALESCE`, `lower`, and `btrim`;
- GIN trigram indexes and `CREATE EXTENSION pg_trgm`;
- `timestamptz` and time-zone-aware migration expressions;
- `gen_random_uuid()` and temporary migration tables;
- `NOT VALID` foreign keys for preserved historical orphans;
- procedural guarded migrations using `DO $$` blocks.

Any change to these queries or migrations needs a PostgreSQL integration check in addition to the H2 unit/repository suite.

## Identity and integrity patterns

UUIDs are used across persisted domain entities. Compound IDs are used for follow and block edges. Important database guarantees include:

- one user/media library entry, rating, media like, and artwork preference;
- one user/review or user/list like;
- one media per list and stable positions;
- one media mapping per `(external source, external ID)` and at most one reference per `(media, source)`;
- one season number per series and episode number per season;
- one episode-level media identity per episode;
- exactly one subject for comments, award entries, award sync states, and interest targets;
- no self follow/block edges;
- consistent follow status/acceptance timestamp;
- grouped notification uniqueness by recipient and subject;
- idempotent imported activity/list identities.

Database constraints complement service checks. Service checks should still provide stable API errors instead of exposing constraint exceptions.

## Transactions

Services are the transaction boundary. General conventions:

- queries use `@Transactional(readOnly = true)`;
- mutations use `@Transactional` and return DTOs while entities are initialized;
- provider refresh persistence is isolated in transactional services so slow network calls do not hold the write transaction;
- notification SSE events use `AFTER_COMMIT`;
- each Letterboxd item applies in `REQUIRES_NEW` for partial success;
- optimistic `@Version` protects concurrent moderator changes to media and awards.

External provider reads sometimes occur during a larger service operation such as import. There is no distributed rollback for an already-completed provider request; only database mutations roll back.

## Index-oriented query behavior

Rankings and discovery rely on activity-oriented indexes rather than scanning arbitrary JSON or event logs. Social cursor ordering matches indexes on accepted/request timestamps plus user ID. Notifications order by `activityAt DESC, id DESC`. Recent list cover queries include `media_id` in the index to reduce heap access.

When changing sort order, cursor composition, visibility predicates, or activity timestamp selection, update the matching index and repository test together.

## Schema change checklist

1. Add the JPA mapping and a new PostgreSQL migration.
2. Preserve existing data explicitly; make nullability changes only after backfill.
3. Use deterministic names for important constraints and indexes.
4. Consider old schemas containing orphan rows and already-partial migrations.
5. Run the H2 tests for mapping/query regressions.
6. Apply migrations to an empty representative database and an upgraded copy of production-like data.
7. Inspect the schema after application startup for extra Hibernate changes.
8. Verify critical query plans when adding a new public discovery/social query.
