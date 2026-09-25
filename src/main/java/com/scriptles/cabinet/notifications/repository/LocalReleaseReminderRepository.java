package com.scriptles.cabinet.notifications.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LocalReleaseReminderRepository {
    private final EntityManager entityManager;
    public boolean existsByUserIdAndMediaId(UUID userId, UUID mediaId) {
        return !entityManager.createNativeQuery("select 1 from local_release_reminders where user_id = :userId and media_id = :mediaId")
                .setParameter("userId", userId).setParameter("mediaId", mediaId).getResultList().isEmpty();
    }
    public void insertIfMissing(UUID userId, UUID mediaId) {
        entityManager.createNativeQuery("insert into local_release_reminders(user_id, media_id) values (:userId, :mediaId) on conflict do nothing")
                .setParameter("userId", userId).setParameter("mediaId", mediaId).executeUpdate();
    }
    public void deleteByUserIdAndMediaId(UUID userId, UUID mediaId) {
        entityManager.createNativeQuery("delete from local_release_reminders where user_id = :userId and media_id = :mediaId")
                .setParameter("userId", userId).setParameter("mediaId", mediaId).executeUpdate();
    }
}
