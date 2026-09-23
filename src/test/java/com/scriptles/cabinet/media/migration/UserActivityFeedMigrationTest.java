package com.scriptles.cabinet.media.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UserActivityFeedMigrationTest {
    @Test
    void migrationBackfillOnlyUsesExistingMediaRows() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/postgresql/V43__add_user_activity_feed.sql")) {
            assertThat(stream).isNotNull();
            String migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(migration).contains("join media m on m.id = activity.media_id");
            assertThat(migration).contains("join media m on m.id = likes.media_id");
            assertThat(migration).contains("join media m on m.id = ratings.media_id");
            assertThat(migration).contains("join media m on m.id = review.media_id");
        }
    }
}
