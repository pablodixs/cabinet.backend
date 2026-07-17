package com.scriptles.cabinet.notifications.service;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListLike;
import com.scriptles.cabinet.lists.repository.MediaListLikeRepository;
import com.scriptles.cabinet.media.repository.ReviewLikeRepository;
import com.scriptles.cabinet.media.entity.MediaReport;
import com.scriptles.cabinet.media.enums.MediaReportStatus;
import com.scriptles.cabinet.notifications.entity.Notification;
import com.scriptles.cabinet.notifications.enums.NotificationType;
import com.scriptles.cabinet.notifications.event.NotificationChangedEvent;
import com.scriptles.cabinet.notifications.repository.NotificationRepository;
import com.scriptles.cabinet.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository notificationRepository;
    @Mock MediaListLikeRepository mediaListLikeRepository;
    @Mock ReviewLikeRepository reviewLikeRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EntityManager entityManager;
    @InjectMocks NotificationService notificationService;

    @Test
    void groupsActiveListLikesAndReopensTheNotification() {
        User owner = user("owner");
        User actor = user("actor");
        MediaList list = new MediaList();
        list.setId(UUID.randomUUID());
        list.setOwner(owner);
        Notification existing = new Notification();
        existing.setRecipient(owner);
        existing.setType(NotificationType.LIST_LIKED);
        existing.setReadAt(java.time.Instant.now());
        MediaListLike latest = new MediaListLike();
        latest.setUser(actor);

        when(mediaListLikeRepository.countByListIdAndUserIdNot(list.getId(), owner.getId())).thenReturn(3L);
        when(notificationRepository.findByRecipientIdAndTypeAndMediaListId(
                owner.getId(), NotificationType.LIST_LIKED, list.getId())).thenReturn(Optional.of(existing));
        when(mediaListLikeRepository.findFirstByListIdAndUserIdNotOrderByCreatedAtDescIdDesc(
                list.getId(), owner.getId())).thenReturn(Optional.of(latest));

        notificationService.syncListLike(list, actor);

        assertThat(existing.getActor()).isSameAs(actor);
        assertThat(existing.getActorCount()).isEqualTo(3);
        assertThat(existing.getReadAt()).isNull();
        verify(notificationRepository).save(existing);
        verify(eventPublisher).publishEvent(new NotificationChangedEvent(owner.getId()));
    }

    @Test
    void removesTheGroupWhenTheLastExternalLikeIsRemoved() {
        User owner = user("owner");
        User actor = user("actor");
        MediaList list = new MediaList();
        list.setId(UUID.randomUUID());
        list.setOwner(owner);
        Notification existing = new Notification();
        existing.setRecipient(owner);
        when(notificationRepository.findByRecipientIdAndTypeAndMediaListId(
                owner.getId(), NotificationType.LIST_LIKED, list.getId())).thenReturn(Optional.of(existing));

        notificationService.syncListLike(list, actor);

        verify(notificationRepository).delete(existing);
        verify(eventPublisher).publishEvent(new NotificationChangedEvent(owner.getId()));
    }

    @Test
    void neverNotifiesAnActorAboutTheirOwnList() {
        User owner = user("owner");
        MediaList list = new MediaList();
        list.setId(UUID.randomUUID());
        list.setOwner(owner);

        notificationService.syncListLike(list, owner);

        verifyNoInteractions(notificationRepository, mediaListLikeRepository, eventPublisher);
    }

    @Test
    void createsOnlyOneResolutionNotificationForTheReporter() {
        User reporter = user("reporter");
        User reviewer = user("reviewer");
        MediaReport report = new MediaReport();
        report.setId(UUID.randomUUID());
        report.setReportedBy(reporter);
        report.setStatus(MediaReportStatus.APPROVED);
        report.setMediaTitle("A obra");
        when(notificationRepository.existsByRecipientIdAndReportId(reporter.getId(), report.getId()))
                .thenReturn(false, true);

        notificationService.reportResolved(report, reviewer);
        notificationService.reportResolved(report, reviewer);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.REPORT_RESOLVED);
        assertThat(captor.getValue().getReport()).isSameAs(report);
        assertThat(captor.getValue().getActor()).isSameAs(reviewer);
        verify(eventPublisher, times(1)).publishEvent(new NotificationChangedEvent(reporter.getId()));
    }

    @Test
    void marksOnlyTheRecipientsRequestedUnreadNotifications() {
        User recipient = user("recipient");
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        Notification first = new Notification();
        first.setRecipient(recipient);
        Notification second = new Notification();
        second.setRecipient(recipient);
        when(notificationRepository.findByRecipientIdAndIdInAndReadAtIsNull(recipient.getId(), ids))
                .thenReturn(List.of(first, second));

        notificationService.markRead(recipient.getId(), ids);

        assertThat(first.getReadAt()).isNotNull();
        assertThat(second.getReadAt()).isNotNull();
        verify(notificationRepository).saveAll(List.of(first, second));
        verify(eventPublisher).publishEvent(new NotificationChangedEvent(recipient.getId()));
    }

    private User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setDisplayName(username);
        return user;
    }
}
