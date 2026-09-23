package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.request.UpsertUserMediaArtworkRequest;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ArtworkProvider;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.*;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMediaArtworkPreference;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.repository.UserMediaArtworkPreferenceRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserMediaArtworkServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final ExternalReferenceRepository referenceRepository = mock(ExternalReferenceRepository.class);
    private final UserMediaArtworkPreferenceRepository preferenceRepository =
            mock(UserMediaArtworkPreferenceRepository.class);
    private final MediaArtworkCatalogProviderRegistry providerRegistry =
            mock(MediaArtworkCatalogProviderRegistry.class);
    private final MediaArtworkCatalogProvider provider = mock(MediaArtworkCatalogProvider.class);
    private final UserMediaArtworkService service = new UserMediaArtworkService(
            userRepository, mediaRepository, referenceRepository, preferenceRepository, providerRegistry);

    private UUID userId;
    private UUID mediaId;
    private User user;
    private Media media;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mediaId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setActive(true);
        user.setAccountTier(AccountTier.PRO);
        media = new Media();
        media.setId(mediaId);
        media.setType(MediaType.MOVIE);
        media.setTitle("Matrix");
        media.setCoverUrl("https://canonical/cover.jpg");
        media.setBackdropUrl("https://canonical/backdrop.jpg");
    }

    @Test
    void rejectsFreeUsersBeforeCallingTheProvider() {
        user.setAccountTier(AccountTier.FREE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.findOptions(userId, mediaId))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("PRO_REQUIRED");
                    assertThat(error.getStatus().value()).isEqualTo(403);
                });
        verifyNoInteractions(mediaRepository, providerRegistry);
    }

    @Test
    void persistsOnlyProviderResolvedAssets() {
        ArtworkAsset cover = new ArtworkAsset(
                "/cover.jpg", "https://images/original/cover.jpg", "https://images/preview/cover.jpg",
                1000, 1500, "pt");
        ArtworkAsset backdrop = new ArtworkAsset(
                "/backdrop.jpg", "https://images/original/backdrop.jpg", "https://images/preview/backdrop.jpg",
                1920, 1080, null);
        stubCatalog(new ArtworkCatalog(ArtworkProvider.TMDB, List.of(cover), List.of(backdrop)));
        when(preferenceRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(preferenceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upsert(
                userId, mediaId, new UpsertUserMediaArtworkRequest(cover.key(), backdrop.key()));

        var saved = org.mockito.ArgumentCaptor.forClass(UserMediaArtworkPreference.class);
        verify(preferenceRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCoverKey()).isEqualTo(cover.key());
        assertThat(saved.getValue().getCoverUrl()).isEqualTo(cover.url());
        assertThat(saved.getValue().getBackdropKey()).isEqualTo(backdrop.key());
        assertThat(saved.getValue().getBackdropUrl()).isEqualTo(backdrop.url());
        assertThat(response.selectedCoverKey()).isEqualTo(cover.key());
        assertThat(response.selectedBackdropKey()).isEqualTo(backdrop.key());
    }

    @Test
    void rejectsAKeyThatIsNotInTheCurrentProviderCatalog() {
        stubCatalog(new ArtworkCatalog(ArtworkProvider.TMDB, List.of(), List.of()));

        assertThatThrownBy(() -> service.upsert(
                userId, mediaId, new UpsertUserMediaArtworkRequest("https://untrusted/image.jpg", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_ARTWORK_SELECTION"));
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void resolvesPreviouslySavedAlbumCoverKeysToMatchingEditionArtwork() {
        media.setType(MediaType.ALBUM);
        String coverUrl = "https://images/album-cover.jpg";
        ArtworkAsset editionCover = new ArtworkAsset(
                "release:release-id:front-id", coverUrl, coverUrl, null, null, null,
                "Album · 2020 · US");
        stubCatalog(ExternalSource.MUSICBRAINZ,
                new ArtworkCatalog(ArtworkProvider.COVER_ART_ARCHIVE, List.of(editionCover), List.of()));
        UserMediaArtworkPreference preference = new UserMediaArtworkPreference();
        preference.setCoverKey("front-id");
        preference.setCoverUrl(coverUrl);
        when(preferenceRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.of(preference));

        var response = service.findOptions(userId, mediaId);

        assertThat(response.selectedCoverKey()).isEqualTo(editionCover.key());
    }

    @Test
    void rejectsBackdropForAlbums() {
        media.setType(MediaType.ALBUM);
        stubCatalog(ExternalSource.MUSICBRAINZ,
                new ArtworkCatalog(ArtworkProvider.COVER_ART_ARCHIVE, List.of(), List.of()));

        assertThatThrownBy(() -> service.upsert(
                userId, mediaId, new UpsertUserMediaArtworkRequest(null, "backdrop")))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("UNSUPPORTED_MEDIA_CAPABILITY"));
    }

    @Test
    void restoringDefaultsDeletesThePreference() {
        stubCatalog(new ArtworkCatalog(ArtworkProvider.TMDB, List.of(), List.of()));
        UserMediaArtworkPreference existing = new UserMediaArtworkPreference();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setMedia(media);
        when(preferenceRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.of(existing));

        service.upsert(userId, mediaId, new UpsertUserMediaArtworkRequest(null, null));

        verify(preferenceRepository).delete(existing);
        verify(preferenceRepository, never()).saveAndFlush(any());
    }

    private void stubCatalog(ArtworkCatalog catalog) {
        stubCatalog(ExternalSource.TMDB, catalog);
    }

    private void stubCatalog(ExternalSource source, ArtworkCatalog catalog) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(source);
        reference.setExternalId("external-id");
        when(referenceRepository.findByMediaIdAndSource(mediaId, source)).thenReturn(Optional.of(reference));
        when(providerRegistry.find(source, media.getType())).thenReturn(Optional.of(provider));
        when(provider.find(media.getType(), "external-id", "pt-BR")).thenReturn(catalog);
    }
}
