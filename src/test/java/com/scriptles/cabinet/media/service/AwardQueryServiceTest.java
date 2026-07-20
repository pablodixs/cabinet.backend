package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.AwardPageResponse;
import com.scriptles.cabinet.media.entity.AwardEntry;
import com.scriptles.cabinet.media.entity.AwardSyncState;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.AwardOrigin;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.AwardSectionState;
import com.scriptles.cabinet.media.enums.AwardSyncStatus;
import com.scriptles.cabinet.media.repository.AwardEntryRepository;
import com.scriptles.cabinet.media.repository.AwardSyncStateRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwardQueryServiceTest {
    @Mock private MediaRepository mediaRepository;
    @Mock private PersonRepository personRepository;
    @Mock private AwardEntryRepository awardEntryRepository;
    @Mock private AwardSyncStateRepository syncStateRepository;
    @Mock private AwardRefreshScheduler refreshScheduler;

    private AwardQueryService service;

    @BeforeEach
    void setUp() {
        service = new AwardQueryService(mediaRepository, personRepository, awardEntryRepository,
                syncStateRepository, refreshScheduler);
    }

    @Test
    void returnsPendingAndSchedulesFirstMediaLookup() {
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(syncStateRepository.findByMediaId(mediaId)).thenReturn(Optional.empty());
        when(awardEntryRepository.findVisibleByMediaId(mediaId, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        AwardPageResponse response = service.findMedia(mediaId, null, 0, 20);

        assertThat(response.state()).isEqualTo(AwardSectionState.PENDING);
        assertThat(response.pendingWithoutData()).isTrue();
        verify(refreshScheduler).scheduleMedia(mediaId);
    }

    @Test
    void returnsStaleDataAndKeepsSeparateWinAndNominationTotals() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        AwardEntry entry = new AwardEntry();
        entry.setId(UUID.randomUUID());
        entry.setMedia(media);
        entry.setResult(AwardResult.WIN);
        entry.setCategoryName("Óscar de melhor filme");
        entry.setOrigin(AwardOrigin.WIKIDATA);
        AwardSyncState sync = new AwardSyncState();
        sync.setStatus(AwardSyncStatus.READY);
        sync.setFetchedAt(Instant.now().minusSeconds(7200));
        sync.setExpiresAt(Instant.now().minusSeconds(60));

        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(syncStateRepository.findByMediaId(mediaId)).thenReturn(Optional.of(sync));
        when(awardEntryRepository.findVisibleByMediaId(mediaId, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));
        when(awardEntryRepository.countByMediaIdAndHiddenFalseAndResult(mediaId, AwardResult.WIN))
                .thenReturn(3L);
        when(awardEntryRepository.countByMediaIdAndHiddenFalseAndResult(mediaId, AwardResult.NOMINATION))
                .thenReturn(7L);

        AwardPageResponse response = service.findMedia(mediaId, null, 0, 20);

        assertThat(response.state()).isEqualTo(AwardSectionState.STALE);
        assertThat(response.totalWins()).isEqualTo(3);
        assertThat(response.totalNominations()).isEqualTo(7);
        assertThat(response.items()).singleElement()
                .extracting(item -> item.category().name())
                .isEqualTo("Óscar de melhor filme");
        verify(refreshScheduler).scheduleMedia(mediaId);
    }

    @Test
    void returnsNotLinkedWithoutSchedulingUntilTtlExpires() {
        UUID personId = UUID.randomUUID();
        AwardSyncState sync = new AwardSyncState();
        sync.setStatus(AwardSyncStatus.NOT_LINKED);
        sync.setFetchedAt(Instant.now());
        sync.setExpiresAt(Instant.now().plusSeconds(3600));

        when(personRepository.existsById(personId)).thenReturn(true);
        when(syncStateRepository.findByPersonId(personId)).thenReturn(Optional.of(sync));
        when(awardEntryRepository.findVisibleByPersonId(personId, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        AwardPageResponse response = service.findPerson(personId, null, 0, 20);

        assertThat(response.state()).isEqualTo(AwardSectionState.NOT_LINKED);
        assertThat(response.pendingWithoutData()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "READY, READY",
            "EMPTY, EMPTY",
            "ERROR, ERROR"
    })
    void mapsFreshSynchronizationStates(AwardSyncStatus syncStatus, AwardSectionState expectedState) {
        UUID mediaId = UUID.randomUUID();
        AwardSyncState sync = new AwardSyncState();
        sync.setStatus(syncStatus);
        sync.setFetchedAt(Instant.now());
        sync.setExpiresAt(Instant.now().plusSeconds(3600));
        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(syncStateRepository.findByMediaId(mediaId)).thenReturn(Optional.of(sync));
        when(awardEntryRepository.findVisibleByMediaId(mediaId, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        AwardPageResponse response = service.findMedia(mediaId, null, 0, 20);

        assertThat(response.state()).isEqualTo(expectedState);
    }
}
