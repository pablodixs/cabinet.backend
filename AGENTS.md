# AGENTS.md

## Scope

These instructions apply to the entire `cabinet.backend` repository.

Cabinet Backend is the authoritative HTTP API and durable domain layer for Cabinet. It is a Java 21 / Spring Boot 4.1 modular monolith backed by PostgreSQL. Changes must preserve API compatibility with the Web and iOS clients unless the task explicitly includes coordinated client changes.

## Read before changing code

Use the repository itself as the source of truth. Start with:

- `README.md` for the product and repository overview.
- `docs/architecture.md` for module boundaries and request flows.
- `docs/domain-model.md` before changing entities, relationships, visibility, or state.
- `docs/api-reference.md` before changing an HTTP contract.
- `docs/security.md` for session, CSRF, CORS, and authorization behavior.
- `docs/external-integrations.md` for provider behavior.
- `docs/background-processing.md` for schedulers, workers, retries, and asynchronous work.
- `docs/database.md` before changing persistence or migrations.
- `docs/testing.md` for the verification strategy.
- `src/main/resources/application.yaml` for current runtime configuration.

When documentation and executable configuration disagree, prefer the current code/configuration and update stale documentation as part of the change.

## Technology baseline

- Java 21.
- Spring Boot 4.1.
- Spring MVC.
- Spring Security with server-side session authentication.
- Spring Data JPA / Hibernate.
- PostgreSQL in production.
- Flyway migrations.
- Caffeine for local caching.
- Spring Boot Actuator for health/info/metrics/caches.
- Maven Wrapper; use `sh mvnw ...` rather than assuming a global Maven installation.
- H2 exists only for the test suite and is not equivalent to PostgreSQL.

## Architecture

Cabinet is a modular monolith. Keep work inside the owning module whenever practical.

`src/main/java/com/scriptles/cabinet/` contains the major boundaries:

- `auth` — login, logout, current session, CSRF.
- `security` — filter chain, principals, roles, CORS.
- `user` — accounts, profiles, library, diary, social graph, imports, interests.
- `media` — catalog, providers, credits, people, ratings, reviews, moderation, collections, enrichment.
- `lists` — user lists, membership, discovery, likes.
- `comments` — comments on supported community resources.
- `notifications` — persistent notifications and realtime refresh signals.
- `common` — shared API primitives, exceptions, and time helpers.

Do not introduce a new top-level module merely to avoid understanding the existing ownership. Prefer extending the closest existing domain.

## Layering rules

### Controllers

Controllers define HTTP contracts. Keep them thin.

They may:

- parse and validate request input;
- obtain the authenticated principal;
- call application/domain services;
- select the correct HTTP status;
- return request/response DTOs.

They should not contain persistence orchestration, provider-specific branching, authorization policy, or business state transitions.

All public API routes belong under `/v1` unless the task explicitly establishes a new API version.

### Services

Services own business rules, resource authorization, orchestration, and transactional boundaries.

- Use `@Transactional(readOnly = true)` for read paths where appropriate.
- Use `@Transactional` for writes.
- Preserve resource-level visibility, ownership, follow/block, release, and moderation rules.
- Return stable domain failures through the existing `ApiException` conventions instead of ad-hoc exceptions.
- Avoid network calls while holding database locks when the workflow can be structured differently.

### Repositories

Repositories own database queries and projections.

- Prefer clear derived queries or explicit JPQL/native queries over loading large datasets and filtering in Java.
- Treat ranking, discovery, social feeds, and cursor pagination as performance-sensitive.
- Add or adjust PostgreSQL indexes when a new query pattern requires them.
- Do not assume H2 proves PostgreSQL-specific SQL or query-plan behavior.

### DTOs

Keep persistence entities out of public API contracts.

- Follow the module's existing request/response DTO placement.
- Preserve JSON field names and nullability consumed by Web and iOS.
- When changing a shared response, search both client repositories for affected fields before finalizing the backend change.

## Identity and media rules

Persisted resources use internal UUIDs.

External catalog identity is based on the source/media-type/external-id tuple. Preserve that distinction:

- external previews may exist before Cabinet persists the media;
- imports must be idempotent;
- once imported, the internal UUID is the preferred durable identifier;
- provider-specific objects must be normalized behind provider interfaces/registries;
- do not leak provider-specific response objects through Cabinet's public API.

When adding a provider, extend the existing provider abstractions and registry routing instead of adding provider conditionals throughout controllers or unrelated services.

## Authentication and security

Cabinet uses a `CABINET_SESSION` cookie.

- Mutating browser/client requests require the CSRF flow exposed by `GET /v1/auth/csrf`.
- Do not weaken CSRF, CORS, cookie, or method-authorization behavior to make a client bug disappear.
- Public endpoints that support viewer-specific state may accept an optional authenticated principal.
- Authorization belongs in services/policies as well as route configuration; route protection alone is not sufficient for resource ownership.
- Never log passwords, session cookies, CSRF tokens, provider secrets, Supabase service-role keys, or raw authorization credentials.
- Do not hard-code production secrets.

## Database and Flyway

Production configuration currently uses Hibernate `ddl-auto=validate`. Flyway owns schema evolution.

Migration files live in:

`src/main/resources/db/migration/postgresql/`

Rules:

1. Never edit a migration that may already have run in a shared environment. Add a new migration.
2. Make migrations safe for an upgrade from the current production schema, not only for a fresh database.
3. Preserve data unless destructive behavior is explicitly required.
4. Consider lock duration and table size for indexes, constraints, and backfills.
5. Verify PostgreSQL-specific changes against PostgreSQL; H2 is insufficient.
6. Keep entity mappings and migrations consistent so `ddl-auto=validate` succeeds.
7. If a migration changes a public domain model, update the relevant docs.

For large metadata backfills, prefer resumable/idempotent jobs over a startup migration that performs remote calls.

## External providers

Cabinet integrates with services such as TMDB, MusicBrainz, Google Books, Wikidata, OMDb, TheAudioDB, and Supabase-backed avatar storage.

Provider code must handle:

- missing credentials;
- empty/missing provider fields;
- localization;
- timeouts;
- rate limits;
- non-2xx responses;
- malformed upstream payloads;
- retry safety;
- provider outages without corrupting durable Cabinet data.

Do not call live public providers from deterministic tests.

Preserve stale-while-revalidate/cached behavior where it already exists. Avoid turning a read endpoint into a chain of synchronous external calls unless the contract explicitly requires that latency.

## Background work and concurrency

The codebase contains schedulers, event-driven work, catalog synchronization, outbox-style processing, enrichment, imports, and process-local caches.

For new background work:

- make repeated execution safe;
- make retries safe;
- model progress/failure when the work is user-visible or operationally important;
- prevent hot retry loops;
- consider application restarts;
- assume more than one API instance can eventually run;
- do not rely only on an in-memory set for correctness;
- emit after-commit work only after the transaction is durably committed;
- use bounded concurrency for provider calls.

Use `America/Sao_Paulo` / existing `CabinetTime` conventions for business-date behavior rather than scattering `LocalDate.now()` with implicit host timezone semantics.

## API behavior

Preserve the established conventions:

- page responses: `{ items, page, size, totalElements, totalPages }`;
- cursor responses: `{ items, nextCursor, hasMore }`;
- error responses: `{ code, message, fieldErrors }`;
- opaque cursors stay opaque to clients;
- status codes are part of the contract;
- external-source enums and media-type enums are serialized consistently.

If an endpoint can legitimately return stale/pending provider-backed data, follow the existing feature behavior instead of inventing a one-off polling contract.

## Performance expectations

Before adding queries or provider calls to a hot path, consider:

- N+1 JPA access;
- unbounded collections;
- missing indexes;
- eager loading;
- repeated provider lookups;
- serialization size;
- transaction duration;
- connection-pool pressure;
- duplicate asynchronous work.

The configured Hikari pool is intentionally small. Do not design a request path that holds a database connection while waiting on slow external work unnecessarily.

## Testing

Primary commands:

~~~bash
sh mvnw test
sh mvnw -DskipTests compile
sh mvnw clean package
~~~

Run a focused test with:

~~~bash
sh mvnw -Dtest=SocialGraphServiceTest test
sh mvnw -Dtest=SocialGraphServiceTest#followPublicProfile test
~~~

Minimum verification by change type:

- Controller/API change: controller test, validation, authentication, CSRF, status code, JSON shape.
- Service rule: happy path plus authorization/visibility/error branches.
- Repository query: repository-level test; PostgreSQL verification for native/PostgreSQL-specific behavior.
- Migration/entity: fresh schema and upgrade-path verification on PostgreSQL.
- Provider: success, empty, malformed, timeout/rate-limit/server-error behavior.
- Scheduler/worker: idempotency, retry/failure state, duplicate execution, restart/multi-instance assumptions.
- Security: explicitly test unauthorized/forbidden behavior, not only success.

Do not delete or relax a meaningful test just to make a change pass.

## Documentation maintenance

Update documentation when behavior changes.

At minimum:

- controllers/routes -> `docs/api-reference.md`;
- entities/state/visibility -> `docs/domain-model.md`;
- provider/config/cache behavior -> `docs/external-integrations.md`;
- schedulers/executors/retries -> `docs/background-processing.md`;
- persistence/migrations -> `docs/database.md`;
- security/auth/CORS/CSRF -> `docs/security.md`.

## Change discipline

Prefer the smallest coherent change that fully solves the task.

Do not:

- rewrite unrelated modules;
- change public contracts accidentally;
- bypass a service by writing directly through a repository from a controller;
- add a second abstraction that duplicates an existing provider/policy/resolver;
- commit generated build output or secrets;
- silently swallow domain errors;
- introduce an in-memory cache as the only source of truth for durable state.

When fixing a bug, identify the violated invariant and add a regression test at the narrowest useful layer.

## Definition of done

Before considering a task complete:

1. The code follows the owning module and existing abstractions.
2. Authorization, visibility, session, and CSRF behavior remain correct.
3. Database changes include a safe Flyway migration when needed.
4. Cross-client API impact has been considered.
5. Relevant focused tests pass.
6. The full test suite passes when practical.
7. PostgreSQL-specific behavior has been verified when applicable.
8. Documentation is updated for externally visible or architectural changes.
9. No secrets, debug dumps, temporary scripts, or generated artifacts were added accidentally.
