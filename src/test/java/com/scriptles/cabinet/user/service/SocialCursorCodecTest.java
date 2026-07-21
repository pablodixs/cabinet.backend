package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialCursorCodecTest {
    private final SocialCursorCodec codec = new SocialCursorCodec();

    @Test
    void roundTripsOpaqueCursor() {
        Instant timestamp = Instant.parse("2026-07-21T10:00:00.123456Z");
        UUID userId = UUID.randomUUID();
        assertThat(codec.decode(codec.encode(timestamp, userId)))
                .isEqualTo(new SocialCursorCodec.Position(timestamp, userId));
    }

    @Test
    void rejectsMalformedCursorWithoutLeakingParserDetails() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-cursor"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_CURSOR");
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                });
    }
}
