package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.service.MediaCreditService;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.user.dto.request.ReorderUserAlbumRotationRequest;
import com.scriptles.cabinet.user.dto.response.UserAlbumRotationResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserAlbumRotation;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserAlbumRotationRepository;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAlbumRotationService {
    public static final int MAX_SIZE = 5;

    private final UserAlbumRotationRepository rotationRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final UserMediaActivityRepository activityRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final UserArtworkResolver userArtworkResolver;
    private final MediaCreditService mediaCreditService;
    private final SocialAccessPolicy socialAccessPolicy;

    @Transactional(readOnly = true)
    public List<UserAlbumRotationResponse> findMine(UUID userId) {
        return toResponses(rotationRepository.findAllByUserIdOrderByPositionAsc(userId), userId);
    }

    @Transactional(readOnly = true)
    public List<UserAlbumRotationResponse> findByUsername(String username, UUID viewerId) {
        User user = userRepository.findByUsernameIgnoreCase(username.trim())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .orElseThrow(() -> notFound("USER_PROFILE_NOT_FOUND", "Perfil não encontrado"));
        if (!socialAccessPolicy.canViewProfile(user, viewerId)) {
            throw notFound("USER_PROFILE_NOT_FOUND", "Perfil não encontrado");
        }
        return toResponses(rotationRepository.findAllByUserIdOrderByPositionAsc(user.getId()), user.getId());
    }

    @Transactional
    public UserAlbumRotationResponse add(UUID userId, UUID albumId) {
        User user = findUser(userId);
        Media album = mediaRepository.findById(albumId)
                .orElseThrow(() -> notFound("MEDIA_NOT_FOUND", "Mídia não encontrada"));
        ensureAlbum(album);
        UserAlbumRotation existing = rotationRepository.findByUserIdAndAlbumId(userId, albumId).orElse(null);
        if (existing != null) {
            return toResponses(List.of(existing), userId).getFirst();
        }
        List<UserAlbumRotation> current = rotationRepository.findAllByUserIdOrderByPositionAsc(userId);
        if (current.size() >= MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROTATION_FULL", "A rotação aceita no máximo cinco álbuns");
        }
        UserAlbumRotation rotation = new UserAlbumRotation();
        rotation.setUser(user);
        rotation.setAlbum(album);
        rotation.setPosition(current.size());
        rotation = rotationRepository.saveAndFlush(rotation);

        UserMediaActivity activity = new UserMediaActivity();
        activity.setUser(user);
        activity.setMedia(album);
        activity.setType(ProfileActivityType.ADDED_TO_ROTATION);
        activity.setOccurredOn(java.time.LocalDate.now());
        activity.setLoggedOn(java.time.LocalDate.now());
        activity.setVisibility(Visibility.PUBLIC);
        activity.setSource(ExternalSource.MANUAL);
        activity.setSourceKey("cabinet:rotation:" + UUID.randomUUID());
        activityRepository.save(activity);
        return toResponses(List.of(rotation), userId).getFirst();
    }

    @Transactional
    public void remove(UUID userId, UUID albumId) {
        List<UserAlbumRotation> current = locked(userId);
        UserAlbumRotation removed = current.stream()
                .filter(item -> item.getAlbum().getId().equals(albumId))
                .findFirst()
                .orElse(null);
        if (removed == null) return;
        rotationRepository.delete(removed);
        rotationRepository.flush();
        normalize(current.stream().filter(item -> item != removed).toList());
    }

    @Transactional
    public List<UserAlbumRotationResponse> reorder(UUID userId, ReorderUserAlbumRotationRequest request) {
        List<UserAlbumRotation> current = locked(userId);
        List<UUID> requested = request.mediaIds();
        if (requested.size() != current.size()
                || requested.stream().distinct().count() != requested.size()
                || !requested.containsAll(current.stream().map(item -> item.getAlbum().getId()).toList())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROTATION_ORDER", "A ordem deve conter exatamente os álbuns da rotação");
        }
        Map<UUID, UserAlbumRotation> byAlbum = current.stream()
                .collect(java.util.stream.Collectors.toMap(item -> item.getAlbum().getId(), item -> item));
        List<UserAlbumRotation> ordered = requested.stream().map(byAlbum::get).toList();
        normalize(ordered);
        return toResponses(ordered, userId);
    }

    private List<UserAlbumRotation> locked(UUID userId) {
        return rotationRepository.findAllByUserIdForUpdate(userId);
    }

    private void normalize(List<UserAlbumRotation> items) {
        for (int index = 0; index < items.size(); index++) items.get(index).setPosition(100 + index);
        rotationRepository.flush();
        for (int index = 0; index < items.size(); index++) items.get(index).setPosition(index);
        rotationRepository.flush();
    }

    private List<UserAlbumRotationResponse> toResponses(Collection<UserAlbumRotation> rotations, UUID ownerId) {
        List<UserAlbumRotation> items = rotations.stream().toList();
        if (items.isEmpty()) return List.of();
        List<UUID> mediaIds = items.stream().map(item -> item.getAlbum().getId()).toList();
        Map<UUID, ExternalReference> references = externalReferenceRepository
                .findAllByMediaIdInAndPrimaryReferenceTrue(mediaIds).stream()
                .collect(java.util.stream.Collectors.toMap(reference -> reference.getMedia().getId(), item -> item, (a, b) -> a));
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artwork = userArtworkResolver.resolve(
                ownerId, items.stream().map(UserAlbumRotation::getAlbum).toList());
        Map<UUID, MediaCreditService.CreditSummary> credits = mediaCreditService.summaries(
                items.stream().map(UserAlbumRotation::getAlbum).toList());
        return items.stream().map(item -> UserAlbumRotationResponse.from(
                item,
                references.get(item.getAlbum().getId()),
                artwork.get(item.getAlbum().getId()).coverUrl(),
                credits.getOrDefault(item.getAlbum().getId(), MediaCreditService.CreditSummary.empty()).creator()
        )).toList();
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> notFound("USER_NOT_FOUND", "Usuário não encontrado"));
    }

    private void ensureAlbum(Media media) {
        if (media.getType() != MediaType.ALBUM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_NOT_ALBUM", "A mídia informada não é um álbum");
        }
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
