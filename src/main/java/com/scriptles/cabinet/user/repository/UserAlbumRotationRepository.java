package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserAlbumRotation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAlbumRotationRepository extends JpaRepository<UserAlbumRotation, UUID> {
    @EntityGraph(attributePaths = "album")
    List<UserAlbumRotation> findAllByUserIdOrderByPositionAsc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "album")
    @org.springframework.data.jpa.repository.Query("select rotation from UserAlbumRotation rotation where rotation.user.id = :userId order by rotation.position asc")
    List<UserAlbumRotation> findAllByUserIdForUpdate(@org.springframework.data.repository.query.Param("userId") UUID userId);

    @EntityGraph(attributePaths = "album")
    Optional<UserAlbumRotation> findByUserIdAndAlbumId(UUID userId, UUID albumId);

    boolean existsByUserIdAndAlbumId(UUID userId, UUID albumId);
}
