package com.scriptles.cabinet.notifications.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "firebase.messaging.enabled", havingValue = "true")
public class NotificationDeliveryWorker {
    private final NotificationDeliveryService deliveryService;
    private final PushGateway pushGateway;

    @Scheduled(fixedDelayString = "${firebase.messaging.poll-delay:1500}")
    public void poll() {
        deliveryService.releaseStale();
        for (var id : deliveryService.claim(25)) {
            var target = deliveryService.target(id);
            if (target == null) continue;
            try {
                pushGateway.send(target);
                deliveryService.delivered(id);
            } catch (PushDeliveryException failure) {
                deliveryService.failed(id, failure.getMessage(), failure.invalidToken());
            } catch (RuntimeException failure) {
                deliveryService.failed(id, failure.getMessage(), false);
                log.warn("Push delivery {} failed", id, failure);
            }
        }
    }

    public static class PushDeliveryException extends RuntimeException {
        private final boolean invalidToken;
        public PushDeliveryException(String message, boolean invalidToken, Throwable cause) {
            super(message, cause);
            this.invalidToken = invalidToken;
        }
        public boolean invalidToken() { return invalidToken; }
    }
}
