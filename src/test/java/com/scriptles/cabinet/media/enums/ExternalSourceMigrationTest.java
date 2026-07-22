package com.scriptles.cabinet.media.enums;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalSourceMigrationTest {
    @Test
    void databaseChecksContainEveryExternalSource() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/postgresql/V25__allow_letterboxd_external_source.sql")) {
            assertThat(stream).isNotNull();
            String migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            for (ExternalSource source : ExternalSource.values()) {
                assertThat(migration).contains("'" + source.name() + "'");
            }
        }
    }
}
