# Testing and Development

## Test stack

The Maven build includes Spring Boot JPA, MVC, Security, and validation test starters, Mockito through Spring's test support, and H2. Test configuration disables Flyway and sets Hibernate to avoid native enum preference.

Tests are under `src/test/java/com/scriptles/cabinet` and mirror application modules. The current suite covers:

- application context startup;
- authentication service behavior;
- comments and notification rules;
- list controllers, services, likes, search repositories, and access;
- media controllers, provider clients, repositories, import/enrichment, rankings, reviews, moderation, awards, artwork, and release policy;
- profile and community security;
- diary, library, episodes, profiles, social graph/cursors/access policy;
- Letterboxd parsing;
- interest graph, scoring, recommendations, and controller behavior.

## Commands

Run the full suite:

```bash
sh mvnw test
```

Compile without tests:

```bash
sh mvnw -DskipTests compile
```

Run one class or method:

```bash
sh mvnw -Dtest=SocialGraphServiceTest test
sh mvnw -Dtest=SocialGraphServiceTest#followPublicProfile test
```

Build the executable artifact:

```bash
sh mvnw clean package
```

Surefire reports are written to `target/surefire-reports`.

## Test categories

### Service unit tests

Mock repositories/providers and assert state transitions, visibility, policy errors, persistence calls, and response mapping. Use these for branching business behavior and provider degradation.

### Controller tests

Use MVC/security test support to verify routes, validation, status codes, authentication, CSRF, method authorization, and JSON contracts. Mutating controller tests should include CSRF unless the test intentionally asserts rejection.

### Repository tests

Exercise JPQL/native query semantics, projections, ordering, uniqueness, and pagination. H2 is useful for most JPA behavior but not a substitute for PostgreSQL-specific syntax and query plans.

### External client tests

Stub HTTP responses and verify provider authentication, URL/query construction, parsing, pagination, localization, missing fields, rate limits, and transport errors. Never call public providers from the deterministic test suite.

## H2 limitations

Flyway is disabled in tests, so the suite does not execute any PostgreSQL migration. H2 also differs on partial/covering/trigram indexes, `NOT VALID` constraints, time zones, JSON/text behavior, and some SQL functions. For migration or native-query work, create an additional PostgreSQL verification environment.

A practical pre-merge database check is:

1. start a disposable PostgreSQL instance;
2. run the application with Flyway enabled and `ddl-auto=update` as production currently does;
3. inspect Flyway history and startup schema logs;
4. exercise the affected repository/API path;
5. repeat against a schema upgraded from the previous application version.

## Change-oriented test checklist

### New endpoint

- Public/protected behavior in `SecurityConfig`.
- Authentication and CSRF cases.
- Request validation boundaries.
- Success status and response body.
- Ownership/visibility/block cases.
- Stable API error code.
- Documentation route table.

### New entity or migration

- Mapping/context test.
- Constraint and repository behavior.
- PostgreSQL migration from previous state.
- Empty-database startup.
- Index/query-plan impact for discovery paths.

### New provider behavior

- Supported type/source routing.
- Success, empty, malformed, timeout, rate-limit, and server-error responses.
- Missing credential behavior.
- Import idempotency and optional enrichment failure.
- Cache/TTL behavior where applicable.

### New scheduled/background behavior

- The transactional event fires only after commit.
- Duplicate/ineligible state is ignored.
- Retry/error state prevents hot loops.
- Restart recovery or an explicit statement that work is best-effort.
- Multi-instance idempotency.

## Development conventions inferred from the codebase

- Put API DTOs in module `dto/request` and `dto/response` packages; comments/notifications use a flatter DTO package.
- Keep controller methods thin and services transactional.
- Prefer typed enums and stable `ApiException` codes for domain failures.
- Use response assemblers/resolvers for viewer-dependent artwork and community state.
- Keep external provider data normalized behind interfaces.
- Add repository indexes for public ranking/search and cursor order.
- Emit real-time notification signals after commit and keep REST durable.
- Use `CabinetTime.today()` for business-date release decisions.

## Documentation maintenance

When a controller changes, update [API reference](api-reference.md). When an entity/state changes, update [Domain and data model](domain-model.md). Provider and TTL changes belong in [External integrations](external-integrations.md), while schedules/executors belong in [Background processing](background-processing.md).
