package com.scriptles.cabinet.media.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AwardRefreshSchedulerTest {
    @Mock private Executor executor;
    @Mock private AwardRefreshWorker worker;

    @Test
    void allowsOnlyOneInFlightRefreshPerSubject() {
        AwardRefreshScheduler scheduler = new AwardRefreshScheduler(executor, worker);
        UUID mediaId = UUID.randomUUID();

        scheduler.scheduleMedia(mediaId);
        scheduler.scheduleMedia(mediaId);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        task.getValue().run();
        verify(worker).refreshMedia(mediaId);

        scheduler.scheduleMedia(mediaId);
        verify(executor, times(2)).execute(task.capture());
    }
}
