package com.scriptles.cabinet.notifications.service;

import com.scriptles.cabinet.comments.entity.Comment;
import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListLike;
import com.scriptles.cabinet.lists.repository.MediaListLikeRepository;
import com.scriptles.cabinet.media.entity.MediaReport;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.entity.ReviewLike;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.media.repository.ReviewLikeRepository;
import com.scriptles.cabinet.notifications.dto.NotificationResponse;
import com.scriptles.cabinet.notifications.dto.UnreadCountResponse;
import com.scriptles.cabinet.notifications.entity.Notification;
import com.scriptles.cabinet.notifications.enums.NotificationType;
import com.scriptles.cabinet.notifications.event.NotificationChangedEvent;
import com.scriptles.cabinet.notifications.repository.NotificationRepository;
import com.scriptles.cabinet.status.BackgroundJobRetention;
import com.scriptles.cabinet.status.BackgroundJobRunner;
import com.scriptles.cabinet.status.BackgroundJobTracker;
import com.scriptles.cabinet.status.JobKey;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.importer.LetterboxdImportJob;
import com.scriptles.cabinet.user.repository.UserEpisodeWatchRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryService deliveryService;
    private final BackgroundJobRunner jobRunner;
    private final BackgroundJobRetention backgroundJobRetention;
    private final MediaListLikeRepository mediaListLikeRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final SeriesEpisodeRepository seriesEpisodeRepository;
    private final UserMediaRepository userMediaRepository;
    private final UserEpisodeWatchRepository episodeWatchRepository;
    private final com.scriptles.cabinet.notifications.repository.LocalReleaseReminderRepository localReleaseReminderRepository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> find(UUID recipientId, int page, int size) {
        Page<NotificationResponse> notifications = notificationRepository
                .findByRecipientIdOrderByActivityAtDescIdDesc(recipientId, PageRequest.of(page, size))
                .map(NotificationResponse::from);
        return PageResponse.from(notifications);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UUID recipientId) {
        return new UnreadCountResponse(notificationRepository.countByRecipientIdAndReadAtIsNull(recipientId));
    }

    @Transactional
    public void markRead(UUID recipientId, Collection<UUID> ids) {
        List<Notification> notifications = notificationRepository
                .findByRecipientIdAndIdInAndReadAtIsNull(recipientId, ids);
        if (notifications.isEmpty()) return;
        Instant now = Instant.now();
        notifications.forEach(notification -> notification.setReadAt(now));
        notificationRepository.saveAll(notifications);
        changed(recipientId);
    }

    @Transactional
    public void syncListLike(MediaList list, User fallbackActor) {
        User recipient = list.getOwner();
        if (recipient.getId().equals(fallbackActor.getId())) return;
        entityManager.lock(list, LockModeType.PESSIMISTIC_WRITE);
        long count = mediaListLikeRepository.countByListIdAndUserIdNot(list.getId(), recipient.getId());
        Optional<Notification> existing = notificationRepository.findByRecipientIdAndTypeAndMediaListId(
                recipient.getId(), NotificationType.LIST_LIKED, list.getId());
        if (count == 0) {
            existing.ifPresent(notificationRepository::delete);
            if (existing.isPresent()) changed(recipient.getId());
            return;
        }
        User latestActor = mediaListLikeRepository
                .findFirstByListIdAndUserIdNotOrderByCreatedAtDescIdDesc(list.getId(), recipient.getId())
                .map(MediaListLike::getUser)
                .orElse(fallbackActor);
        Notification notification = existing.orElseGet(() -> notification(recipient, NotificationType.LIST_LIKED));
        notification.setMediaList(list);
        notification.setActor(latestActor);
        notification.setActorCount(count);
        notification.setReadAt(null);
        notification.setActivityAt(Instant.now());
        notificationRepository.save(notification);
        enqueue(notification);
        changed(recipient.getId());
    }

    @Transactional
    public void syncReviewLike(Review review, User fallbackActor) {
        User recipient = review.getUser();
        if (recipient.getId().equals(fallbackActor.getId())) return;
        entityManager.lock(review, LockModeType.PESSIMISTIC_WRITE);
        long count = reviewLikeRepository.countByReviewIdAndUserIdNot(review.getId(), recipient.getId());
        Optional<Notification> existing = notificationRepository.findByRecipientIdAndTypeAndReviewId(
                recipient.getId(), NotificationType.REVIEW_LIKED, review.getId());
        if (count == 0) {
            existing.ifPresent(notificationRepository::delete);
            if (existing.isPresent()) changed(recipient.getId());
            return;
        }
        User latestActor = reviewLikeRepository
                .findFirstByReviewIdAndUserIdNotOrderByCreatedAtDescIdDesc(review.getId(), recipient.getId())
                .map(ReviewLike::getUser)
                .orElse(fallbackActor);
        Notification notification = existing.orElseGet(() -> notification(recipient, NotificationType.REVIEW_LIKED));
        notification.setReview(review);
        notification.setActor(latestActor);
        notification.setActorCount(count);
        notification.setReadAt(null);
        notification.setActivityAt(Instant.now());
        notificationRepository.save(notification);
        enqueue(notification);
        changed(recipient.getId());
    }

    @Transactional
    public void commentCreated(Comment comment) {
        User actor = comment.getAuthor();
        User owner = comment.getMediaList() != null
                ? comment.getMediaList().getOwner() : comment.getReview().getUser();
        Map<UUID, RecipientType> recipients = new LinkedHashMap<>();
        if (!owner.getId().equals(actor.getId())) {
            recipients.put(owner.getId(), new RecipientType(
                    owner,
                    comment.getMediaList() != null
                            ? NotificationType.LIST_COMMENTED : NotificationType.REVIEW_COMMENTED));
        }
        if (comment.getParent() != null) {
            User parentAuthor = comment.getParent().getAuthor();
            if (!parentAuthor.getId().equals(actor.getId())) {
                recipients.put(parentAuthor.getId(), new RecipientType(parentAuthor, NotificationType.COMMENT_REPLIED));
            }
        }
        recipients.values().forEach(recipientType -> {
            if (notificationRepository.existsByRecipientIdAndCommentId(
                    recipientType.user().getId(), comment.getId())) return;
            Notification notification = notification(recipientType.user(), recipientType.type());
            notification.setActor(actor);
            notification.setComment(comment);
            notification.setMediaList(comment.getMediaList());
            notification.setReview(comment.getReview());
            notificationRepository.save(notification);
            enqueue(notification);
            changed(recipientType.user().getId());
        });
    }

    @Transactional
    public void commentDeleted(UUID commentId) {
        Set<UUID> recipients = notificationRepository.findByCommentId(commentId).stream()
                .map(notification -> notification.getRecipient().getId())
                .collect(java.util.stream.Collectors.toSet());
        notificationRepository.deleteByCommentId(commentId);
        recipients.forEach(this::changed);
    }

    @Transactional
    public void reportResolved(MediaReport report, User reviewer) {
        User recipient = report.getReportedBy();
        if (recipient.getId().equals(reviewer.getId())
                || notificationRepository.existsByRecipientIdAndReportId(recipient.getId(), report.getId())) return;
        Notification notification = notification(recipient, NotificationType.REPORT_RESOLVED);
        notification.setActor(reviewer);
        notification.setReport(report);
        notificationRepository.save(notification);
        enqueue(notification);
        changed(recipient.getId());
    }

    @Transactional
    public void letterboxdImportReady(LetterboxdImportJob job) {
        notifyLetterboxdImport(job, NotificationType.LETTERBOXD_IMPORT_READY);
    }

    @Transactional
    public void letterboxdImportCompleted(LetterboxdImportJob job) {
        notifyLetterboxdImport(job, NotificationType.LETTERBOXD_IMPORT_COMPLETED);
    }

    @Scheduled(cron = "${app.notifications.episode-cron}", zone = "${app.notifications.episode-zone}")
    @Transactional
    public void notifyEpisodeReleases() {
        jobRunner.execute(JobKey.EPISODE_NOTIFICATIONS, () -> {
            int episodes = 0;
            int notifications = 0;
            for (SeriesEpisode episode : seriesEpisodeRepository
                    .findAllByAirDateAndSeasonSeasonNumberGreaterThan(CabinetTime.today(), 0)) {
                episodes++;
                List<UserMedia> trackedEntries = userMediaRepository.findAllByMediaIdAndStatus(
                        episode.getSeason().getSeries().getId(), UserMediaStatus.IN_PROGRESS);
                for (UserMedia entry : trackedEntries) {
                    UUID recipientId = entry.getUser().getId();
                    if (episodeWatchRepository.existsByUserIdAndEpisodeId(recipientId, episode.getId())
                            || notificationRepository.existsByRecipientIdAndSeriesEpisodeId(
                                    recipientId, episode.getId())) {
                        continue;
                    }
                    Notification notification = notification(entry.getUser(), NotificationType.EPISODE_RELEASED);
                    notification.setSeriesEpisode(episode);
                    notificationRepository.save(notification);
                    enqueue(notification);
                    changed(recipientId);
                    notifications++;
                }
            }
            return BackgroundJobTracker.JobRunResult.completed(episodes, notifications, episodes, 0,
                    "Episode release notifications checked");
        });
    }

    @Scheduled(cron = "${app.notifications.media-release-cron:0 5 8 * * *}",
            zone = "${app.notifications.episode-zone}")
    @Transactional
    public void notifyMediaReleases() {
        jobRunner.execute(JobKey.MEDIA_RELEASE_NOTIFICATIONS, () -> {
            int created = 0;
            for (UserMedia entry : userMediaRepository.findPlannedReleasingOn(CabinetTime.today())) {
                User user = entry.getUser();
                var media = entry.getMedia();
                if (localReleaseReminderRepository.existsByUserIdAndMediaId(user.getId(), media.getId())
                        || notificationRepository.existsByRecipientIdAndTypeAndMediaId(
                        user.getId(), NotificationType.MEDIA_RELEASED, media.getId())) continue;
                Notification notification = notification(user, NotificationType.MEDIA_RELEASED);
                notification.setMedia(media);
                notificationRepository.save(notification);
                enqueue(notification);
                changed(user.getId());
                created++;
            }
            return BackgroundJobTracker.JobRunResult.completed(created, created, created, 0,
                    "Media release notifications created");
        });
    }

    @Transactional
    public void setLocalReleaseReminder(UUID userId, UUID mediaId, boolean enabled) {
        if (enabled) localReleaseReminderRepository.insertIfMissing(userId, mediaId);
        else localReleaseReminderRepository.deleteByUserIdAndMediaId(userId, mediaId);
    }

    @Scheduled(cron = "${app.notifications.retention-cron}")
    @Transactional
    public void deleteExpired() {
        jobRunner.execute(JobKey.NOTIFICATION_RETENTION, () -> {
            int removedNotifications = notificationRepository.deleteOlderThan(
                    Instant.now().minus(90, ChronoUnit.DAYS));
            int removedJobRuns = backgroundJobRetention.deleteExpired();
            int removed = removedNotifications + removedJobRuns;
            return BackgroundJobTracker.JobRunResult.completed(removed, removed, removed, 0,
                    "Expired Cabinet activity was cleaned up");
        });
    }

    private Notification notification(User recipient, NotificationType type) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setActivityAt(Instant.now());
        return notification;
    }

    private void notifyLetterboxdImport(LetterboxdImportJob job, NotificationType type) {
        User recipient = job.getUser();
        if (notificationRepository.existsByRecipientIdAndTypeAndLetterboxdImportJobId(
                recipient.getId(), type, job.getId())) return;
        Notification notification = notification(recipient, type);
        notification.setLetterboxdImportJob(job);
        notificationRepository.save(notification);
        enqueue(notification);
        changed(recipient.getId());
    }

    private void changed(UUID recipientId) {
        eventPublisher.publishEvent(new NotificationChangedEvent(recipientId));
    }

    private void enqueue(Notification notification) {
        if (deliveryService != null) deliveryService.enqueue(notification);
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(UUID recipientId, UUID notificationId) {
        return notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .map(NotificationResponse::from)
                .orElseThrow(() -> new com.scriptles.cabinet.common.api.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notificação não encontrada"));
    }

    @Transactional
    public void followed(User actor, User recipient) {
        if (actor.getId().equals(recipient.getId())
                || notificationRepository.existsByRecipientIdAndTypeAndActorId(
                recipient.getId(), NotificationType.FOLLOWED, actor.getId())) return;
        Notification notification = notification(recipient, NotificationType.FOLLOWED);
        notification.setActor(actor);
        notificationRepository.save(notification);
        enqueue(notification);
        changed(recipient.getId());
    }

    private record RecipientType(User user, NotificationType type) {
    }
}
