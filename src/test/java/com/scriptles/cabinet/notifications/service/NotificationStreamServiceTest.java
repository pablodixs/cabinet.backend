package com.scriptles.cabinet.notifications.service;

import com.scriptles.cabinet.common.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationStreamServiceTest {
    @Test
    void limitsConnectionsPerUser() {
        NotificationStreamService service = new NotificationStreamService(2, 10);
        UUID userId = UUID.randomUUID();

        service.subscribe(userId);
        service.subscribe(userId);

        assertThatThrownBy(() -> service.subscribe(userId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(429);
                    assertThat(exception.getCode()).isEqualTo("SSE_CONNECTION_LIMIT");
                });
    }

    @Test
    void limitsConnectionsGlobally() {
        NotificationStreamService service = new NotificationStreamService(2, 2);

        service.subscribe(UUID.randomUUID());
        service.subscribe(UUID.randomUUID());

        assertThatThrownBy(() -> service.subscribe(UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getStatus().value()).isEqualTo(429));
    }
}
