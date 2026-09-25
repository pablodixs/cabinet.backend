package com.scriptles.cabinet.notifications.repository;

import com.scriptles.cabinet.notifications.entity.PushInstallation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushInstallationRepository extends JpaRepository<PushInstallation, UUID> {
    Optional<PushInstallation> findByFcmToken(String fcmToken);
    List<PushInstallation> findByUserIdAndActiveTrue(UUID userId);
    Optional<PushInstallation> findByUserIdAndFcmToken(UUID userId, String fcmToken);
}
