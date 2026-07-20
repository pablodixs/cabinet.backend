package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserMediaArtworkPreference;
import com.scriptles.cabinet.user.enums.AccountTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMediaArtworkPreferenceRepository
        extends JpaRepository<UserMediaArtworkPreference, UUID> {

    Optional<UserMediaArtworkPreference> findByUserIdAndMediaId(UUID userId, UUID mediaId);

    @Query("""
            select preference
            from UserMediaArtworkPreference preference
            where preference.user.id = :userId
              and preference.user.accountTier = :tier
              and preference.media.id in :mediaIds
            """)
    List<UserMediaArtworkPreference> findActiveByUserAndMediaIds(
            @Param("userId") UUID userId,
            @Param("tier") AccountTier tier,
            @Param("mediaIds") Collection<UUID> mediaIds
    );
}
