package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countByMediaIdAndStatusAndPrivateEntryFalse(
            UUID mediaId,
            UserMediaStatus status
    );

    @Query(
            value = """
                    select userMedia
                    from UserMedia userMedia
                    join fetch userMedia.media media
                    where userMedia.user.id = :userId
                      and (:status is null or userMedia.status = :status)
                      and (:type is null or media.type = :type)
                    """,
            countQuery = """
                    select count(userMedia)
                    from UserMedia userMedia
                    join userMedia.media media
                    where userMedia.user.id = :userId
                      and (:status is null or userMedia.status = :status)
                      and (:type is null or media.type = :type)
                    """
    )
    Page<UserMedia> findLibrary(
            @Param("userId") UUID userId,
            @Param("status") UserMediaStatus status,
            @Param("type") MediaType type,
            Pageable pageable
    );

    @Query(
            value = """
                    select userMedia
                    from UserMedia userMedia
                    join fetch userMedia.media media
                    where userMedia.user.id = :userId
                      and (:includePrivate = true or userMedia.privateEntry = false)
                    """,
            countQuery = """
                    select count(userMedia)
                    from UserMedia userMedia
                    where userMedia.user.id = :userId
                      and (:includePrivate = true or userMedia.privateEntry = false)
                    """
    )
    Page<UserMedia> findProfileLibrary(
            @Param("userId") UUID userId,
            @Param("includePrivate") boolean includePrivate,
            Pageable pageable
    );

    @Query(
            value = """
                    select userMedia
                    from UserMedia userMedia
                    join fetch userMedia.media media
                    where userMedia.user.id = :userId
                      and (:includePrivate = true or userMedia.privateEntry = false)
                    order by coalesce(
                        userMedia.lastInteractionAt,
                        userMedia.updatedAt,
                        userMedia.createdAt
                    ) desc,
                    userMedia.id desc
                    """,
            countQuery = """
                    select count(userMedia)
                    from UserMedia userMedia
                    where userMedia.user.id = :userId
                      and (:includePrivate = true or userMedia.privateEntry = false)
                    """
    )
    Page<UserMedia> findProfileActivities(
            @Param("userId") UUID userId,
            @Param("includePrivate") boolean includePrivate,
            Pageable pageable
    );

    @Query("""
            select count(userMedia)
            from UserMedia userMedia
            where userMedia.user.id = :userId
              and (:includePrivate = true or userMedia.privateEntry = false)
              and (:status is null or userMedia.status = :status)
            """)
    long countProfileLibrary(
            @Param("userId") UUID userId,
            @Param("status") UserMediaStatus status,
            @Param("includePrivate") boolean includePrivate
    );

    boolean existsByUserIdAndMediaId(
            UUID userId,
            UUID mediaId
    );
}
