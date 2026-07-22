# Cabinet Backend

Cabinet is a social media catalog and tracking API built with Java 21, Spring Boot 4, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, and Flyway. It combines an internally persisted catalog with external metadata providers and community features such as ratings, reviews, lists, diaries, follows, recommendations, comments, moderation, and real-time notification signals.

The application is a modular monolith. All features run in one Spring Boot process and use one PostgreSQL database, while the Java packages define the module boundaries.

## Documentation

Start with the [documentation index](docs/README.md). The main guides are:

- [Getting started](docs/getting-started.md) — prerequisites, environment variables, local execution, Docker, and common commands.
- [Architecture](docs/architecture.md) — layers, module boundaries, request flow, asynchronous work, and design decisions.
- [Feature modules](docs/modules.md) — responsibilities and business behavior of every application module.
- [API reference](docs/api-reference.md) — all HTTP route groups, access requirements, parameters, and payload conventions.
- [Domain and data model](docs/domain-model.md) — entities, relationships, state machines, visibility, and important invariants.
- [Security](docs/security.md) — session authentication, CSRF, CORS, roles, and public routes.
- [External integrations](docs/external-integrations.md) — provider routing, configuration, caching, and failure behavior.
- [Background processing](docs/background-processing.md) — schedulers, executors, events, retries, and single-instance assumptions.
- [Database](docs/database.md) — persistence conventions, Flyway, schema evolution, and transaction boundaries.
- [Testing and development](docs/testing.md) — test layout, H2 behavior, and verification commands.
- [Operations](docs/operations.md) — build, deployment, runtime configuration, health considerations, and troubleshooting.

Detailed feature documents that predate this overview remain authoritative for their narrower scope:

- [Media API details](docs/media-api.md)
- [Notification behavior and proposed iOS push architecture](docs/notifications-and-ios-push.md)
- [TypeScript media contracts](docs/media-api.types.ts)

## Quick start

Create a PostgreSQL database, export the required connection variables, and start the application:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:5432/cabinet'
export DATABASE_USERNAME='cabinet'
export DATABASE_PASSWORD='cabinet'
sh mvnw spring-boot:run
```

The API listens on port `8080` by default. External provider credentials are optional for application startup but are required for the related search and enrichment features. See [Getting started](docs/getting-started.md) for the full configuration.

Run the test suite with:

```bash
sh mvnw test
```

## Repository layout

```text
src/main/java/com/scriptles/cabinet/
├── auth/           session login and logout
├── comments/       comments on lists and reviews
├── common/         shared API and time primitives
├── lists/          user-created media lists and list likes
├── media/          catalog, providers, ratings, reviews, awards, and moderation
├── notifications/  persistent inbox and SSE invalidation stream
├── security/       Spring Security configuration and principals
└── user/           accounts, library, diary, social graph, imports, and interests

src/main/resources/
├── application.yaml
└── db/migration/postgresql/
```

## API conventions at a glance

- All routes are versioned under `/v1`.
- Public reads and authentication entry points are explicitly allowlisted; all other routes require a `CABINET_SESSION` cookie.
- Mutating requests require the CSRF token returned by `GET /v1/auth/csrf`.
- Page-number responses use `{ items, page, size, totalElements, totalPages }`.
- Cursor responses use `{ items, nextCursor, hasMore }`; cursors are opaque.
- Errors use `{ code, message, fieldErrors }`.
- UUIDs identify persisted resources. External catalog entries use `(source, mediaType, externalId)`.

## Important implementation note

Production persistence currently uses both Flyway and `spring.jpa.hibernate.ddl-auto=update`. Existing migrations primarily describe incremental schema changes, while Hibernate can still create or alter mappings. Read [Database](docs/database.md) before changing entities or deploying multiple versions.
