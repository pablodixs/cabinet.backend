package com.scriptles.cabinet.notifications.service;

import com.scriptles.cabinet.notifications.dto.PushPreferenceRequest;
import com.scriptles.cabinet.notifications.dto.PushPreferenceResponse;
import com.scriptles.cabinet.notifications.entity.PushInstallation;
import com.scriptles.cabinet.notifications.entity.PushPreference;
import com.scriptles.cabinet.notifications.repository.PushInstallationRepository;
import com.scriptles.cabinet.notifications.repository.NotificationDeliveryRepository;
import com.scriptles.cabinet.notifications.repository.PushPreferenceRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.scriptles.cabinet.notifications.enums.NotificationType;

@Service
@RequiredArgsConstructor
public class PushSettingsService {
    private final PushInstallationRepository installations;
    private final PushPreferenceRepository preferences;
    private final NotificationDeliveryRepository deliveries;
    private final UserRepository users;

    @Transactional
    public void register(UUID userId, String token) {
        PushInstallation installation = installations.findByFcmToken(token).orElseGet(PushInstallation::new);
        if (installation.getId() != null && !installation.getUser().getId().equals(userId)) {
            deliveries.expirePendingForInstallation(installation.getId());
        }
        installation.setUser(users.getReferenceById(userId));
        installation.setFcmToken(token);
        installation.setPlatform("IOS");
        installation.setActive(true);
        installation.setLastSeenAt(Instant.now());
        installations.save(installation);
    }

    @Transactional
    public void unregister(UUID userId, String token) {
        installations.findByUserIdAndFcmToken(userId, token).ifPresent(installation -> installation.setActive(false));
    }

    @Transactional(readOnly = true)
    public List<PushPreferenceResponse> findPreferences(UUID userId) {
        var stored = preferences.findByUserId(userId).stream()
                .collect(Collectors.toMap(PushPreference::getNotificationType, PushPreference::isEnabled));
        return Arrays.stream(NotificationType.values())
                .map(type -> new PushPreferenceResponse(type, stored.getOrDefault(type, true))).toList();
    }

    @Transactional
    public List<PushPreferenceResponse> updatePreferences(UUID userId, PushPreferenceRequest request) {
        for (var preference : request.preferences()) {
            var id = new com.scriptles.cabinet.notifications.entity.PushPreferenceId(userId, preference.type());
            PushPreference value = preferences.findById(id).orElseGet(PushPreference::new);
            value.setUser(users.getReferenceById(userId));
            value.setNotificationType(preference.type());
            value.setEnabled(preference.enabled());
            preferences.save(value);
        }
        return findPreferences(userId);
    }
}
