package com.scriptles.cabinet.notifications.repository;

import com.scriptles.cabinet.notifications.entity.Notification;
import com.scriptles.cabinet.notifications.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @EntityGraph(attributePaths = {
            "actor",
            "mediaList",
            "review",
            "review.rating",
            "review.rating.media",
            "comment",
            "report",
            "report.media",
            "seriesEpisode",
            "seriesEpisode.episodeMedia",
            "seriesEpisode.season",
            "seriesEpisode.season.series",
            "letterboxdImportJob",
            "media"
    })
    Page<Notification> findByRecipientIdOrderByActivityAtDescIdDesc(UUID recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"actor", "mediaList", "review", "review.rating", "review.rating.media",
            "comment", "report", "report.media", "seriesEpisode", "seriesEpisode.episodeMedia",
            "seriesEpisode.season", "seriesEpisode.season.series", "letterboxdImportJob", "media"})
    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    List<Notification> findByRecipientIdAndIdInAndReadAtIsNull(UUID recipientId, Collection<UUID> ids);

    Optional<Notification> findByRecipientIdAndTypeAndMediaListId(
            UUID recipientId, NotificationType type, UUID listId);

    Optional<Notification> findByRecipientIdAndTypeAndReviewId(
            UUID recipientId, NotificationType type, UUID reviewId);

    boolean existsByRecipientIdAndCommentId(UUID recipientId, UUID commentId);

    boolean existsByRecipientIdAndReportId(UUID recipientId, UUID reportId);

    boolean existsByRecipientIdAndSeriesEpisodeId(UUID recipientId, UUID seriesEpisodeId);

    boolean existsByRecipientIdAndTypeAndLetterboxdImportJobId(
            UUID recipientId, NotificationType type, UUID letterboxdImportJobId);

    boolean existsByRecipientIdAndTypeAndActorId(UUID recipientId, NotificationType type, UUID actorId);
    boolean existsByRecipientIdAndTypeAndMediaId(UUID recipientId, NotificationType type, UUID mediaId);

    List<Notification> findByCommentId(UUID commentId);

    @Modifying
    long deleteByCommentId(UUID commentId);

    @Modifying
    @Query("""
            delete from Notification notification
            where notification.actor is not null
              and ((notification.recipient.id = :firstId and notification.actor.id = :secondId)
                or (notification.recipient.id = :secondId and notification.actor.id = :firstId))
            """)
    int deleteBetweenUsers(@Param("firstId") UUID firstId, @Param("secondId") UUID secondId);

    @Modifying
    @Query("delete from Notification notification where notification.activityAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
