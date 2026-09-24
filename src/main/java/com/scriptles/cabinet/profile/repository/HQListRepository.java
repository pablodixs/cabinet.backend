package com.scriptles.cabinet.profile.repository;

import com.scriptles.cabinet.profile.entity.HQList;
import com.scriptles.cabinet.user.enums.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HQListRepository extends JpaRepository<HQList, UUID> {
    List<HQList> findByHqProfileIdOrderByUpdatedAtDesc(UUID hqId);
    List<HQList> findByHqProfileIdAndVisibilityOrderByUpdatedAtDesc(UUID hqId, Visibility visibility);
    Optional<HQList> findByIdAndHqProfileId(UUID id, UUID hqId);
}
