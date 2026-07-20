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
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
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
    private final MediaListLikeRepository mediaListLikeRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final SeriesEpisodeRepository seriesEpisodeRepository;
    private final UserMediaRepository userMediaRepository;
    private final UserEpisodeWatchRepository episodeWatchRepository;

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
        changed(recipient.getId());
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void notifyEpisodeReleases() {
        for (SeriesEpisode episode : seriesEpisodeRepository
                .findAllByAirDateAndSeasonSeasonNumberGreaterThan(CabinetTime.today(), 0)) {
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
                changed(recipientId);
            }
        }
    }

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void deleteExpired() {
        notificationRepository.deleteOlderThan(Instant.now().minus(90, ChronoUnit.DAYS));
    }

    private Notification notification(User recipient, NotificationType type) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setActivityAt(Instant.now());
        return notification;
    }

    private void changed(UUID recipientId) {
        eventPublisher.publishEvent(new NotificationChangedEvent(recipientId));
    }

    private record RecipientType(User user, NotificationType type) {
    }
}
