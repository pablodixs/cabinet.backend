package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserInterestPreference;
import com.scriptles.cabinet.user.enums.InterestTargetType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserInterestPreferenceRepository extends JpaRepository<UserInterestPreference, UUID> {
    @EntityGraph(attributePaths = {"person", "media"})
    List<UserInterestPreference> findAllByUserId(UUID userId);

    Optional<UserInterestPreference> findByUserIdAndTargetTypeAndGenreKey(
            UUID userId, InterestTargetType targetType, String genreKey);

    Optional<UserInterestPreference> findByUserIdAndTargetTypeAndPersonId(
            UUID userId, InterestTargetType targetType, UUID personId);

    Optional<UserInterestPreference> findByUserIdAndTargetTypeAndMediaId(
            UUID userId, InterestTargetType targetType, UUID mediaId);
}
