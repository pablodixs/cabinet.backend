package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.AwardEntry;
import com.scriptles.cabinet.media.entity.AwardSyncState;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.AwardDatePrecision;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.external.WikidataClient;
import com.scriptles.cabinet.media.repository.AwardEntryRepository;
import com.scriptles.cabinet.media.repository.AwardSyncStateRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwardSyncPersistenceServiceTest {
    @Mock private MediaRepository mediaRepository;
    @Mock private PersonRepository personRepository;
    @Mock private AwardEntryRepository awardEntryRepository;
    @Mock private AwardSyncStateRepository syncStateRepository;

    private AwardSyncPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new AwardSyncPersistenceService(
                mediaRepository, personRepository, awardEntryRepository, syncStateRepository);
    }

    @Test
    void keepsOnlyWinWhenMatchingNominationAndWinAreReturned() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        when(mediaRepository.getReferenceById(mediaId)).thenReturn(media);
        when(awardEntryRepository.findAllByMediaIdAndOriginAndCuratedFalse(any(), any()))
                .thenReturn(List.of());
        when(awardEntryRepository.findBySourceStatementId(any())).thenReturn(Optional.empty());
        when(syncStateRepository.findByMediaId(mediaId)).thenReturn(Optional.empty());

        WikidataClient.WikidataAward nomination = award(
                "http://www.wikidata.org/entity/statement/Q1-nomination", AwardResult.NOMINATION);
        WikidataClient.WikidataAward win = award(
                "http://www.wikidata.org/entity/statement/Q1-win", AwardResult.WIN);

        service.replaceMedia(mediaId, List.of(nomination, win), Duration.ofDays(7));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<AwardEntry>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(awardEntryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(entry -> {
            assertThat(entry.getResult()).isEqualTo(AwardResult.WIN);
            assertThat(entry.getSourceStatementId()).endsWith("Q1-win");
        });
        ArgumentCaptor<AwardSyncState> stateCaptor = ArgumentCaptor.forClass(AwardSyncState.class);
        verify(syncStateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getStatus().name()).isEqualTo("READY");
    }

    @Test
    void neverOverwritesCuratedExternalEntry() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        AwardEntry curated = new AwardEntry();
        curated.setCurated(true);
        curated.setCategoryName("Nome corrigido");
        String statement = "http://www.wikidata.org/entity/statement/Q1-win";

        when(mediaRepository.getReferenceById(mediaId)).thenReturn(media);
        when(awardEntryRepository.findAllByMediaIdAndOriginAndCuratedFalse(any(), any()))
                .thenReturn(List.of());
        when(awardEntryRepository.findBySourceStatementId(statement)).thenReturn(Optional.of(curated));
        when(syncStateRepository.findByMediaId(mediaId)).thenReturn(Optional.empty());

        service.replaceMedia(mediaId, List.of(award(statement, AwardResult.WIN)), Duration.ofDays(7));

        assertThat(curated.getCategoryName()).isEqualTo("Nome corrigido");
    }

    @Test
    void removesOnlyAutomaticStatementsThatDisappearedFromTheProvider() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        AwardEntry disappeared = new AwardEntry();
        disappeared.setSourceStatementId("Q1-old-statement");
        disappeared.setOrigin(com.scriptles.cabinet.media.enums.AwardOrigin.WIKIDATA);
        disappeared.setCurated(false);
        when(mediaRepository.getReferenceById(mediaId)).thenReturn(media);
        when(awardEntryRepository.findAllByMediaIdAndOriginAndCuratedFalse(any(), any()))
                .thenReturn(List.of(disappeared));
        when(syncStateRepository.findByMediaId(mediaId)).thenReturn(Optional.empty());

        service.replaceMedia(mediaId, List.of(), Duration.ofDays(7));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<AwardEntry>> removed = ArgumentCaptor.forClass(Iterable.class);
        verify(awardEntryRepository).deleteAll(removed.capture());
        assertThat(removed.getValue()).containsExactly(disappeared);
        ArgumentCaptor<AwardSyncState> state = ArgumentCaptor.forClass(AwardSyncState.class);
        verify(syncStateRepository).save(state.capture());
        assertThat(state.getValue().getStatus().name()).isEqualTo("EMPTY");
    }

    private WikidataClient.WikidataAward award(String statement, AwardResult result) {
        return new WikidataClient.WikidataAward(
                statement, result, "Q19020", "Óscar", "Q103916", "Óscar de melhor ator",
                "Q20022969", "Oscar 2016", LocalDate.of(2016, 2, 28), 2016,
                AwardDatePrecision.DAY, "Q18002795", "The Revenant",
                "https://www.wikidata.org/wiki/Q1"
        );
    }
}
