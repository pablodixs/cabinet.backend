package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.dto.response.LibraryEntryResponse;
import com.scriptles.cabinet.user.dto.response.LibraryMediaResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserMediaService {
    private final UserMediaRepository userMediaRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;

    @Transactional(readOnly = true)
    public PageResponse<LibraryMediaResponse> findLibrary(
            UUID userId,
            UserMediaStatus status,
            MediaType type,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "lastInteractionAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Page<UserMedia> entries = userMediaRepository.findLibrary(
                userId,
                status,
                type,
                pageable
        );
        Map<UUID, ExternalReference> referencesByMediaId = entries.isEmpty()
                ? Map.of()
                : externalReferenceRepository
                        .findAllByMediaIdInAndPrimaryReferenceTrue(
                                entries.getContent().stream()
                                        .map(entry -> entry.getMedia().getId())
                                        .toList()
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                reference -> reference.getMedia().getId(),
                                Function.identity(),
                                (first, ignored) -> first
                        ));

        return PageResponse.from(entries.map(entry -> LibraryMediaResponse.from(
                entry,
                referencesByMediaId.get(entry.getMedia().getId())
        )));
    }

    @Transactional(readOnly = true)
    public Optional<LibraryEntryResponse> find(UUID userId, UUID mediaId) {
        return userMediaRepository.findByUserIdAndMediaId(userId, mediaId)
                .map(LibraryEntryResponse::from);
    }

    @Transactional
    public LibraryEntryResponse upsert(UUID userId, UUID mediaId, UserMediaStatus status) {
        User user = findUser(userId);
        Media media = findMedia(mediaId);
        UserMedia entry = userMediaRepository.findByUserIdAndMediaId(userId, mediaId)
                .orElseGet(() -> newEntry(user, media));

        applyStatus(entry, status, Instant.now());
        return LibraryEntryResponse.from(userMediaRepository.saveAndFlush(entry));
    }

    @Transactional
    public void delete(UUID userId, UUID mediaId) {
        userMediaRepository.findByUserIdAndMediaId(userId, mediaId)
                .ifPresent(userMediaRepository::delete);
    }

    @Transactional
    public UserMedia markCompleted(User user, Media media) {
        UserMedia entry = userMediaRepository.findByUserIdAndMediaId(user.getId(), media.getId())
                .orElseGet(() -> newEntry(user, media));
        applyStatus(entry, UserMediaStatus.COMPLETED, Instant.now());
        return userMediaRepository.save(entry);
    }

    private UserMedia newEntry(User user, Media media) {
        UserMedia entry = new UserMedia();
        entry.setUser(user);
        entry.setMedia(media);
        entry.setFavorite(false);
        entry.setPrivateEntry(false);
        return entry;
    }

    private void applyStatus(UserMedia entry, UserMediaStatus status, Instant now) {
        validateStatus(entry.getMedia().getType(), status);
        UserMediaStatus previousStatus = entry.getStatus();

        switch (status) {
            case PLANNED -> {
                entry.setStartedAt(null);
                entry.setCompletedAt(null);
            }
            case IN_PROGRESS -> {
                if (entry.getStartedAt() == null) {
                    entry.setStartedAt(now);
                }
                entry.setCompletedAt(null);
            }
            case COMPLETED -> {
                if (previousStatus != UserMediaStatus.COMPLETED || entry.getCompletedAt() == null) {
                    entry.setCompletedAt(now);
                }
            }
            case PAUSED, DROPPED -> entry.setCompletedAt(null);
        }

        entry.setStatus(status);
        entry.setLastInteractionAt(now);
    }

    private void validateStatus(MediaType mediaType, UserMediaStatus status) {
        EnumSet<UserMediaStatus> allowed = switch (mediaType) {
            case MOVIE, TRACK -> EnumSet.of(UserMediaStatus.PLANNED, UserMediaStatus.COMPLETED);
            case ALBUM -> EnumSet.of(
                    UserMediaStatus.PLANNED,
                    UserMediaStatus.IN_PROGRESS,
                    UserMediaStatus.COMPLETED
            );
            case BOOK, SERIES -> EnumSet.allOf(UserMediaStatus.class);
        };

        if (!allowed.contains(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MEDIA_STATUS",
                    "Este estado não está disponível para o tipo de mídia"
            );
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Usuário não encontrado"
        ));
    }

    private Media findMedia(UUID mediaId) {
        return mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Mídia não encontrada"
        ));
    }
}
