# Local iOS development

Prerequisites on this Mac: `brew install openjdk@21 postgresql@17`.

From the backend folder, run:

```sh
sh scripts/dev.sh
```

The script starts an isolated PostgreSQL cluster in `.local/postgres` on loopback port 55432 and the API on port 8080. The development database uses trust authentication and must remain local. Data survives backend restarts. Stop the API with Ctrl-C; stop the database separately with:

```sh
/opt/homebrew/opt/postgresql@17/bin/pg_ctl -D .local/postgres stop
```

The iOS Debug build defaults to `http://localhost:8080` for Simulator. To use another server, set `APIBaseURL` in the app's Info.plist. Release builds require an explicit URL; use HTTPS for deployment. A physical iPhone needs a reachable Mac LAN address instead of localhost and local-network permission configuration.

The app restores `/v1/auth/me` at launch. Sign In uses `/v1/auth/login`; Sign Out uses `/v1/auth/logout`. Before mutations it fetches `/v1/auth/csrf`; URLSession retains the CSRF and session cookies. A 401 during restoration is the normal signed-out state.

External provider credentials (such as `TMDB_ACCESS_TOKEN`) can be exported before running the script. They belong in the backend environment, never in the iOS app.

The local script disables Flyway and uses Hibernate schema update because the migration history lacks the initial tables. This supports local app development but does not validate production migrations or migration-only indexes. Production configuration is unchanged.
