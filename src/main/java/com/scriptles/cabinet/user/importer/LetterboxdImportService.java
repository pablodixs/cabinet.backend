package com.scriptles.cabinet.user.importer;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.dto.request.ResolveLetterboxdImportItemRequest;
import com.scriptles.cabinet.user.dto.response.LetterboxdImportItemResponse;
import com.scriptles.cabinet.user.dto.response.LetterboxdImportJobResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class LetterboxdImportService {
    private static final EnumSet<LetterboxdImportJobState> ACTIVE_STATES = EnumSet.of(
            LetterboxdImportJobState.PARSING,
            LetterboxdImportJobState.MATCHING,
            LetterboxdImportJobState.READY,
            LetterboxdImportJobState.IMPORTING
    );

    private final LetterboxdExportParser parser;
    private final LetterboxdImportJobRepository jobRepository;
    private final LetterboxdImportItemRepository itemRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public LetterboxdImportJobResponse start(UUID userId, MultipartFile file) {
        if (jobRepository.existsByUserIdAndStateIn(userId, ACTIVE_STATES)) {
            throw new ApiException(HttpStatus.CONFLICT, "LETTERBOXD_IMPORT_ALREADY_ACTIVE",
                    "Já existe uma importação do Letterboxd em andamento");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> notFound("Usuário não encontrado"));
        LetterboxdImportJob job = new LetterboxdImportJob();
        job.setUser(user);
        job.setState(LetterboxdImportJobState.PARSING);
        job.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        jobRepository.saveAndFlush(job);

        var films = parser.parse(file);
        for (var film : films) {
            LetterboxdImportItem item = new LetterboxdImportItem();
            item.setJob(job);
            item.setSourceKey(film.sourceKey());
            item.setLetterboxdUri(film.letterboxdUri());
            item.setTitle(film.title());
            item.setReleaseYear(film.year());
            item.setPayload(objectMapper.writeValueAsString(film.payload()));
            itemRepository.save(item);
        }
        job.setTotalItems(films.size());
        job.setState(LetterboxdImportJobState.MATCHING);
        jobRepository.saveAndFlush(job);
        eventPublisher.publishEvent(new LetterboxdImportRequestedEvent(job.getId(),
                LetterboxdImportRequestedEvent.Action.MATCH));
        return LetterboxdImportJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public LetterboxdImportJobResponse find(UUID userId, UUID jobId) {
        return LetterboxdImportJobResponse.from(findOwned(userId, jobId));
    }

    @Transactional(readOnly = true)
    public PageResponse<LetterboxdImportItemResponse> items(
            UUID userId, UUID jobId, LetterboxdImportItemState state, int page, int size) {
        findOwned(userId, jobId);
        var pageable = PageRequest.of(page, size);
        var result = state == null
                ? itemRepository.findAllByJobId(jobId, pageable)
                : itemRepository.findAllByJobIdAndState(jobId, state, pageable);
        return PageResponse.from(result.map(LetterboxdImportItemResponse::from));
    }

    @Transactional
    public LetterboxdImportItemResponse resolve(
            UUID userId, UUID jobId, UUID itemId, ResolveLetterboxdImportItemRequest request) {
        LetterboxdImportJob job = findOwned(userId, jobId);
        if (job.getState() != LetterboxdImportJobState.READY) {
            throw new ApiException(HttpStatus.CONFLICT, "LETTERBOXD_IMPORT_NOT_READY",
                    "A importação ainda não está pronta para revisão");
        }
        LetterboxdImportItem item = itemRepository.findByIdAndJobId(itemId, jobId)
                .orElseThrow(() -> notFound("Item da importação não encontrado"));
        if (Boolean.TRUE.equals(request.ignored())) {
            item.setState(LetterboxdImportItemState.SKIPPED);
            item.setSelectedMedia(null);
            item.setSelectedTmdbId(null);
        } else if (request.mediaId() != null) {
            Media media = mediaRepository.findById(request.mediaId())
                    .orElseThrow(() -> notFound("Filme selecionado não encontrado"));
            if (media.getType() != com.scriptles.cabinet.media.enums.MediaType.MOVIE) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMPORT_RESOLUTION",
                        "A exportação do Letterboxd só pode ser associada a filmes");
            }
            item.setSelectedMedia(media);
            item.setSelectedTmdbId(null);
            item.setState(LetterboxdImportItemState.RESOLVED);
        } else if (request.tmdbId() != null && !request.tmdbId().isBlank()) {
            item.setSelectedMedia(null);
            item.setSelectedTmdbId(request.tmdbId().trim());
            item.setState(LetterboxdImportItemState.RESOLVED);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMPORT_RESOLUTION",
                    "Selecione um filme ou ignore o item");
        }
        item.setOverrideStatus(Boolean.TRUE.equals(request.overrideStatus()));
        item.setOverrideRating(Boolean.TRUE.equals(request.overrideRating()));
        item.setOverrideReview(Boolean.TRUE.equals(request.overrideReview()));
        refreshCounts(job);
        return LetterboxdImportItemResponse.from(itemRepository.saveAndFlush(item));
    }

    @Transactional
    public LetterboxdImportJobResponse confirm(UUID userId, UUID jobId) {
        LetterboxdImportJob job = findOwned(userId, jobId);
        if (job.getState() != LetterboxdImportJobState.READY) {
            throw new ApiException(HttpStatus.CONFLICT, "LETTERBOXD_IMPORT_NOT_READY",
                    "A importação não está pronta para confirmação");
        }
        if (itemRepository.countByJobIdAndState(jobId, LetterboxdImportItemState.NEEDS_REVIEW) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "LETTERBOXD_IMPORT_HAS_UNRESOLVED_ITEMS",
                    "Resolva ou ignore todos os filmes ambíguos antes de confirmar");
        }
        job.setState(LetterboxdImportJobState.IMPORTING);
        jobRepository.saveAndFlush(job);
        eventPublisher.publishEvent(new LetterboxdImportRequestedEvent(jobId,
                LetterboxdImportRequestedEvent.Action.APPLY));
        return LetterboxdImportJobResponse.from(job);
    }

    @Transactional
    public LetterboxdImportJobResponse retryFailed(UUID userId, UUID jobId) {
        LetterboxdImportJob job = findOwned(userId, jobId);
        if (job.getState() != LetterboxdImportJobState.COMPLETED_WITH_ERRORS
                || itemRepository.countByJobIdAndState(jobId, LetterboxdImportItemState.FAILED) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "LETTERBOXD_IMPORT_HAS_NO_RETRYABLE_ITEMS",
                    "A importação não possui itens falhos para tentar novamente");
        }
        job.setState(LetterboxdImportJobState.IMPORTING);
        job.setCompletedAt(null);
        job.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        jobRepository.saveAndFlush(job);
        eventPublisher.publishEvent(new LetterboxdImportRequestedEvent(jobId,
                LetterboxdImportRequestedEvent.Action.APPLY));
        return LetterboxdImportJobResponse.from(job);
    }

    @Transactional
    public LetterboxdImportJobResponse cancel(UUID userId, UUID jobId) {
        LetterboxdImportJob job = findOwned(userId, jobId);
        if (job.getState() == LetterboxdImportJobState.IMPORTING) {
            throw new ApiException(HttpStatus.CONFLICT, "LETTERBOXD_IMPORT_APPLYING",
                    "A importação já está sendo aplicada");
        }
        if (!job.getState().terminal()) {
            job.setState(LetterboxdImportJobState.CANCELLED);
            job.setCompletedAt(Instant.now());
        }
        return LetterboxdImportJobResponse.from(jobRepository.saveAndFlush(job));
    }

    @Transactional(readOnly = true)
    public byte[] failures(UUID userId, UUID jobId) {
        findOwned(userId, jobId);
        StringBuilder csv = new StringBuilder("Title,Year,Letterboxd URI,Error\n");
        itemRepository.findAllByJobIdOrderByCreatedAtAsc(jobId).stream()
                .filter(item -> item.getState() == LetterboxdImportItemState.FAILED)
                .forEach(item -> csv.append(csv(item.getTitle())).append(',')
                        .append(item.getReleaseYear() == null ? "" : item.getReleaseYear()).append(',')
                        .append(csv(item.getLetterboxdUri())).append(',')
                        .append(csv(item.getErrorMessage())).append('\n'));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private LetterboxdImportJob findOwned(UUID userId, UUID jobId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> notFound("Importação não encontrada"));
    }

    void refreshCounts(LetterboxdImportJob job) {
        UUID id = job.getId();
        job.setMatchedItems((int) (itemRepository.countByJobIdAndState(id, LetterboxdImportItemState.AUTO_MATCHED)
                + itemRepository.countByJobIdAndState(id, LetterboxdImportItemState.RESOLVED)));
        job.setReviewItems((int) itemRepository.countByJobIdAndState(id, LetterboxdImportItemState.NEEDS_REVIEW));
        job.setImportedItems((int) itemRepository.countByJobIdAndState(id, LetterboxdImportItemState.IMPORTED));
        job.setPreservedItems((int) itemRepository.countByJobIdAndState(id, LetterboxdImportItemState.PRESERVED));
        job.setSkippedItems((int) itemRepository.countByJobIdAndState(id, LetterboxdImportItemState.SKIPPED));
        job.setFailedItems((int) itemRepository.countByJobIdAndState(id, LetterboxdImportItemState.FAILED));
        jobRepository.save(job);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "LETTERBOXD_IMPORT_NOT_FOUND", message);
    }

    private String csv(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
