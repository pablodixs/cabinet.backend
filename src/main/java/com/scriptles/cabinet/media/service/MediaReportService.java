package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.CreateMediaReportRequest;
import com.scriptles.cabinet.media.dto.request.ImportExternalMediaRequest;
import com.scriptles.cabinet.media.dto.request.ReviewMediaReportRequest;
import com.scriptles.cabinet.media.dto.response.ExternalMediaResponse;
import com.scriptles.cabinet.media.dto.response.MediaReportResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaRelation;
import com.scriptles.cabinet.media.entity.MediaReport;
import com.scriptles.cabinet.media.enums.*;
import com.scriptles.cabinet.media.repository.MediaRelationRepository;
import com.scriptles.cabinet.media.repository.MediaReportRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.notifications.service.NotificationService;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class MediaReportService {
    private final MediaReportRepository mediaReportRepository;
    private final MediaRelationRepository mediaRelationRepository;
    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final ExternalMediaService externalMediaService;
    private final NotificationService notificationService;

    @Autowired
    public MediaReportService(
            MediaReportRepository mediaReportRepository,
            MediaRelationRepository mediaRelationRepository,
            MediaRepository mediaRepository,
            UserRepository userRepository,
            ExternalMediaService externalMediaService,
            NotificationService notificationService
    ) {
        this.mediaReportRepository = mediaReportRepository;
        this.mediaRelationRepository = mediaRelationRepository;
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
        this.externalMediaService = externalMediaService;
        this.notificationService = notificationService;
    }

    public MediaReportService(
            MediaReportRepository mediaReportRepository,
            MediaRelationRepository mediaRelationRepository,
            MediaRepository mediaRepository,
            UserRepository userRepository,
            ExternalMediaService externalMediaService
    ) {
        this(mediaReportRepository, mediaRelationRepository, mediaRepository, userRepository,
                externalMediaService, null);
    }

    @Transactional
    public MediaReportResponse create(CreateMediaReportRequest request, UUID reporterId) {
        validateSuggestion(request);
        User reporter = requireUser(reporterId);
        Media media = request.mediaId() == null ? null : mediaRepository.findById(request.mediaId())
                .orElseThrow(() -> notFound("MEDIA_NOT_FOUND", "A obra informada não foi encontrada"));

        MediaReport report = new MediaReport();
        report.setMedia(media);
        report.setSource(request.source());
        report.setExternalId(request.externalId().trim());
        report.setMediaType(request.mediaType());
        report.setMediaTitle(request.mediaTitle().trim());
        report.setCategory(request.category());
        report.setDescription(request.description().trim());
        report.setSuggestedTargetSource(request.suggestedTargetSource());
        report.setSuggestedTargetExternalId(trimToNull(request.suggestedTargetExternalId()));
        report.setSuggestedTargetType(request.suggestedTargetType());
        report.setSuggestedTargetTitle(trimToNull(request.suggestedTargetTitle()));
        report.setSuggestedRelationType(request.suggestedRelationType());
        report.setReportedBy(reporter);
        return MediaReportResponse.from(mediaReportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public PageResponse<MediaReportResponse> find(MediaReportStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<MediaReportResponse> reports = (status == null
                ? mediaReportRepository.findAllBy(pageable)
                : mediaReportRepository.findAllByStatus(status, pageable))
                .map(MediaReportResponse::from);
        return PageResponse.from(reports);
    }

    @Transactional
    public MediaReportResponse review(UUID reportId, ReviewMediaReportRequest request, UUID reviewerId) {
        MediaReport report = mediaReportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> notFound("REPORT_NOT_FOUND", "O reporte não foi encontrado"));
        if (report.getStatus() != MediaReportStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_ALREADY_REVIEWED", "Este reporte já foi revisado");
        }

        User reviewer = requireUser(reviewerId);
        if (request.decision() == MediaReportDecision.APPROVE && hasRelation(request, report)) {
            createRelation(report, request, reviewer);
        }

        report.setStatus(request.decision() == MediaReportDecision.APPROVE
                ? MediaReportStatus.APPROVED : MediaReportStatus.REJECTED);
        report.setReviewedBy(reviewer);
        report.setResolutionNote(trimToNull(request.resolutionNote()));
        report.setResolvedAt(Instant.now());
        MediaReport saved = mediaReportRepository.save(report);
        if (notificationService != null) notificationService.reportResolved(saved, reviewer);
        return MediaReportResponse.from(saved);
    }

    private void createRelation(MediaReport report, ReviewMediaReportRequest request, User reviewer) {
        ExternalSource targetSource = request.targetSource() != null
                ? request.targetSource() : report.getSuggestedTargetSource();
        String targetExternalId = request.targetExternalId() != null
                ? request.targetExternalId() : report.getSuggestedTargetExternalId();
        MediaType targetType = request.targetType() != null
                ? request.targetType() : report.getSuggestedTargetType();
        MediaRelationType relationType = request.relationType() != null
                ? request.relationType() : report.getSuggestedRelationType();

        if (targetSource == null || targetExternalId == null || targetType == null || relationType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INCOMPLETE_RELATION", "Informe a obra relacionada e o tipo de vínculo");
        }

        Media sourceMedia = resolveSourceMedia(report);
        Media targetMedia = importMedia(targetSource, targetExternalId, targetType);
        if (sourceMedia.getId().equals(targetMedia.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_RELATION", "Uma obra não pode ser vinculada a ela mesma");
        }
        validateRelationTypes(sourceMedia.getType(), targetMedia.getType(), relationType);

        saveRelation(sourceMedia, targetMedia, relationType, reviewer);
        saveRelation(targetMedia, sourceMedia, inverse(relationType), reviewer);
    }

    private Media resolveSourceMedia(MediaReport report) {
        if (report.getMedia() != null) {
            return report.getMedia();
        }
        if (report.getSource() == ExternalSource.MANUAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_MEDIA_NOT_IMPORTED", "A obra de origem precisa estar no Cabinet");
        }
        Media media = importMedia(report.getSource(), report.getExternalId(), report.getMediaType());
        report.setMedia(media);
        return media;
    }

    private Media importMedia(ExternalSource source, String externalId, MediaType type) {
        if (source == ExternalSource.MANUAL) {
            try {
                return mediaRepository.findById(UUID.fromString(externalId)).orElseThrow();
            } catch (RuntimeException exception) {
                throw notFound("TARGET_MEDIA_NOT_FOUND", "A obra relacionada não foi encontrada");
            }
        }
        ExternalMediaResponse imported = externalMediaService.importMedia(
                new ImportExternalMediaRequest(source, externalId.trim(), type));
        return mediaRepository.findById(imported.id())
                .orElseThrow(() -> notFound("MEDIA_NOT_FOUND", "Não foi possível importar a obra"));
    }

    private void saveRelation(Media source, Media target, MediaRelationType type, User reviewer) {
        if (mediaRelationRepository.existsBySourceMediaIdAndTargetMediaIdAndRelationType(
                source.getId(), target.getId(), type)) {
            return;
        }
        MediaRelation relation = new MediaRelation();
        relation.setSourceMedia(source);
        relation.setTargetMedia(target);
        relation.setRelationType(type);
        relation.setCreatedBy(reviewer);
        mediaRelationRepository.save(relation);
    }

    private MediaRelationType inverse(MediaRelationType type) {
        return switch (type) {
            case ADAPTATION_OF -> MediaRelationType.ADAPTED_AS;
            case ADAPTED_AS -> MediaRelationType.ADAPTATION_OF;
            case SOUNDTRACK -> MediaRelationType.SOUNDTRACK_OF;
            case SOUNDTRACK_OF -> MediaRelationType.SOUNDTRACK;
            case RE_RECORDING_OF -> MediaRelationType.RE_RECORDED_AS;
            case RE_RECORDED_AS -> MediaRelationType.RE_RECORDING_OF;
        };
    }

    private boolean hasRelation(ReviewMediaReportRequest request, MediaReport report) {
        return request.relationType() != null || request.targetExternalId() != null
                || report.getSuggestedRelationType() != null || report.getSuggestedTargetExternalId() != null;
    }

    private void validateSuggestion(CreateMediaReportRequest request) {
        boolean any = request.suggestedTargetSource() != null || request.suggestedTargetExternalId() != null
                || request.suggestedTargetType() != null || request.suggestedRelationType() != null;
        boolean complete = request.suggestedTargetSource() != null
                && trimToNull(request.suggestedTargetExternalId()) != null
                && request.suggestedTargetType() != null && request.suggestedRelationType() != null;
        if (any && !complete) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INCOMPLETE_RELATION", "Complete os dados da obra relacionada");
        }
        if (complete) {
            validateRelationTypes(
                    request.mediaType(),
                    request.suggestedTargetType(),
                    request.suggestedRelationType()
            );
        }
    }

    private void validateRelationTypes(
            MediaType sourceType,
            MediaType targetType,
            MediaRelationType relationType
    ) {
        if (relationType != MediaRelationType.RE_RECORDING_OF
                && relationType != MediaRelationType.RE_RECORDED_AS) {
            return;
        }
        if (sourceType != MediaType.ALBUM || targetType != MediaType.ALBUM) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RE_RECORDING_RELATION",
                    "Uma regravação deve vincular dois álbuns"
            );
        }
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "Usuário não encontrado"));
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
