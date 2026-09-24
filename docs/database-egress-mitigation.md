# Database egress mitigation

## Temporary catalog-job settings

The application defaults keep TMDB catalog backfill disabled and bound enabled backfills to a batch size of 1 and 20 active jobs. Production on the VPS can make the pause explicit with:

```text
CATALOG_TMDB_BACKFILL_ENABLED=false
```

If backfill must continue, keep the bounded defaults or set `CATALOG_TMDB_BACKFILL_BATCH_SIZE=1` and `CATALOG_TMDB_BACKFILL_MAX_ACTIVE_JOBS=20`. User syncs, ratings, and likes do not use this switch. Restore higher throughput only after reviewing daily query rows and database I/O on the VPS.

## Baseline and daily comparison

The baseline supplied for this investigation is 43,852 calls and 12,118,267 rows for statements containing `media_credits`, accumulated after the PostgreSQL statistics reset on 2026-09-09. Capture a timestamped snapshot before rollout and at the same time each day after rollout:

```sql
SELECT sum(calls) AS calls,
       sum(rows) AS rows,
       sum(total_exec_time) AS total_exec_time_ms
FROM pg_stat_statements
WHERE query ILIKE '%media_credits%';

SELECT stats_reset FROM pg_stat_statements_info;
```

Use daily deltas rather than comparing cumulative totals from different reset windows. Compare periods with similar request volume and track recommendation candidates and loaded-credit counters alongside the SQL totals. The admin-only `/v1/admin/database-efficiency` view reports the ten highest-cost queries across the current database by rows, calls, total execution time, and average execution time when the application role can read `pg_stat_statements`. It also reports the statistics reset time. SQL text is limited to 240 characters and should remain admin-only.

The same view reports Micrometer search timings for the service as a whole, TMDB, MusicBrainz, Google Books, result enrichment, and rating searches. Values are cumulative since the process started; a restart resets them. External provider calls overlap, so their averages must not be added together. Hikari active, idle, and pending connection gauges help identify pool contention. PostgreSQL query statistics measure database execution, while the search timers measure application and external-provider latency. For a production incident, compare both views over the same period.

If `pg_stat_statements` is unavailable to the application role, the endpoint still returns search and pool metrics and marks PostgreSQL statistics unavailable. Check whether the extension is enabled on the VPS PostgreSQL instance and whether the application role can read it; keep the admin endpoint restricted.

The application database is PostgreSQL on the VPS. Supabase Storage is used for profile avatars and its usage metrics do not measure Cabinet database queries. Compare `pg_stat_statements` snapshots with VPS CPU, memory, disk I/O, and PostgreSQL connection metrics over matching periods.

## Completion target

Keep the mitigation in place until comparable daily windows show a sustained reduction of at least 90% in `media_credits` rows, with recommendation, interest, report, and detail flows behaving correctly. Re-enable backfill gradually while watching the same counters and egress categories.
