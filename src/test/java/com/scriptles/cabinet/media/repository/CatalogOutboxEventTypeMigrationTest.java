package com.scriptles.cabinet.media.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogOutboxEventTypeMigrationTest {
    @Test
    void migrationAllowsAlbumReleaseVersionSyncEvents() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE catalog_outbox (event_type VARCHAR(80) NOT NULL)");
            statement.execute("ALTER TABLE catalog_outbox ADD CONSTRAINT catalog_outbox_event_type_check " +
                    "CHECK (event_type IN ('MEDIA_CORE_MATERIALIZED', 'MEDIA_REFRESH_REQUESTED'))");

            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(
                    "db/migration/postgresql/V66__allow_album_release_version_outbox_events.sql")));

            statement.execute("INSERT INTO catalog_outbox (event_type) " +
                    "VALUES ('ALBUM_RELEASE_VERSIONS_SYNC_REQUESTED')");

            try (var result = statement.executeQuery("SELECT event_type FROM catalog_outbox")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("ALBUM_RELEASE_VERSIONS_SYNC_REQUESTED");
            }
        } catch (Exception exception) {
            throw new AssertionError("The event type constraint migration should allow album version sync", exception);
        }
    }
}
