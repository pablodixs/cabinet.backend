package com.scriptles.cabinet.profile.repository;

import com.scriptles.cabinet.profile.entity.HQReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HQReviewRepository extends JpaRepository<HQReview, UUID> {
    List<HQReview> findByHqProfileIdOrderByCreatedAtDesc(UUID hqId);
    Optional<HQReview> findByHqProfileIdAndMediaId(UUID hqId, UUID mediaId);
}
