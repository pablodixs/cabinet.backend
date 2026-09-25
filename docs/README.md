# Cabinet Backend Documentation

This directory documents the backend as implemented in the current source tree. It is organized so that a new contributor can begin with setup, understand the architecture, then move into feature and API details.

## Reading paths

### New contributor

1. [Getting started](getting-started.md)
2. [Architecture](architecture.md)
3. [Feature modules](modules.md)
4. [Domain and data model](domain-model.md)
5. [Testing and development](testing.md)

### Client developer

1. [API reference](api-reference.md)
2. [Security](security.md)
3. [Media API details](media-api.md)
4. [Notifications and iOS push](notifications-and-ios-push.md)
5. [TypeScript media contracts](media-api.types.ts)

### Backend or operations engineer

1. [Architecture](architecture.md)
2. [Database](database.md)
3. [External integrations](external-integrations.md)
4. [Background processing](background-processing.md)
5. [Operations](operations.md)

## Document map

| Document | Scope |
| --- | --- |
| [Getting started](getting-started.md) | Local environment, configuration, Maven, Docker |
| [Architecture](architecture.md) | System shape, layers, boundaries, major request flows |
| [Feature modules](modules.md) | Behavior and ownership of every top-level Java package |
| [API reference](api-reference.md) | Complete route inventory and common HTTP contracts |
| [Domain and data model](domain-model.md) | Entities, relationships, enums, invariants, state transitions |
| [Security](security.md) | Authentication, session, CSRF, CORS, authorization |
| [External integrations](external-integrations.md) | TMDB, MusicBrainz, Google Books, Wikidata, OMDb, artwork |
| [Background processing](background-processing.md) | Executors, domain events, scheduled jobs, stale-while-revalidate |
| [Database](database.md) | JPA mappings, migrations, indexing, transactions |
| [Testing and development](testing.md) | Test strategy, commands, conventions, change checklist |
| [Operations](operations.md) | Image build, runtime behavior, deployment checklist, troubleshooting |
| [Media API details](media-api.md) | Detailed media search, import, relations, awards, and external info behavior |
| [Notifications and iOS push](notifications-and-ios-push.md) | Notification inbox/SSE, ready FCM backend infrastructure, and deferred iOS push activation |

## Source of truth

The Java source and `application.yaml` are the final source of truth. This documentation intentionally describes both stable functionality and recently added modules visible in the working tree. When behavior changes, update the closest focused document and the API inventory if a route changed.

OpenAPI routes are allowlisted by security configuration, but no OpenAPI dependency is currently declared in `pom.xml`; `/swagger-ui/**` and `/v3/api-docs/**` are therefore not available unless such a dependency is added.
