package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;
import java.util.List;
import java.time.LocalDate;

public interface UserMediaActivityRepository extends JpaRepository<UserMediaActivity, UUID> {
    Optional<UserMediaActivity> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserMediaActivity> findByUserIdAndSourceAndSourceKey(
            UUID userId,
            com.scriptles.cabinet.media.enums.ExternalSource source,
            String sourceKey
    );

    @Query(value = """
            select activity
            from UserMediaActivity activity
            join fetch activity.media
            where activity.user.id = :userId
              and (:includePrivate = true or activity.visibility = :publicVisibility)
            order by activity.occurredOn desc, activity.createdAt desc, activity.id desc
            """, countQuery = """
            select count(activity)
            from UserMediaActivity activity
            where activity.user.id = :userId
              and (:includePrivate = true or activity.visibility = :publicVisibility)
            """)
    Page<UserMediaActivity> findProfileActivities(
            @Param("userId") UUID userId,
            @Param("includePrivate") boolean includePrivate,
            @Param("publicVisibility") Visibility publicVisibility,
            Pageable pageable
    );

    @Query(value = """
            select activity
            from UserMediaActivity activity
            join fetch activity.media
            where activity.user.id = :userId
              and activity.type in :types
              and (:mediaType is null or activity.media.typeValue = :#{#mediaType == null ? null : #mediaType.name()})
              and (:includePrivate = true or activity.visibility = :publicVisibility)
            order by activity.occurredOn desc, activity.createdAt desc, activity.id desc
            """, countQuery = """
            select count(activity)
            from UserMediaActivity activity
            where activity.user.id = :userId
              and activity.type in :types
              and (:mediaType is null or activity.media.typeValue = :#{#mediaType == null ? null : #mediaType.name()})
              and (:includePrivate = true or activity.visibility = :publicVisibility)
            """)
    Page<UserMediaActivity> findDiaryEntries(
            @Param("userId") UUID userId,
            @Param("types") Collection<com.scriptles.cabinet.user.enums.ProfileActivityType> types,
            @Param("mediaType") com.scriptles.cabinet.media.enums.MediaType mediaType,
            @Param("includePrivate") boolean includePrivate,
            @Param("publicVisibility") Visibility publicVisibility,
            Pageable pageable
    );

    @Query(value = """
            select activity
            from UserMediaActivity activity
            join fetch activity.media
            where activity.user.id = :userId
              and activity.visibility in :visibilities
            order by activity.occurredOn desc, activity.createdAt desc, activity.id desc
            """, countQuery = """
            select count(activity)
            from UserMediaActivity activity
            where activity.user.id = :userId
              and activity.visibility in :visibilities
            """)
    Page<UserMediaActivity> findProfileActivitiesVisibleToFollower(
            @Param("userId") UUID userId,
            @Param("visibilities") Collection<Visibility> visibilities,
            Pageable pageable
    );

    @Query("""
            select activity
            from UserMediaActivity activity
            join fetch activity.media
            where activity.user.id = :userId
              and activity.media.id = :mediaId
              and activity.visibility in :visibilities
            order by activity.occurredOn desc, activity.createdAt desc, activity.id desc
            """)
    List<UserMediaActivity> findByUserAndMediaVisible(
            @Param("userId") UUID userId,
            @Param("mediaId") UUID mediaId,
            @Param("visibilities") Collection<Visibility> visibilities
    );

    @Query(value = """
            select activity
            from UserMediaActivity activity
            join fetch activity.media
            where activity.user.id = :userId
              and activity.type in :types
              and (:mediaType is null or activity.media.typeValue = :#{#mediaType == null ? null : #mediaType.name()})
              and activity.visibility in :visibilities
            order by activity.occurredOn desc, activity.createdAt desc, activity.id desc
            """, countQuery = """
            select count(activity)
            from UserMediaActivity activity
            where activity.user.id = :userId
              and activity.type in :types
              and (:mediaType is null or activity.media.typeValue = :#{#mediaType == null ? null : #mediaType.name()})
              and activity.visibility in :visibilities
            """)
    Page<UserMediaActivity> findDiaryEntriesVisibleToFollower(
            @Param("userId") UUID userId,
            @Param("types") Collection<com.scriptles.cabinet.user.enums.ProfileActivityType> types,
            @Param("mediaType") com.scriptles.cabinet.media.enums.MediaType mediaType,
            @Param("visibilities") Collection<Visibility> visibilities,
            Pageable pageable
    );

    @Query("""
            select tag as name, count(activity.id) as usageCount
            from UserMediaActivity activity
            join activity.tags tag
            where activity.user.id = :userId
              and activity.visibility in :visibilities
            group by tag
            order by count(activity.id) desc, tag asc
            """)
    List<ProfileTagCount> findProfileTags(
            @Param("userId") UUID userId,
            @Param("visibilities") Collection<Visibility> visibilities,
            Pageable pageable
    );

    @Query("""
            select distinct activity.media
            from UserMediaActivity activity
            join activity.tags tag
            where activity.user.id = :userId
              and activity.visibility in :visibilities
              and lower(tag) = :normalizedName
            """)
    List<Media> findMediaByTag(
            @Param("userId") UUID userId,
            @Param("visibilities") Collection<Visibility> visibilities,
            @Param("normalizedName") String normalizedName);

    long countByUserIdAndMediaIdAndTypeIn(
            UUID userId,
            UUID mediaId,
            Collection<com.scriptles.cabinet.user.enums.ProfileActivityType> types
    );

    java.util.Optional<UserMediaActivity> findTopByUserIdAndMediaIdAndTypeInOrderByOccurredOnDescCreatedAtDesc(
            UUID userId,
            UUID mediaId,
            Collection<com.scriptles.cabinet.user.enums.ProfileActivityType> types
    );

    interface ProfileTagCount {
        String getName();
        long getUsageCount();
    }
}
