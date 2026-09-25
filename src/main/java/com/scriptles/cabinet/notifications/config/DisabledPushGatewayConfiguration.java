package com.scriptles.cabinet.notifications.config;

import com.scriptles.cabinet.notifications.service.NotificationDeliveryService;
import com.scriptles.cabinet.notifications.service.PushGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "firebase.messaging.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledPushGatewayConfiguration {
    @Bean
    PushGateway disabledPushGateway() {
        return new PushGateway() {
            @Override public void send(NotificationDeliveryService.DeliveryTarget target) {
                throw new IllegalStateException("FCM delivery is disabled");
            }
            @Override public boolean configured() { return false; }
        };
    }
}
