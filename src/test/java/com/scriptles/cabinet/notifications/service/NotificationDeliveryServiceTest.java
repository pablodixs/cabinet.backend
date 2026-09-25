package com.scriptles.cabinet.notifications.service;

import com.scriptles.cabinet.notifications.entity.Notification;
import com.scriptles.cabinet.notifications.entity.NotificationDelivery;
import com.scriptles.cabinet.notifications.entity.PushInstallation;
import com.scriptles.cabinet.notifications.entity.PushPreference;
import com.scriptles.cabinet.notifications.entity.PushPreferenceId;
import com.scriptles.cabinet.notifications.enums.NotificationType;
import com.scriptles.cabinet.notifications.repository.NotificationDeliveryRepository;
import com.scriptles.cabinet.notifications.repository.PushInstallationRepository;
import com.scriptles.cabinet.notifications.repository.PushPreferenceRepository;
import com.scriptles.cabinet.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {
    @Mock NotificationDeliveryRepository deliveryRepository;
    @Mock PushInstallationRepository installationRepository;
    @Mock PushPreferenceRepository preferenceRepository;
    @InjectMocks NotificationDeliveryService service;

    @Test
    void enqueuesOnceForActiveInstallationsWhenPreferenceIsUnspecified() {
        User recipient = user();
        Notification notification = notification(recipient, NotificationType.LIST_LIKED);
        PushInstallation installation = installation(recipient);
        when(installationRepository.findByUserIdAndActiveTrue(recipient.getId())).thenReturn(List.of(installation));
        when(preferenceRepository.findById(new PushPreferenceId(recipient.getId(), notification.getType())))
                .thenReturn(Optional.empty());

        service.enqueue(notification);
        service.enqueue(notification);

        verify(deliveryRepository, times(2)).enqueueIfMissing(notification.getId(), installation.getId());
    }

    @Test
    void skipsEnqueueWhenTypeIsDisabled() {
        User recipient = user();
        Notification notification = notification(recipient, NotificationType.LIST_LIKED);
        PushInstallation installation = installation(recipient);
        PushPreference preference = new PushPreference();
        preference.setEnabled(false);
        when(installationRepository.findByUserIdAndActiveTrue(recipient.getId())).thenReturn(List.of(installation));
        when(preferenceRepository.findById(new PushPreferenceId(recipient.getId(), notification.getType())))
                .thenReturn(Optional.of(preference));

        service.enqueue(notification);

        verify(deliveryRepository, never()).enqueueIfMissing(any(), any());
    }

    @Test
    void retriesTemporaryFailureAndDeactivatesInvalidInstallation() {
        UUID deliveryId = UUID.randomUUID();
        PushInstallation installation = installation(user());
        NotificationDelivery row = new NotificationDelivery();
        row.setId(deliveryId);
        row.setInstallation(installation);
        row.setAttemptCount(1);
        row.setStatus(NotificationDelivery.Status.PROCESSING);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(row));

        service.failed(deliveryId, "temporary", false);

        assertThat(row.getStatus()).isEqualTo(NotificationDelivery.Status.RETRY);
        assertThat(row.getAvailableAt()).isNotNull();

        row.setStatus(NotificationDelivery.Status.PROCESSING);
        service.failed(deliveryId, "invalid", true);

        assertThat(row.getStatus()).isEqualTo(NotificationDelivery.Status.DEAD);
        assertThat(installation.isActive()).isFalse();
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static Notification notification(User recipient, NotificationType type) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipient(recipient);
        notification.setType(type);
        return notification;
    }

    private static PushInstallation installation(User user) {
        PushInstallation installation = new PushInstallation();
        installation.setId(UUID.randomUUID());
        installation.setUser(user);
        installation.setFcmToken("fcm-token");
        return installation;
    }
}
