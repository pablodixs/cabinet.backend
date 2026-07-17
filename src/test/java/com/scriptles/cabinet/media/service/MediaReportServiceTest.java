package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.request.CreateMediaReportRequest;
import com.scriptles.cabinet.media.dto.request.ReviewMediaReportRequest;
import com.scriptles.cabinet.media.dto.response.ExternalMediaResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaReport;
import com.scriptles.cabinet.media.enums.*;
import com.scriptles.cabinet.media.repository.MediaRelationRepository;
import com.scriptles.cabinet.media.repository.MediaReportRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaReportServiceTest {
    @Mock MediaReportRepository reportRepository;
    @Mock MediaRelationRepository relationRepository;
    @Mock MediaRepository mediaRepository;
    @Mock UserRepository userRepository;
    @Mock ExternalMediaService externalMediaService;

    @Test
    void approvingSuggestedRelationPersistsBothDirections() {
        UUID reportId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Media source = media(UUID.randomUUID(), MediaType.MOVIE, "Filme");
        Media target = media(UUID.randomUUID(), MediaType.BOOK, "Livro");
        User reviewer = new User();
        reviewer.setId(reviewerId);
        reviewer.setUsername("curador");
        reviewer.setDisplayName("Curador");

        MediaReport report = new MediaReport();
        report.setId(reportId);
        report.setMedia(source);
        report.setSource(ExternalSource.TMDB);
        report.setExternalId("10");
        report.setMediaType(MediaType.MOVIE);
        report.setMediaTitle("Filme");
        report.setCategory(MediaReportCategory.MISSING_RELATION);
        report.setDescription("A adaptação não está vinculada.");
        report.setStatus(MediaReportStatus.PENDING);
        report.setReportedBy(reviewer);
        report.setSuggestedTargetSource(ExternalSource.GOOGLE_BOOKS);
        report.setSuggestedTargetExternalId("book-10");
        report.setSuggestedTargetType(MediaType.BOOK);
        report.setSuggestedTargetTitle("Livro");
        report.setSuggestedRelationType(MediaRelationType.ADAPTATION_OF);

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(externalMediaService.importMedia(any())).thenReturn(new ExternalMediaResponse(
                target.getId(), "book-10", ExternalSource.GOOGLE_BOOKS, MediaType.BOOK,
                "Livro", null, null, null, null, null, null, true));
        when(mediaRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(reportRepository.save(report)).thenReturn(report);

        MediaReportService service = new MediaReportService(
                reportRepository, relationRepository, mediaRepository, userRepository, externalMediaService);
        service.review(reportId, new ReviewMediaReportRequest(
                MediaReportDecision.APPROVE, "Validado", null, null, null, null), reviewerId);

        ArgumentCaptor<com.scriptles.cabinet.media.entity.MediaRelation> relations =
                ArgumentCaptor.forClass(com.scriptles.cabinet.media.entity.MediaRelation.class);
        verify(relationRepository, times(2)).save(relations.capture());
        assertThat(relations.getAllValues()).extracting(
                relation -> relation.getSourceMedia().getId(),
                relation -> relation.getTargetMedia().getId(),
                com.scriptles.cabinet.media.entity.MediaRelation::getRelationType
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple(source.getId(), target.getId(), MediaRelationType.ADAPTATION_OF),
                org.assertj.core.groups.Tuple.tuple(target.getId(), source.getId(), MediaRelationType.ADAPTED_AS)
        );
        assertThat(report.getStatus()).isEqualTo(MediaReportStatus.APPROVED);
        assertThat(report.getResolutionNote()).isEqualTo("Validado");
    }

    @Test
    void approvingAlbumReRecordingPersistsBothDirections() {
        UUID reportId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Media taylorsVersion = media(UUID.randomUUID(), MediaType.ALBUM, "1989 (Taylor's Version)");
        Media original = media(UUID.randomUUID(), MediaType.ALBUM, "1989");
        User reviewer = new User();
        reviewer.setId(reviewerId);
        reviewer.setUsername("curador");
        reviewer.setDisplayName("Curador");

        MediaReport report = new MediaReport();
        report.setId(reportId);
        report.setMedia(taylorsVersion);
        report.setSource(ExternalSource.MUSICBRAINZ);
        report.setExternalId("taylors-version-id");
        report.setMediaType(MediaType.ALBUM);
        report.setMediaTitle("1989 (Taylor's Version)");
        report.setCategory(MediaReportCategory.MISSING_RELATION);
        report.setDescription("O álbum é uma nova gravação de 1989.");
        report.setStatus(MediaReportStatus.PENDING);
        report.setReportedBy(reviewer);
        report.setSuggestedTargetSource(ExternalSource.MUSICBRAINZ);
        report.setSuggestedTargetExternalId("original-id");
        report.setSuggestedTargetType(MediaType.ALBUM);
        report.setSuggestedTargetTitle("1989");
        report.setSuggestedRelationType(MediaRelationType.RE_RECORDING_OF);

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(externalMediaService.importMedia(any())).thenReturn(new ExternalMediaResponse(
                original.getId(), "original-id", ExternalSource.MUSICBRAINZ, MediaType.ALBUM,
                "1989", null, null, null, null, null, null, true));
        when(mediaRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(reportRepository.save(report)).thenReturn(report);

        MediaReportService service = new MediaReportService(
                reportRepository, relationRepository, mediaRepository, userRepository, externalMediaService);
        service.review(reportId, new ReviewMediaReportRequest(
                MediaReportDecision.APPROVE, null, null, null, null, null), reviewerId);

        ArgumentCaptor<com.scriptles.cabinet.media.entity.MediaRelation> relations =
                ArgumentCaptor.forClass(com.scriptles.cabinet.media.entity.MediaRelation.class);
        verify(relationRepository, times(2)).save(relations.capture());
        assertThat(relations.getAllValues()).extracting(
                relation -> relation.getSourceMedia().getId(),
                relation -> relation.getTargetMedia().getId(),
                com.scriptles.cabinet.media.entity.MediaRelation::getRelationType
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                        taylorsVersion.getId(), original.getId(), MediaRelationType.RE_RECORDING_OF),
                org.assertj.core.groups.Tuple.tuple(
                        original.getId(), taylorsVersion.getId(), MediaRelationType.RE_RECORDED_AS)
        );
    }

    @Test
    void rejectsReRecordingSuggestionOutsideAlbums() {
        MediaReportService service = new MediaReportService(
                reportRepository, relationRepository, mediaRepository, userRepository, externalMediaService);
        CreateMediaReportRequest request = new CreateMediaReportRequest(
                null,
                ExternalSource.MUSICBRAINZ,
                "recording-new",
                MediaType.TRACK,
                "New recording",
                MediaReportCategory.MISSING_RELATION,
                "Nova gravação da faixa.",
                ExternalSource.MUSICBRAINZ,
                "recording-original",
                MediaType.TRACK,
                "Original recording",
                MediaRelationType.RE_RECORDING_OF
        );

        assertThatThrownBy(() -> service.create(request, UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_RE_RECORDING_RELATION");
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                });
        verifyNoInteractions(reportRepository, userRepository, externalMediaService);
    }

    private Media media(UUID id, MediaType type, String title) {
        Media media = new Media();
        media.setId(id);
        media.setType(type);
        media.setTitle(title);
        return media;
    }
}
