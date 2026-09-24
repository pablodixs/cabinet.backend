package com.scriptles.cabinet.status;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OperationalMetrics {
    private final JdbcTemplate jdbc;
    private final AtomicReference<Double> communityLagSeconds = new AtomicReference<>(0d);
    private final Map<String, QueueSnapshot> queues = Map.of(
            "domain", new QueueSnapshot(),
            "catalog", new QueueSnapshot()
    );

    public OperationalMetrics(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        queues.forEach((name, snapshot) -> {
            gauge(meters, "cabinet.outbox.pending", name, snapshot.pending);
            gauge(meters, "cabinet.outbox.retry", name, snapshot.retry);
            gauge(meters, "cabinet.outbox.dead", name, snapshot.dead);
            Gauge.builder("cabinet.outbox.oldest.seconds", snapshot.oldestSeconds, AtomicReference::get)
                    .tag("queue", name).register(meters);
        });
        Gauge.builder("cabinet.community.lag.seconds", communityLagSeconds, AtomicReference::get)
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${cabinet.metrics.snapshot-delay:30000}", initialDelay = 10000)
    public void refreshDatabaseGauges() {
        refreshQueue("domain", "domain_outbox_events");
        refreshQueue("catalog", "catalog_outbox");
        Double lag = jdbc.queryForObject("""
                select coalesce(extract(epoch from current_timestamp - min(marked_at)), 0)
                from media_community_stats_dirty
                """, Double.class);
        communityLagSeconds.set(lag == null ? 0d : lag);
    }

    private void refreshQueue(String name, String table) {
        QueueCounts counts = jdbc.queryForObject("""
                select count(*) filter (where status = 'PENDING'),
                       count(*) filter (where status = 'RETRY'),
                       count(*) filter (where status = 'DEAD'),
                       coalesce(extract(epoch from current_timestamp -
                           min(created_at) filter (where status in ('PENDING', 'RETRY'))), 0)
                from %s
                where status in ('PENDING', 'RETRY', 'PROCESSING', 'DEAD')
                """.formatted(table), (rs, rowNum) -> new QueueCounts(
                rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getDouble(4)));
        QueueSnapshot snapshot = queues.get(name);
        snapshot.pending.set(counts.pending());
        snapshot.retry.set(counts.retry());
        snapshot.dead.set(counts.dead());
        snapshot.oldestSeconds.set(counts.oldestSeconds());
    }

    private void gauge(MeterRegistry meters, String name, String queue, AtomicLong value) {
        Gauge.builder(name, value, AtomicLong::doubleValue).tag("queue", queue).register(meters);
    }

    private static class QueueSnapshot {
        private final AtomicLong pending = new AtomicLong();
        private final AtomicLong retry = new AtomicLong();
        private final AtomicLong dead = new AtomicLong();
        private final AtomicReference<Double> oldestSeconds = new AtomicReference<>(0d);
    }

    private record QueueCounts(long pending, long retry, long dead, double oldestSeconds) {}
}
