package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTagRepository extends JpaRepository<UserTag, UUID> {
    List<UserTag> findAllByUserIdOrderByNameAsc(UUID userId);

    List<UserTag> findAllByUserIdAndNormalizedNameIn(
            UUID userId, Collection<String> normalizedNames);

    Optional<UserTag> findByUserIdAndNormalizedName(
            UUID userId, String normalizedName);

    long countByUserId(UUID userId);
}
