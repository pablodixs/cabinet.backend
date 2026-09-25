# Getting Started

## Prerequisites

- JDK 21. The Maven wrapper downloads the repository's Maven distribution.
- PostgreSQL for a normal application run.
- Docker only if building or running the container image.
- Provider credentials for the external features you intend to use.

The application is configured exclusively through Spring configuration and environment variables. There is no committed development database or Compose stack.

## Required database configuration

These variables have no default and are required for startup:

| Variable | Example | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/cabinet` | JDBC connection URL |
| `DATABASE_USERNAME` | `cabinet` | Database user |
| `DATABASE_PASSWORD` | `cabinet` | Database password |

Create a database and credentials using your preferred PostgreSQL administration method. Then run:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:5432/cabinet'
export DATABASE_USERNAME='cabinet'
export DATABASE_PASSWORD='cabinet'
sh mvnw spring-boot:run
```

The default server port is `8080`. The Docker entrypoint also accepts `PORT` and defaults it to `8080`.

## Optional runtime configuration

| Variable | Default | Notes |
| --- | --- | --- |
| `FLYWAY_ENABLED` | `true` | Enables PostgreSQL migrations. Disabling it does not disable Hibernate schema update. |
| `SESSION_COOKIE_SECURE` | `true` | Keep enabled behind production HTTPS. The local development script overrides it to `false`. |
| `SESSION_COOKIE_SAME_SITE` | `none` | Supports the cross-site web client. The local development script overrides it to `lax`. |
| `SESSION_COOKIE_PARTITIONED` | `true` | Allows the cross-site web client to retain the session when third-party cookies are restricted. The local development script overrides it to `false`. |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `https://cabinetbeta.vercel.app,https://cabinet-hq.vercel.app` | Comma-separated patterns; credentials are enabled. The local development script allows `localhost` and `127.0.0.1`. |
| `ADMIN_EMAILS` | empty | Bootstrap admin emails. This overrides a persisted role at authentication time. |
| `SSE_MAX_CONNECTIONS_PER_USER` | `3` | Per-user in-memory SSE connection limit. |
| `SSE_MAX_TOTAL_CONNECTIONS` | `1000` | Process-wide SSE connection limit. |
| `TMDB_ACCESS_TOKEN` | empty | Preferred TMDB authentication method. |
| `TMDB_API_KEY` | empty | Alternative TMDB credential. |
| `OMDB_API_KEY` | empty | Enables imported movie/series external ratings. |
| `GOOGLE_BOOKS_API_KEY` | empty | Optional Google Books API key. |
| `MUSICBRAINZ_USER_AGENT` | Cabinet GitHub identifier | Use a real application/contact identifier in production. |
| `THEAUDIODB_API_KEY` | `123` | TheAudioDB key; currently part of configuration even though no dedicated client exists in the source tree. |
| `WIKIDATA_USER_AGENT` | Cabinet GitHub identifier | Identifies SPARQL requests. |
| `WIKIDATA_READ_TIMEOUT` | `30s` | Wikidata-specific read timeout. |
| `SUPABASE_URL` | empty | Project URL used by profile photo uploads. |
| `SUPABASE_SERVICE_ROLE_KEY` | empty | Server-only Storage key; never configure this in a web or mobile app. |
| `SUPABASE_AVATARS_BUCKET` | `avatars` | Existing public Storage bucket for profile photos. |

To enable profile photos, create a public Storage bucket named `avatars` in Supabase and set its file size limit to 5 MB with allowed MIME types `image/jpeg` and `image/png`. The backend uploads each image to a user-scoped path and saves its public URL to the user's profile. Public bucket access is required so profile photos can load in both apps. Keep `SUPABASE_SERVICE_ROLE_KEY` only in the backend environment.

Provider base URLs can also be overridden through Spring properties (`external.*.base-url` and `external.wikidata.sparql-url`), which is useful for integration tests or proxies.

## Build and test

```bash
sh mvnw clean test
sh mvnw package
java -jar target/cabinet-0.0.1-SNAPSHOT.jar
```

The tests use H2 and disable Flyway. Production-specific SQL, PostgreSQL indexes, partial indexes, and PostgreSQL enum behavior therefore need separate verification when changed.

## Docker

Build and run the multi-stage image:

```bash
docker build -t cabinet-backend .
docker run --rm -p 8080:8080 \
  -e DATABASE_URL='jdbc:postgresql://host.docker.internal:5432/cabinet' \
  -e DATABASE_USERNAME='cabinet' \
  -e DATABASE_PASSWORD='cabinet' \
  cabinet-backend
```

The build stage uses Maven 3.9.11 with Temurin 21. The runtime is a Temurin 21 Alpine JRE and runs as the non-root `cabinet` user.

## First authenticated request

Fetch a CSRF token, register or log in, and retain both the CSRF and session cookies. A browser client normally handles cookies automatically. A command-line client can use a cookie jar:

```bash
curl -c cookies.txt http://localhost:8080/v1/auth/csrf
```

Use the returned token in the header named by `headerName` for `POST`, `PUT`, `PATCH`, and `DELETE` requests. See [Security](security.md) for the complete flow.

## IDE setup

Enable annotation processing for Lombok. The Maven compiler plugin already configures Lombok as an annotation processor for main and test compilation. The application entry point is `com.scriptles.cabinet.CabinetApplication`.
