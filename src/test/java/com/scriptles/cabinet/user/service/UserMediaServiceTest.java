package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.service.MediaConsumptionPolicy;
import com.scriptles.cabinet.user.dto.response.LibraryEntryResponse;
import com.scriptles.cabinet.user.dto.response.LibraryMediaResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMediaServiceTest {
    @Mock
    private UserMediaRepository userMediaRepository;

    @Mock
    private UserMediaActivityRepository userMediaActivityRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private ExternalReferenceRepository externalReferenceRepository;
    @Mock
    private MediaConsumptionPolicy mediaConsumptionPolicy;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserMediaService userMediaService;

    @Test
    void listsOnlyTheRequestedUsersFilteredLibraryWithExternalLinks() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Media media = media(mediaId, MediaType.BOOK);
        media.setTitle("A mão esquerda da escuridão");
        media.setCoverUrl("https://example.com/book.jpg");
        media.setReleaseDate(LocalDate.parse("1969-03-01"));

        UserMedia entry = new UserMedia();
        entry.setId(UUID.randomUUID());
        entry.setUser(user(userId));
        entry.setMedia(media);
        entry.setStatus(UserMediaStatus.COMPLETED);
        entry.setLastInteractionAt(Instant.parse("2026-07-14T12:00:00Z"));

        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(ExternalSource.GOOGLE_BOOKS);
        reference.setExternalId("book-123");
        reference.setPrimaryReference(true);

        when(userMediaRepository.findLibrary(
                any(UUID.class),
                any(UserMediaStatus.class),
                any(MediaType.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(entry),
                PageRequest.of(1, 12),
                25
        ));
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(any()))
                .thenReturn(List.of(reference));

        PageResponse<LibraryMediaResponse> response = userMediaService.findLibrary(
                userId,
                UserMediaStatus.COMPLETED,
                MediaType.BOOK,
                1,
                12
        );

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.mediaId()).isEqualTo(mediaId);
            assertThat(item.title()).isEqualTo("A mão esquerda da escuridão");
            assertThat(item.source()).isEqualTo(ExternalSource.GOOGLE_BOOKS);
            assertThat(item.externalId()).isEqualTo("book-123");
        });
        verify(userMediaRepository).findLibrary(
                userId,
                UserMediaStatus.COMPLETED,
                MediaType.BOOK,
                PageRequest.of(
                        1,
                        12,
                        org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC,
                                        "lastInteractionAt"
                                )
                                .and(org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC,
                                        "createdAt"
                                ))
                )
        );
    }

    @Test
    void createsAlbumInRotationWithSafeDefaults() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media media = media(mediaId, MediaType.ALBUM);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(userMediaRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(userMediaRepository.saveAndFlush(any(UserMedia.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LibraryEntryResponse response = userMediaService.upsert(
                userId,
                mediaId,
                UserMediaStatus.IN_PROGRESS
        );

        ArgumentCaptor<UserMedia> captor = ArgumentCaptor.forClass(UserMedia.class);
        verify(userMediaRepository).saveAndFlush(captor.capture());
        UserMedia saved = captor.getValue();
        assertThat(response.status()).isEqualTo(UserMediaStatus.IN_PROGRESS);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getCompletedAt()).isNull();
        assertThat(saved.getLastInteractionAt()).isNotNull();
        assertThat(saved.getFavorite()).isFalse();
        assertThat(saved.getPrivateEntry()).isFalse();
    }

    @Test
    void rejectsUnsupportedMovieStatus() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media(mediaId, MediaType.MOVIE)));
        when(userMediaRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userMediaService.upsert(userId, mediaId, UserMediaStatus.IN_PROGRESS))
                .isInstanceOf(ApiException.class)
                .hasMessage("Este estado não está disponível para o tipo de mídia");

        verify(userMediaRepository, never()).saveAndFlush(any());
    }

    @Test
    void repeatedCompletionKeepsOriginalCompletionTimestamp() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UserMedia entry = new UserMedia();
        entry.setUser(user(userId));
        entry.setMedia(media(mediaId, MediaType.BOOK));
        entry.setStatus(UserMediaStatus.COMPLETED);
        entry.setCompletedAt(java.time.Instant.parse("2026-01-02T03:04:05Z"));
        entry.setFavorite(false);
        entry.setPrivateEntry(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(entry.getUser()));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(entry.getMedia()));
        when(userMediaRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.of(entry));
        when(userMediaRepository.saveAndFlush(entry)).thenReturn(entry);

        userMediaService.upsert(userId, mediaId, UserMediaStatus.COMPLETED);

        assertThat(entry.getCompletedAt()).isEqualTo("2026-01-02T03:04:05Z");
        assertThat(entry.getLastInteractionAt()).isAfter(entry.getCompletedAt());
    }

    @Test
    void rejectsConsumptionStatusForAnUnreleasedWorkButAllowsPlanningIt() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media futureBook = media(mediaId, MediaType.BOOK);
        futureBook.setReleaseDate(LocalDate.now().plusDays(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(futureBook));
        when(userMediaRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        doThrow(new ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "MEDIA_NOT_RELEASED",
                "A obra ainda não foi lançada"
        )).when(mediaConsumptionPolicy).ensureReleased(futureBook);

        assertThatThrownBy(() -> userMediaService.upsert(
                userId, mediaId, UserMediaStatus.IN_PROGRESS))
                .isInstanceOf(ApiException.class)
                .hasMessage("A obra ainda não foi lançada");
        verify(userMediaRepository, never()).saveAndFlush(any());

        when(userMediaRepository.saveAndFlush(any(UserMedia.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(userMediaService.upsert(userId, mediaId, UserMediaStatus.PLANNED).status())
                .isEqualTo(UserMediaStatus.PLANNED);
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Media media(UUID id, MediaType type) {
        Media media = new Media();
        media.setId(id);
        media.setType(type);
        return media;
    }
}
