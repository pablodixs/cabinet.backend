# Security

## Authentication model

Cabinet uses stateful Spring Security sessions. It does not use JWTs or HTTP Basic authentication.

On successful login, `AuthService`:

1. authenticates the email/username and password through `AuthenticationManager`;
2. changes the HTTP session ID to prevent session fixation;
3. stores the `SecurityContext` through `HttpSessionSecurityContextRepository`;
4. ensures a CSRF token is created;
5. returns a client-safe account representation.

The session cookie is named `CABINET_SESSION`, is HTTP-only, has `SameSite=Lax`, lasts seven days, and uses the `Secure` flag when `SESSION_COOKIE_SECURE=true`. URL rewriting of session IDs is disabled.

Passwords are hashed with BCrypt. The authenticated principal contains account ID, email, username/display name, password hash, authorities, active flag, role, and tier. Its Spring Security username is the email, although login lookup accepts email or username.

## CSRF

CSRF is enabled using `CookieCsrfTokenRepository`. Before any state-changing request:

```http
GET /v1/auth/csrf
```

The response returns the token and required header name. Send that header and both CSRF/session cookies on `POST`, `PUT`, `PATCH`, and `DELETE`. The CSRF endpoint itself is public so a client can bootstrap before login or registration.

CSRF failures are handled by Spring Security's access-denied flow. Browser and mobile clients must preserve cookies between the token request and mutation.

## Authorization levels

### User

Every route not explicitly allowlisted requires an authenticated, active principal. Resource services then enforce ownership and visibility; a session alone does not grant access to another user's private list, review, diary, import, or graph management.

### Moderator

`MODERATOR` receives `ROLE_USER` and `ROLE_MODERATOR`. `ADMIN` receives all three roles. Moderator controllers use method expressions such as:

```java
@PreAuthorize("@communityAuthorization.isModerator(authentication)")
```

The authorization component reloads the user from the database for each check, so demotion or deactivation applies without waiting for the current HTTP session to expire.

### Admin

Only effective admins can manage roles and account tiers. Emails listed in `ADMIN_EMAILS` are treated as admins at authentication/authorization time even if the stored role is lower. This is intended as bootstrap access; keep the variable narrowly scoped and protected.

## Public allowlist

The filter chain permits:

- login, registration aliases, CSRF, and `/error`;
- public reads for external/persisted media, search, rankings, credits, external info, awards, seasons, lists, reviews, comments, people/artists, public user search/profiles/social lists/activity/diary;
- Swagger/OpenAPI URL patterns, if an OpenAPI dependency is later installed.

Everything else is authenticated. A subtle consequence is that `GET /v1/users/{username}/summary` is authenticated, while the fuller `/profile` route is public and visibility-aware. `POST /v1/users/create` is also authenticated; public clients should use `/v1/auth/register`.

## CORS

CORS allows credentials, all headers, methods `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, and `OPTIONS`, with a one-hour preflight cache. Allowed origin patterns come from comma-separated `CORS_ALLOWED_ORIGIN_PATTERNS`.

Default patterns allow local hosts on any port. Production must explicitly set trusted HTTPS origins. Because credentials are enabled, avoid broad wildcard patterns.

## Visibility and blocking

Content authorization is not expressed solely through URL security:

- `PUBLIC` content is broadly readable unless blocked.
- `FOLLOWERS` content requires an accepted follow or ownership.
- `PRIVATE` content requires ownership.
- User blocks prevent profile/social/content access and sever accepted or pending follows.
- List and review comments inherit access from their subject.
- Likes require access to the subject and cannot be used to probe private resources.
- Aggregates and discovery queries filter private content rather than returning it and relying on the client.

## Error behavior

Unauthenticated protected access returns HTTP `401`:

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "Faça login para continuar",
  "fieldErrors": {}
}
```

Forbidden access returns `403` with code `ACCESS_DENIED`. Invalid login returns `401` with `INVALID_CREDENTIALS`. Resource-level access failures may intentionally use `404` to avoid disclosing private resource existence, depending on the service.

## Security checklist

- Set `SESSION_COOKIE_SECURE=true` in HTTPS environments.
- Terminate TLS at a trusted proxy and ensure forwarded-header behavior matches the deployment platform.
- Restrict CORS to known client origins.
- Protect database and provider credentials as secrets.
- Keep `ADMIN_EMAILS` empty after persisted admin roles are established, or audit it as privileged configuration.
- Do not log login request bodies, cookies, CSRF tokens, import payloads, or provider keys.
- Review public allowlist patterns whenever adding a controller; broad `*` patterns can unintentionally expose a sibling endpoint.
- Add rate limiting at the edge for login, registration, provider proxy routes, comments, and reports; no application-level general rate limiter is present.
