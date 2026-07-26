package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserProfileFavorite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserProfileFavoriteRepository
        extends JpaRepository<UserProfileFavorite, UUID> {

    @EntityGraph(attributePaths = "media")
    List<UserProfileFavorite> findAllByUserIdOrderByPositionAsc(UUID userId);

    void deleteAllByUserId(UUID userId);
}
