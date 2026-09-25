package com.scriptles.cabinet.notifications.repository;

import com.scriptles.cabinet.notifications.entity.NotificationDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    boolean existsByNotificationIdAndInstallationId(UUID notificationId, UUID installationId);
    @Modifying
    @Query(value = "insert into notification_deliveries (id, notification_id, installation_id, status, attempt_count, available_at, created_at, updated_at) " +
            "values (gen_random_uuid(), :notificationId, :installationId, 'PENDING', 0, now(), now(), now()) " +
            "on conflict (notification_id, installation_id) do nothing", nativeQuery = true)
    int enqueueIfMissing(@Param("notificationId") UUID notificationId,
                         @Param("installationId") UUID installationId);
    @Query(value = "select * from notification_deliveries where status in ('PENDING', 'RETRY') and available_at <= :now order by created_at, id for update skip locked",
            nativeQuery = true)
    List<NotificationDelivery> claimable(Instant now, Pageable pageable);
    List<NotificationDelivery> findByNotificationId(UUID notificationId);
    List<NotificationDelivery> findByStatusAndProcessingStartedAtBefore(
            NotificationDelivery.Status status, Instant before);
    @Modifying
    @Query("update NotificationDelivery delivery set delivery.status = com.scriptles.cabinet.notifications.entity.NotificationDelivery.Status.DEAD, delivery.lastError = 'Installation ownership changed' where delivery.installation.id = :installationId and delivery.status in (com.scriptles.cabinet.notifications.entity.NotificationDelivery.Status.PENDING, com.scriptles.cabinet.notifications.entity.NotificationDelivery.Status.RETRY, com.scriptles.cabinet.notifications.entity.NotificationDelivery.Status.PROCESSING)")
    int expirePendingForInstallation(UUID installationId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update notification_deliveries set status = case when attempt_count >= :maxAttempts then 'DEAD' else 'RETRY' end, available_at = :now, processing_started_at = null, last_error = 'Processing lock expired' where status = 'PROCESSING' and processing_started_at < :cutoff",
            nativeQuery = true)
    int releaseStale(Instant cutoff, Instant now, int maxAttempts);
}
