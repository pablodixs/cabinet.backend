# Database egress mitigation

## Temporary catalog-job settings

The application defaults keep TMDB catalog backfill disabled and bound enabled backfills to a batch size of 1 and 20 active jobs. Production can make the pause explicit with:

```text
CATALOG_TMDB_BACKFILL_ENABLED=false
```

If backfill must continue, keep the bounded defaults or set `CATALOG_TMDB_BACKFILL_BATCH_SIZE=1` and `CATALOG_TMDB_BACKFILL_MAX_ACTIVE_JOBS=20`. User syncs, ratings, and likes do not use this switch. Restore higher throughput only after reviewing daily query rows and Supabase egress.

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

Use daily deltas rather than comparing cumulative totals from different reset windows. Compare periods with similar request volume and track recommendation candidates and loaded-credit counters alongside the SQL totals. The admin-only `/v1/admin/database-efficiency` view reports query rows/calls/time when the application database role can read `pg_stat_statements`.

In Supabase Usage, compare **Database Egress**, **Shared Pooler Egress**, **Storage Egress**, and **Cached Egress** on matching date ranges. The application cannot read those account-level usage totals, so they remain a dashboard check.

## Completion target

Keep the mitigation in place until comparable daily windows show a sustained reduction of at least 90% in `media_credits` rows, with recommendation, interest, report, and detail flows behaving correctly. Re-enable backfill gradually while watching the same counters and egress categories.
