package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.UpdateMediaMetadataRequest;
import com.scriptles.cabinet.media.dto.response.ModerationMediaResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaMetadataRevision;
import com.scriptles.cabinet.media.repository.MediaMetadataRevisionRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaModerationService {
    private final MediaRepository mediaRepository;
    private final MediaMetadataRevisionRepository revisionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<ModerationMediaResponse> findMedia(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.asc("title"), Sort.Order.asc("id")));
        String normalizedQuery = query == null ? "" : query.trim();
        Page<Media> media = normalizedQuery.isBlank()
                ? mediaRepository.findAll(pageable)
                : mediaRepository.findByTitleContainingIgnoreCaseOrOriginalTitleContainingIgnoreCase(
                        normalizedQuery, normalizedQuery, pageable);
        return PageResponse.from(media.map(ModerationMediaResponse::from));
    }

    @Transactional
    public ModerationMediaResponse update(UUID mediaId, UpdateMediaMetadataRequest request, UUID editorId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Obra não encontrada"));
        if (media.getVersion() != request.version()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "MEDIA_CHANGED",
                    "Esta obra foi alterada por outra pessoa. Recarregue antes de salvar"
            );
        }
        User editor = userRepository.findById(editorId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado"));

        String beforeState = snapshot(media);
        media.setTitle(request.title().trim());
        media.setOriginalTitle(trimToNull(request.originalTitle()));
        media.setDescription(trimToNull(request.description()));
        media.setTagline(trimToNull(request.tagline()));
        media.setCoverUrl(trimToNull(request.coverUrl()));
        media.setBackdropUrl(trimToNull(request.backdropUrl()));
        media.setLogoUrl(trimToNull(request.logoUrl()));
        media.setReleaseDate(request.releaseDate());
        media.setOriginalLanguage(normalizeLanguage(request.originalLanguage()));
        media.setCountryCode(normalizeCountry(request.countryCode()));
        media.setGenres(request.genres() == null
                ? new LinkedHashSet<>()
                : request.genres().stream()
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));

        Media saved = mediaRepository.saveAndFlush(media);
        MediaMetadataRevision revision = new MediaMetadataRevision();
        revision.setMedia(saved);
        revision.setEditedBy(editor);
        revision.setBeforeState(beforeState);
        revision.setAfterState(snapshot(saved));
        revisionRepository.save(revision);
        return ModerationMediaResponse.from(saved);
    }

    private String snapshot(Media media) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("title", media.getTitle());
        state.put("originalTitle", media.getOriginalTitle());
        state.put("description", media.getDescription());
        state.put("tagline", media.getTagline());
        state.put("coverUrl", media.getCoverUrl());
        state.put("backdropUrl", media.getBackdropUrl());
        state.put("logoUrl", media.getLogoUrl());
        state.put("releaseDate", media.getReleaseDate());
        state.put("originalLanguage", media.getOriginalLanguage());
        state.put("countryCode", media.getCountryCode());
        state.put("genres", media.getGenres());
        return objectMapper.writeValueAsString(state);
    }

    private String normalizeLanguage(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeCountry(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
