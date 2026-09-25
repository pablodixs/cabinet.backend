package com.scriptles.cabinet.notifications.repository;

import com.scriptles.cabinet.notifications.entity.PushPreference;
import com.scriptles.cabinet.notifications.entity.PushPreferenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PushPreferenceRepository extends JpaRepository<PushPreference, PushPreferenceId> {
    List<PushPreference> findByUserId(UUID userId);
}
