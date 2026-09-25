package com.scriptles.cabinet.notifications.service;

import com.scriptles.cabinet.notifications.entity.Notification;
import com.scriptles.cabinet.notifications.entity.NotificationDelivery;
import com.scriptles.cabinet.notifications.entity.PushInstallation;
import com.scriptles.cabinet.notifications.entity.PushPreference;
import com.scriptles.cabinet.notifications.enums.NotificationType;
import com.scriptles.cabinet.notifications.repository.NotificationDeliveryRepository;
import com.scriptles.cabinet.notifications.repository.PushInstallationRepository;
import com.scriptles.cabinet.notifications.repository.PushPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {
    private static final int MAX_ATTEMPTS = 8;
    private final NotificationDeliveryRepository deliveryRepository;
    private final PushInstallationRepository installationRepository;
    private final PushPreferenceRepository preferenceRepository;

    @Transactional
    public void enqueue(Notification notification) {
        List<PushInstallation> installations = installationRepository.findByUserIdAndActiveTrue(
                notification.getRecipient().getId());
        if (installations.isEmpty()) return;
        boolean enabled = preferenceRepository.findById(new com.scriptles.cabinet.notifications.entity.PushPreferenceId(
                        notification.getRecipient().getId(), notification.getType()))
                .map(PushPreference::isEnabled).orElse(true);
        if (!enabled) return;
        for (PushInstallation installation : installations) {
            deliveryRepository.enqueueIfMissing(notification.getId(), installation.getId());
        }
    }

    @Transactional
    public List<UUID> claim(int limit) {
        Instant now = Instant.now();
        List<NotificationDelivery> rows = deliveryRepository.claimable(now, PageRequest.of(0, limit));
        rows.forEach(row -> {
            row.setStatus(NotificationDelivery.Status.PROCESSING);
            row.setAttemptCount(row.getAttemptCount() + 1);
            row.setProcessingStartedAt(now);
        });
        return rows.stream().map(NotificationDelivery::getId).toList();
    }

    @Transactional
    public DeliveryTarget target(UUID deliveryId) {
        NotificationDelivery row = deliveryRepository.findById(deliveryId).orElse(null);
        if (row == null || row.getStatus() != NotificationDelivery.Status.PROCESSING) return null;
        boolean enabled = preferenceRepository.findById(new com.scriptles.cabinet.notifications.entity.PushPreferenceId(
                row.getNotification().getRecipient().getId(), row.getNotification().getType()))
                .map(PushPreference::isEnabled).orElse(true);
        if (!enabled || !row.getInstallation().isActive()) {
            row.setStatus(NotificationDelivery.Status.DEAD);
            row.setProcessingStartedAt(null);
            row.setLastError("Push disabled or installation inactive");
            return null;
        }
        return new DeliveryTarget(row.getId(), row.getAttemptCount(), row.getNotification().getId(),
                row.getNotification().getType(), row.getInstallation().getId(),
                row.getInstallation().getFcmToken(), row.getNotification().getActor() == null
                ? "Cabinet" : row.getNotification().getActor().getDisplayName());
    }

    @Transactional
    public void delivered(UUID deliveryId) {
        deliveryRepository.findById(deliveryId).ifPresent(row -> {
            if (row.getStatus() != NotificationDelivery.Status.PROCESSING) return;
            row.setStatus(NotificationDelivery.Status.DELIVERED);
            row.setDeliveredAt(Instant.now());
            row.setProcessingStartedAt(null);
            row.setLastError(null);
        });
    }

    @Transactional
    public void failed(UUID deliveryId, String message, boolean invalidToken) {
        deliveryRepository.findById(deliveryId).ifPresent(row -> {
            if (row.getStatus() != NotificationDelivery.Status.PROCESSING) return;
            row.setLastError(shortMessage(message));
            row.setProcessingStartedAt(null);
            if (invalidToken) {
                row.getInstallation().setActive(false);
                row.setStatus(NotificationDelivery.Status.DEAD);
            } else if (row.getAttemptCount() >= MAX_ATTEMPTS) {
                row.setStatus(NotificationDelivery.Status.DEAD);
            } else {
                row.setStatus(NotificationDelivery.Status.RETRY);
                row.setAvailableAt(Instant.now().plusSeconds(Math.min(3600, 15L << Math.min(8, row.getAttemptCount() - 1))));
            }
        });
    }

    @Transactional
    public void releaseStale() {
        // A crash after claiming must not strand a delivery permanently.
        Instant now = Instant.now();
        deliveryRepository.releaseStale(now.minusSeconds(600), now, MAX_ATTEMPTS);
    }

    private String shortMessage(String message) {
        if (message == null) return "Push delivery failed";
        return message.substring(0, Math.min(message.length(), 1000));
    }

    public record DeliveryTarget(UUID deliveryId, int attempt, UUID notificationId, NotificationType type,
                                 UUID installationId, String token, String actorName) { }
}
