package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.collection.CollectionSourceMode;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.event.TmdbCollectionReferenceLinkedEvent;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.status.BackgroundJobRunner;
import com.scriptles.cabinet.status.BackgroundJobTracker.JobRunResult;
import com.scriptles.cabinet.status.JobKey;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionEnrichmentSchedulerTest {
    @Test
    void periodicallySchedulesDueExternalFilmCollectionsAndContinuesAfterFailure() {
        CollectionExternalReferenceRepository references = mock(CollectionExternalReferenceRepository.class);
        CollectionEnrichmentService service = mock(CollectionEnrichmentService.class);
        BackgroundJobRunner tracker = trackingInline();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(references.findCollectionIdsDueForSync(
                eq(ExternalSource.TMDB), eq(CollectionType.FILM_SERIES), eq(CollectionSourceMode.EXTERNAL), any()))
                .thenReturn(List.of(firstId, secondId));
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary TMDB failure"))
                .when(service).sync(firstId, "pt-BR");

        CollectionEnrichmentScheduler scheduler = new CollectionEnrichmentScheduler(
                references, service, tracker, Runnable::run, Duration.ofHours(24), "pt-BR");
        scheduler.syncStaleCollections();

        org.mockito.ArgumentCaptor<Instant> cutoff = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(references).findCollectionIdsDueForSync(
                org.mockito.ArgumentMatchers.eq(ExternalSource.TMDB),
                org.mockito.ArgumentMatchers.eq(CollectionType.FILM_SERIES),
                org.mockito.ArgumentMatchers.eq(CollectionSourceMode.EXTERNAL), cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(Instant.now().minus(Duration.ofHours(23)));
        verify(service).sync(firstId, "pt-BR");
        verify(service).sync(secondId, "pt-BR");
    }

    @Test
    void syncsImmediatelyAfterTheTmdbReferenceTransactionCommits() {
        CollectionExternalReferenceRepository references = mock(CollectionExternalReferenceRepository.class);
        CollectionEnrichmentService service = mock(CollectionEnrichmentService.class);
        BackgroundJobRunner tracker = trackingInline();
        UUID collectionId = UUID.randomUUID();
        CollectionEnrichmentScheduler scheduler = new CollectionEnrichmentScheduler(
                references, service, tracker, Runnable::run, Duration.ofHours(24), "en-US");

        scheduler.referenceLinked(new TmdbCollectionReferenceLinkedEvent(collectionId));

        verify(service).sync(collectionId, "en-US");
        verify(references, never()).findCollectionIdsDueForSync(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private BackgroundJobRunner trackingInline() {
        BackgroundJobRunner tracker = mock(BackgroundJobRunner.class);
        when(tracker.execute(org.mockito.ArgumentMatchers.any(JobKey.class),
                org.mockito.ArgumentMatchers.any(Supplier.class))).thenAnswer(invocation ->
                ((Supplier<JobRunResult>) invocation.getArgument(1)).get());
        return tracker;
    }
}
