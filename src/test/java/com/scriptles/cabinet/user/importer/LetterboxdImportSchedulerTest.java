package com.scriptles.cabinet.user.importer;

import com.scriptles.cabinet.notifications.service.NotificationService;
import com.scriptles.cabinet.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LetterboxdImportSchedulerTest {
    @Mock LetterboxdImportJobRepository jobRepository;
    @Mock LetterboxdImportItemRepository itemRepository;
    @Mock LetterboxdImportMatcher matcher;
    @Mock LetterboxdImportApplier applier;
    @Mock LetterboxdImportService importService;
    @Mock NotificationService notificationService;

    private LetterboxdImportScheduler scheduler;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        scheduler = new LetterboxdImportScheduler(directExecutor, jobRepository, itemRepository,
                matcher, applier, importService, notificationService);
    }

    @Test
    void notifiesTheUserWhenMatchingIsReadyForReview() {
        LetterboxdImportJob job = job(LetterboxdImportJobState.MATCHING);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(itemRepository.findAllByJobIdOrderByCreatedAtAsc(job.getId())).thenReturn(List.of());

        scheduler.requested(new LetterboxdImportRequestedEvent(
                job.getId(), LetterboxdImportRequestedEvent.Action.MATCH));

        assertThat(job.getState()).isEqualTo(LetterboxdImportJobState.READY);
        verify(notificationService).letterboxdImportReady(job);
    }

    @Test
    void notifiesTheUserWhenApplicationIsCompleted() {
        LetterboxdImportJob job = job(LetterboxdImportJobState.IMPORTING);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(itemRepository.findAllByJobIdOrderByCreatedAtAsc(job.getId())).thenReturn(List.of());

        scheduler.requested(new LetterboxdImportRequestedEvent(
                job.getId(), LetterboxdImportRequestedEvent.Action.APPLY));

        assertThat(job.getState()).isEqualTo(LetterboxdImportJobState.COMPLETED);
        verify(notificationService).letterboxdImportCompleted(job);
    }

    private LetterboxdImportJob job(LetterboxdImportJobState state) {
        User user = new User();
        user.setId(UUID.randomUUID());
        LetterboxdImportJob job = new LetterboxdImportJob();
        job.setId(UUID.randomUUID());
        job.setUser(user);
        job.setState(state);
        return job;
    }
}
