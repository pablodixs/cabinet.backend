package com.scriptles.cabinet.catalog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Gradually turns the daily TMDB identity index into canonical collections. */
@Component
@Slf4j
public class TmdbCollectionBackfillScheduler {
    private final JdbcTemplate jdbc;
    private final CatalogJobOrchestrationService orchestration;
    private final boolean enabled;
    private final int batchSize;
    private final int maxActiveJobs;
    private final String locale;

    public TmdbCollectionBackfillScheduler(JdbcTemplate jdbc, CatalogJobOrchestrationService orchestration,
            @Value("${catalog.tmdb.backfill.enabled:true}") boolean enabled,
            @Value("${catalog.tmdb.backfill.batch-size:10}") int batchSize,
            @Value("${catalog.tmdb.backfill.max-active-jobs:500}") int maxActiveJobs,
            @Value("${catalog.collections.tmdb-sync.locale:pt-BR}") String locale) {
        this.jdbc = jdbc;
        this.orchestration = orchestration;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
        this.maxActiveJobs = Math.max(1, maxActiveJobs);
        this.locale = locale;
    }

    @Scheduled(fixedDelayString = "${catalog.tmdb.backfill.poll-delay:1m}")
    public void dispatch() {
        if (!enabled) return;
        Long active = jdbc.queryForObject("""
                select count(*) from catalog_jobs where status in ('PENDING', 'PROCESSING', 'RETRY')
                """, Long.class);
        int capacity = active >= maxActiveJobs ? 0 : Math.min(batchSize, (int) (maxActiveJobs - active));
        if (capacity == 0) return;

        // A successful hydration has a snapshot. Any previous hydration job, including DEAD,
        // is excluded so a permanent failure stays visible for moderator retry.
        List<String> ids = jdbc.query("""
                select e.external_id from external_catalog_entities e
                where e.provider = 'TMDB' and e.entity_type = 'COLLECTION' and e.state = 'ACTIVE'
                  and not exists (
                    select 1 from collection_external_references r
                    join collection_source_snapshots s on s.collection_id = r.collection_id
                      and s.provider = 'TMDB'
                    where r.provider = 'TMDB' and r.external_id = e.external_id
                  )
                  and not exists (
                    select 1 from catalog_jobs j
                    where j.provider = 'TMDB' and j.entity_type = 'COLLECTION'
                      and j.external_id = e.external_id
                      and j.job_type = 'TMDB_COLLECTION_HYDRATE'
                  )
                order by e.external_id
                limit ?
                """, (rs, row) -> rs.getString(1), capacity);
        for (String id : ids) {
            try {
                orchestration.backfillCollection(id, locale);
            } catch (DataIntegrityViolationException race) {
                // Another instance enqueued the same collection first.
                log.debug("TMDB collection {} was already queued", id);
            } catch (RuntimeException failure) {
                log.warn("Unable to queue TMDB collection backfill {}: {}", id, failure.getMessage());
            }
        }
    }
}
