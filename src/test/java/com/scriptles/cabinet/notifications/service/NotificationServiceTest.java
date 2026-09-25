package com.scriptles.cabinet.notifications.service;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListLike;
import com.scriptles.cabinet.lists.repository.MediaListLikeRepository;
import com.scriptles.cabinet.media.repository.ReviewLikeRepository;
import com.scriptles.cabinet.media.entity.MediaReport;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.media.enums.MediaReportStatus;
import com.scriptles.cabinet.notifications.entity.Notification;
import com.scriptles.cabinet.notifications.dto.NotificationResponse;
import com.scriptles.cabinet.notifications.enums.NotificationType;
import com.scriptles.cabinet.notifications.event.NotificationChangedEvent;
import com.scriptles.cabinet.notifications.repository.NotificationRepository;
import com.scriptles.cabinet.notifications.service.NotificationDeliveryService;
import com.scriptles.cabinet.status.BackgroundJobRunner;
import com.scriptles.cabinet.status.BackgroundJobTracker.JobRunResult;
import com.scriptles.cabinet.status.BackgroundJobRetention;
import com.scriptles.cabinet.status.JobKey;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.importer.LetterboxdImportJob;
import com.scriptles.cabinet.user.repository.UserEpisodeWatchRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository notificationRepository;
    @Mock NotificationDeliveryService deliveryService;
    @Mock MediaListLikeRepository mediaListLikeRepository;
    @Mock ReviewLikeRepository reviewLikeRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EntityManager entityManager;
    @Mock SeriesEpisodeRepository seriesEpisodeRepository;
    @Mock UserMediaRepository userMediaRepository;
    @Mock UserEpisodeWatchRepository episodeWatchRepository;
    @Mock BackgroundJobRunner jobRunner;
    @Mock BackgroundJobRetention backgroundJobRetention;
    @InjectMocks NotificationService notificationService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void runTrackedWorkInline() {
        lenient().when(jobRunner.execute(any(JobKey.class), any(Supplier.class))).thenAnswer(invocation -> {
            ((Supplier<JobRunResult>) invocation.getArgument(1)).get();
            return null;
        });
    }

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
    void createsAndQueuesFollowNotificationOnlyOnce() {
        User actor = user("follow-actor");
        User recipient = user("follow-recipient");
        when(notificationRepository.existsByRecipientIdAndTypeAndActorId(
                recipient.getId(), NotificationType.FOLLOWED, actor.getId()))
                .thenReturn(false, true);

        notificationService.followed(actor, recipient);
        notificationService.followed(actor, recipient);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getType()).isEqualTo(NotificationType.FOLLOWED);
        assertThat(notification.getActor()).isSameAs(actor);
        assertThat(notification.getRecipient()).isSameAs(recipient);
        verify(deliveryService).enqueue(notification);
        verify(eventPublisher).publishEvent(new NotificationChangedEvent(recipient.getId()));
    }

    @Test
    void createsOnlyOneReleaseNotificationForAnUnwatchedTrackedEpisode() {
        User recipient = user("recipient");
        Media series = new Media();
        series.setId(UUID.randomUUID());
        series.setType(MediaType.SERIES);
        series.setTitle("Ruptura");
        SeriesSeason season = new SeriesSeason();
        season.setSeries(series);
        season.setSeasonNumber(2);
        Media episodeMedia = new Media();
        episodeMedia.setId(UUID.randomUUID());
        episodeMedia.setType(MediaType.EPISODE);
        SeriesEpisode episode = new SeriesEpisode();
        episode.setId(UUID.randomUUID());
        episode.setSeason(season);
        episode.setEpisodeMedia(episodeMedia);
        UserMedia entry = new UserMedia();
        entry.setUser(recipient);
        entry.setMedia(series);
        entry.setStatus(UserMediaStatus.IN_PROGRESS);

        when(seriesEpisodeRepository.findAllByAirDateAndSeasonSeasonNumberGreaterThan(
                java.time.LocalDate.now(), 0)).thenReturn(List.of(episode));
        when(userMediaRepository.findAllByMediaIdAndStatus(series.getId(), UserMediaStatus.IN_PROGRESS))
                .thenReturn(List.of(entry));

        notificationService.notifyEpisodeReleases();

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.EPISODE_RELEASED);
        assertThat(notification.getValue().getSeriesEpisode()).isSameAs(episode);
        verify(eventPublisher).publishEvent(new NotificationChangedEvent(recipient.getId()));

        when(notificationRepository.existsByRecipientIdAndSeriesEpisodeId(recipient.getId(), episode.getId()))
                .thenReturn(true);
        notificationService.notifyEpisodeReleases();
        verify(notificationRepository, times(1)).save(any(Notification.class));
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
    void notifiesWhenLetterboxdImportIsReadyForReview() {
        User recipient = user("recipient");
        LetterboxdImportJob job = letterboxdJob(recipient);

        notificationService.letterboxdImportReady(job);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.LETTERBOXD_IMPORT_READY);
        assertThat(captor.getValue().getLetterboxdImportJob()).isSameAs(job);
        NotificationResponse response = NotificationResponse.from(captor.getValue());
        assertThat(response.subject()).isEqualTo(new NotificationResponse.SubjectResponse(
                "LETTERBOXD_IMPORT", job.getId(), "Importação do Letterboxd", null));
        assertThat(response.href()).isEqualTo("/importacoes/letterboxd/" + job.getId());
        verify(eventPublisher).publishEvent(new NotificationChangedEvent(recipient.getId()));
    }

    @Test
    void notifiesOnlyOnceWhenLetterboxdImportIsCompleted() {
        User recipient = user("recipient");
        LetterboxdImportJob job = letterboxdJob(recipient);
        when(notificationRepository.existsByRecipientIdAndTypeAndLetterboxdImportJobId(
                recipient.getId(), NotificationType.LETTERBOXD_IMPORT_COMPLETED, job.getId()))
                .thenReturn(false, true);

        notificationService.letterboxdImportCompleted(job);
        notificationService.letterboxdImportCompleted(job);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.LETTERBOXD_IMPORT_COMPLETED);
        assertThat(captor.getValue().getLetterboxdImportJob()).isSameAs(job);
        verify(eventPublisher, times(1)).publishEvent(new NotificationChangedEvent(recipient.getId()));
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

    private LetterboxdImportJob letterboxdJob(User user) {
        LetterboxdImportJob job = new LetterboxdImportJob();
        job.setId(UUID.randomUUID());
        job.setUser(user);
        return job;
    }
}
