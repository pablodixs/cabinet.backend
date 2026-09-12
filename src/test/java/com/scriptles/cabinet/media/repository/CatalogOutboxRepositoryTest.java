package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.catalog.CatalogEventPayload;
import com.scriptles.cabinet.media.entity.CatalogOutboxEvent;
import com.scriptles.cabinet.media.enums.CatalogEventType;
import com.scriptles.cabinet.media.enums.CatalogOutboxStatus;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
class CatalogOutboxRepositoryTest {
    @Autowired
    private CatalogOutboxRepository repository;

    @Test
    void releasesOnlyProcessingEventsWhoseLockExpired() {
        Instant now = Instant.now();
        CatalogOutboxEvent stale = processingEvent(now.minus(Duration.ofMinutes(20)));
        CatalogOutboxEvent active = processingEvent(now.minus(Duration.ofMinutes(2)));
        repository.saveAllAndFlush(java.util.List.of(stale, active));

        int released = repository.releaseStaleProcessing(
                now.minus(Duration.ofMinutes(15)), now);

        assertThat(released).isEqualTo(1);
        CatalogOutboxEvent recovered = repository.findById(stale.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(CatalogOutboxStatus.RETRY);
        assertThat(recovered.getAvailableAt()).isCloseTo(now, within(1, ChronoUnit.MICROS));
        assertThat(recovered.getLockedAt()).isNull();
        assertThat(recovered.getLockedBy()).isNull();
        assertThat(repository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(CatalogOutboxStatus.PROCESSING);
    }

    private CatalogOutboxEvent processingEvent(Instant lockedAt) {
        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateId(UUID.randomUUID());
        event.setEventType(CatalogEventType.MEDIA_CORE_MATERIALIZED);
        event.setPayload(new CatalogEventPayload(
                ExternalSource.TMDB, "550", MediaType.MOVIE, "pt-BR"));
        event.setStatus(CatalogOutboxStatus.PROCESSING);
        event.setAvailableAt(lockedAt);
        event.setLockedAt(lockedAt);
        event.setLockedBy("test-worker");
        return event;
    }
}
