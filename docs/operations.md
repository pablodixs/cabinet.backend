# Operations

## Build artifact and container

`sh mvnw package` creates the Spring Boot executable JAR. The wrapper file is not executable in the current checkout, so the documentation invokes it through `sh`. The Dockerfile uses a two-stage build:

1. Maven 3.9.11/Temurin 21 downloads dependencies and packages with tests skipped.
2. Temurin 21 JRE Alpine runs `/app/app.jar` as the non-root `cabinet` user.

The entrypoint maps `PORT` to `server.port`, defaulting to `8080`.

## Required runtime dependencies

- Reachable PostgreSQL with permission to create/alter tables, indexes, constraints, and the `pg_trgm` extension when migrations run.
- Outbound HTTPS/DNS access to the configured providers for external functionality.
- Persistent application secrets supplied through the deployment environment.

The process has no local durable storage requirements. All durable application state is in PostgreSQL.

## Deployment checklist

- Set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.
- Set `SESSION_COOKIE_SECURE=true` when clients use HTTPS.
- Restrict `CORS_ALLOWED_ORIGIN_PATTERNS` to deployed client origins.
- Configure TMDB and any other provider credentials used by the product.
- Replace default MusicBrainz/Wikidata user agents with monitored contact details.
- Review `ADMIN_EMAILS` as privileged bootstrap configuration.
- Confirm the database user may apply Flyway migrations and `CREATE EXTENSION pg_trgm`, or pre-provision the extension with an administrator.
- Back up the database before migration.
- Apply the release to a staging copy and inspect both Flyway and Hibernate schema output.
- Size database pool and replica count together; each process can open up to five connections.
- Account for all replicas running cron jobs independently.

## Health and readiness

No Spring Boot Actuator dependency is declared, so `/actuator/health` and metrics endpoints do not exist. A platform TCP/HTTP check can verify the server is listening, but a robust readiness check should eventually include database connectivity without requiring external provider success.

The allowlisted `/error` path is not a health endpoint. Swagger/OpenAPI paths are also allowlisted but unavailable without an OpenAPI dependency.

## Logging and monitoring

Spring Boot default logging is used. The code emits warnings for provider/background failures and informational metrics for Letterboxd job duration and counts. Recommended production signals include:

- HTTP rate/error/latency by route group;
- Hikari active/pending/timeout counts;
- Flyway startup result;
- provider latency, `429`, timeout, and `502` counts;
- external-info and award snapshot error age;
- executor active threads, queue usage, and rejected tasks;
- Letterboxd jobs stuck in active states;
- scheduled-job last success and processed counts;
- SSE active connections, send failures, and limit rejections;
- notification unread/retention growth.

These are not currently exported as metrics; add Actuator/Micrometer or derive them from structured logs.

## Scaling

The API is stateful because authentication sessions and SSE emitters are process-local. Spring's default session storage is in-memory unless the platform adds a shared Spring Session implementation. With multiple replicas:

- use sticky sessions or add shared session storage;
- route an SSE connection consistently for its lifetime;
- accept that an event committed on one replica does not signal emitters on another;
- coordinate scheduled jobs or make their writes rigorously idempotent;
- expect provider refresh duplication because in-flight sets are local.

The PostgreSQL source of truth means clients recover by refetching REST data, but real-time hints and cron efficiency are best-effort until shared infrastructure is introduced.

## Backup and recovery

Back up the PostgreSQL database using the platform's normal snapshot/PITR tooling. Important state includes user accounts and hashes, catalog identities, community content, social edges, persistent notifications, moderation audits, import job state, and external snapshots.

Provider data can often be refreshed, but internal UUID identity, user content, audit records, follows, and imported source keys cannot be reconstructed reliably from providers. Test restore procedures and migration startup against a restored copy.

## Troubleshooting

### Application does not start: datasource

Check that all three required database environment variables are set and the JDBC URL starts with `jdbc:postgresql:`. Verify network reachability, credentials, TLS parameters if required, and the five-connection pool against database limits.

### Migration fails at `pg_trgm`

`V21` executes `CREATE EXTENSION IF NOT EXISTS pg_trgm`. Some managed databases require an administrator to enable it. Pre-enable the extension or grant the documented platform permission; do not silently skip the migration because user search indexes depend on it.

### Provider search returns `502`

Verify the provider credential/base URL, outbound DNS/TLS, user agent, and timeout. A provider rate limit returns `429`, not `502`. Wikidata enrichment can degrade gracefully, but the selected primary provider must succeed for external details/import.

### External-info remains pending or stale

Check `external-info-*` executor saturation, provider logs, required TMDB/MusicBrainz references, OMDb configuration, and the snapshot error/expiry rows. Failed refreshes intentionally wait about one hour before retry.

### Letterboxd job is stuck

Inspect the durable job state and executor logs. Restart resubmits `MATCHING` and `IMPORTING` jobs. `READY` requires user conflict resolution/confirmation. Old ready jobs are cancelled and payloads redacted after expiry.

### Notifications are persisted but not live

The SSE signal is process-local and after-commit. Confirm the client retained its session, has not exceeded connection limits, receives heartbeats, and refetches REST after reconnect. On multiple replicas, sticky routing alone does not propagate signals across replicas.

### Tests pass but PostgreSQL fails

The default tests use H2 and skip Flyway. Reproduce against PostgreSQL, especially for migrations, native queries, trigram/partial/covering indexes, time zones, and historical orphan constraints.

## Current production-hardening opportunities

- Replace Hibernate schema update with validation after establishing a complete Flyway baseline.
- Add Actuator/Micrometer health, readiness, and executor/database/provider metrics.
- Add shared session storage for multiple replicas.
- Add distributed job locking and durable outbox/queue processing.
- Add pub/sub notification invalidation or mobile push delivery.
- Add edge/application rate limits and abuse controls.
- Add PostgreSQL integration tests and migration tests in CI.
