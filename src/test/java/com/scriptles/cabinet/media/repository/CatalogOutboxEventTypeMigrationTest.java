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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CatalogOutboxEventTypeMigrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private CatalogOutboxRepository repository;

    @Test
    void migrationAllowsAlbumReleaseVersionSyncEvents() {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE catalog_outbox ADD CONSTRAINT catalog_outbox_event_type_check " +
                    "CHECK (event_type IN ('MEDIA_CORE_MATERIALIZED', 'MEDIA_REFRESH_REQUESTED'))");
        } catch (Exception exception) {
            throw new AssertionError("Unable to install the pre-migration event type constraint", exception);
        }

        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/postgresql/V66__allow_album_release_version_outbox_events.sql"))
                .execute(dataSource);

        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateId(UUID.randomUUID());
        event.setEventType(CatalogEventType.ALBUM_RELEASE_VERSIONS_SYNC_REQUESTED);
        event.setPayload(new CatalogEventPayload(
                ExternalSource.MUSICBRAINZ, "release-group-id", MediaType.ALBUM, "pt-BR"));
        event.setStatus(CatalogOutboxStatus.PENDING);
        event.setAvailableAt(Instant.now());

        repository.saveAndFlush(event);

        assertThat(repository.findById(event.getId())).isPresent();
    }
}
