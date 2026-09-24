package com.scriptles.cabinet.profile.repository;

import com.scriptles.cabinet.profile.entity.HQOperator;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HQOperatorRepository extends JpaRepository<HQOperator, UUID> {
    Optional<HQOperator> findByHqProfileIdAndEmailIgnoreCase(UUID hqId, String email);
    List<HQOperator> findByHqProfileIdOrderByCreatedAtAsc(UUID hqId);
}
