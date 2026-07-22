package com.scriptles.cabinet.user.importer;

import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.dto.response.ActiveLetterboxdImportResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LetterboxdImportServiceTest {
    @Mock LetterboxdExportParser parser;
    @Mock LetterboxdImportJobRepository jobRepository;
    @Mock LetterboxdImportItemRepository itemRepository;
    @Mock UserRepository userRepository;
    @Mock MediaRepository mediaRepository;
    @Mock ObjectMapper objectMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks LetterboxdImportService importService;

    @Test
    void returnsTheCurrentActiveImport() {
        UUID userId = UUID.randomUUID();
        LetterboxdImportJob job = job(userId, LetterboxdImportJobState.READY);
        when(jobRepository.findFirstByUserIdAndStateInOrderByCreatedAtDesc(
                eq(userId), anyCollection())).thenReturn(Optional.of(job));

        ActiveLetterboxdImportResponse response = importService.findActive(userId);

        assertThat(response.active()).isTrue();
        assertThat(response.job()).isNotNull();
        assertThat(response.job().id()).isEqualTo(job.getId());
        assertThat(response.job().state()).isEqualTo(LetterboxdImportJobState.READY);
    }

    @Test
    void reportsWhenThereIsNoActiveImport() {
        UUID userId = UUID.randomUUID();
        when(jobRepository.findFirstByUserIdAndStateInOrderByCreatedAtDesc(
                eq(userId), anyCollection())).thenReturn(Optional.empty());

        ActiveLetterboxdImportResponse response = importService.findActive(userId);

        assertThat(response.active()).isFalse();
        assertThat(response.job()).isNull();
    }

    private LetterboxdImportJob job(UUID userId, LetterboxdImportJobState state) {
        User user = new User();
        user.setId(userId);
        LetterboxdImportJob job = new LetterboxdImportJob();
        job.setId(UUID.randomUUID());
        job.setUser(user);
        job.setState(state);
        return job;
    }
}
