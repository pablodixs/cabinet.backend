package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.request.UpdateMediaMetadataRequest;
import com.scriptles.cabinet.media.entity.AlbumDetails;
import com.scriptles.cabinet.media.catalog.GenreCatalogService;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaMetadataRevision;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.AlbumDetailsRepository;
import com.scriptles.cabinet.media.repository.MediaMetadataRevisionRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaModerationServiceTest {
    @Mock MediaRepository mediaRepository;
    @Mock AlbumDetailsRepository albumDetailsRepository;
    @Mock MediaMetadataRevisionRepository revisionRepository;
    @Mock UserRepository userRepository;
    @Mock ObjectMapper objectMapper;
    @Mock GenreCatalogService genreCatalogService;

    @Test
    void updatesMetadataAndRecordsRevision() {
        UUID mediaId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        Media media = media(mediaId, 3);
        User editor = new User();
        editor.setId(editorId);
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(userRepository.findById(editorId)).thenReturn(Optional.of(editor));
        when(mediaRepository.saveAndFlush(media)).thenReturn(media);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"state\":true}");

        MediaModerationService service = new MediaModerationService(
                mediaRepository, albumDetailsRepository, revisionRepository, userRepository, objectMapper, genreCatalogService);
        var response = service.update(mediaId, request(3L), editorId);

        assertThat(response.title()).isEqualTo("Novo título");
        assertThat(media.getOriginalTitle()).isNull();
        assertThat(media.getOriginalLanguage()).isEqualTo("pt-br");
        assertThat(media.getCountryCode()).isEqualTo("BR");
        assertThat(media.getGenres()).containsExactly("Drama", "Fantasia");
        ArgumentCaptor<MediaMetadataRevision> revision = ArgumentCaptor.forClass(MediaMetadataRevision.class);
        verify(revisionRepository).save(revision.capture());
        assertThat(revision.getValue().getMedia()).isSameAs(media);
        assertThat(revision.getValue().getEditedBy()).isSameAs(editor);
        assertThat(revision.getValue().getBeforeState()).isEqualTo("{\"state\":true}");
    }

    @Test
    void rejectsStaleEdits() {
        UUID mediaId = UUID.randomUUID();
        Media media = media(mediaId, 4);
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        MediaModerationService service = new MediaModerationService(
                mediaRepository, albumDetailsRepository, revisionRepository, userRepository, objectMapper, genreCatalogService);

        assertThatThrownBy(() -> service.update(mediaId, request(3L), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("alterada por outra pessoa");
        verifyNoInteractions(userRepository, revisionRepository, objectMapper);
    }

    @Test
    void updatesAnimatedCoverForAlbums() {
        UUID mediaId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        Media media = media(mediaId, 2);
        media.setType(MediaType.ALBUM);
        AlbumDetails albumDetails = new AlbumDetails();
        albumDetails.setMedia(media);
        albumDetails.setAnimatedCoverUrl("https://example.com/old.gif");
        User editor = new User();
        editor.setId(editorId);

        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(albumDetailsRepository.findById(mediaId)).thenReturn(Optional.of(albumDetails));
        when(userRepository.findById(editorId)).thenReturn(Optional.of(editor));
        when(mediaRepository.saveAndFlush(media)).thenReturn(media);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"state\":true}");

        MediaModerationService service = new MediaModerationService(
                mediaRepository, albumDetailsRepository, revisionRepository, userRepository, objectMapper, genreCatalogService);
        var response = service.update(mediaId, request(2L, " https://example.com/new.gif "), editorId);

        assertThat(response.animatedCoverUrl()).isEqualTo("https://example.com/new.gif");
        assertThat(albumDetails.getAnimatedCoverUrl()).isEqualTo("https://example.com/new.gif");
        verify(albumDetailsRepository).save(albumDetails);
    }

    private Media media(UUID id, long version) {
        Media media = new Media();
        media.setId(id);
        media.setType(MediaType.BOOK);
        media.setTitle("Título antigo");
        media.setGenres(new LinkedHashSet<>(List.of("Antigo")));
        media.setVersion(version);
        return media;
    }

    private UpdateMediaMetadataRequest request(long version) {
        return request(version, null);
    }

    private UpdateMediaMetadataRequest request(long version, String animatedCoverUrl) {
        return new UpdateMediaMetadataRequest(
                version,
                " Novo título ",
                " ",
                "Nova sinopse",
                null,
                "https://example.com/cover.jpg",
                animatedCoverUrl,
                null,
                null,
                LocalDate.of(2024, 1, 1),
                "PT-BR",
                "br",
                List.of("Drama", "Fantasia")
        );
    }
}
