package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserMediaRepository extends JpaRepository<UserMedia, UUID> {
    Optional<UserMedia> findByUserIdAndMediaId(
            UUID userId,
            UUID mediaId
    );

    Page<UserMedia> findByUserId(
            UUID userId,
            Pageable pageable
    );

    Page<UserMedia> findByUserIdAndStatus(
            UUID userId,
            UserMediaStatus status,
            Pageable pageable
    );

    boolean existsByUserIdAndMediaId(
            UUID userId,
            UUID mediaId
    );
}
