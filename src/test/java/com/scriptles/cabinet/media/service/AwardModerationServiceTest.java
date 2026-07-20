package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.request.UpdateAwardRequest;
import com.scriptles.cabinet.media.entity.AwardEntry;
import com.scriptles.cabinet.media.entity.AwardEntryRevision;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.AwardOrigin;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.repository.AwardEntryRepository;
import com.scriptles.cabinet.media.repository.AwardEntryRevisionRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwardModerationServiceTest {
    @Mock private AwardEntryRepository awardEntryRepository;
    @Mock private AwardEntryRevisionRepository revisionRepository;
    @Mock private MediaRepository mediaRepository;
    @Mock private PersonRepository personRepository;
    @Mock private UserRepository userRepository;
    @Mock private AwardRefreshScheduler refreshScheduler;
    @Mock private ObjectMapper objectMapper;

    private AwardModerationService service;

    @BeforeEach
    void setUp() {
        service = new AwardModerationService(
                awardEntryRepository, revisionRepository, mediaRepository, personRepository,
                userRepository, refreshScheduler, objectMapper);
    }

    @Test
    void rejectsAnUpdateMadeAgainstAnOldVersion() {
        UUID awardId = UUID.randomUUID();
        AwardEntry entry = importedEntry();
        entry.setVersion(4);
        when(awardEntryRepository.findById(awardId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.update(awardId, update(3), UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                    assertThat(exception.getCode()).isEqualTo("AWARD_CHANGED");
                });

        verify(userRepository, never()).findById(any());
    }

    @Test
    void hidesAnEntryWithoutDeletingItAndRecordsTheRevision() {
        UUID awardId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        AwardEntry entry = importedEntry();
        User editor = new User();
        editor.setId(editorId);
        when(awardEntryRepository.findById(awardId)).thenReturn(Optional.of(entry));
        when(userRepository.findById(editorId)).thenReturn(Optional.of(editor));
        when(awardEntryRepository.saveAndFlush(entry)).thenReturn(entry);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.hide(awardId, editorId);

        assertThat(entry.isHidden()).isTrue();
        assertThat(entry.isCurated()).isTrue();
        assertThat(entry.getCuratedBy()).isSameAs(editor);
        ArgumentCaptor<AwardEntryRevision> revision = ArgumentCaptor.forClass(AwardEntryRevision.class);
        verify(revisionRepository).save(revision.capture());
        assertThat(revision.getValue().getAction()).isEqualTo("HIDE");
    }

    @Test
    void resetsAnImportedEntryAndSchedulesItsSubjectForRefresh() {
        UUID awardId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        AwardEntry entry = importedEntry();
        entry.setCurated(true);
        entry.setHidden(true);
        User editor = new User();
        editor.setId(editorId);
        when(awardEntryRepository.findById(awardId)).thenReturn(Optional.of(entry));
        when(userRepository.findById(editorId)).thenReturn(Optional.of(editor));
        when(awardEntryRepository.saveAndFlush(entry)).thenReturn(entry);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.resetToSource(awardId, editorId);

        assertThat(entry.isCurated()).isFalse();
        assertThat(entry.isHidden()).isFalse();
        assertThat(entry.getCuratedBy()).isNull();
        verify(refreshScheduler).scheduleMedia(entry.getMedia().getId());
        ArgumentCaptor<AwardEntryRevision> revision = ArgumentCaptor.forClass(AwardEntryRevision.class);
        verify(revisionRepository).save(revision.capture());
        assertThat(revision.getValue().getAction()).isEqualTo("RESET_TO_SOURCE");
    }

    private AwardEntry importedEntry() {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setTitle("Ainda Estou Aqui");
        AwardEntry entry = new AwardEntry();
        entry.setId(UUID.randomUUID());
        entry.setMedia(media);
        entry.setResult(AwardResult.WIN);
        entry.setCategoryName("Melhor filme internacional");
        entry.setOrigin(AwardOrigin.WIKIDATA);
        entry.setSourceStatementId("Q1-statement");
        return entry;
    }

    private UpdateAwardRequest update(long version) {
        return new UpdateAwardRequest(
                version, AwardResult.WIN, null, null, null, "Melhor filme",
                null, null, null, null, null, null, null, null, false);
    }
}
