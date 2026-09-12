package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.request.UpsertUserMediaArtworkRequest;
import com.scriptles.cabinet.media.dto.response.ArtworkOptionResponse;
import com.scriptles.cabinet.media.dto.response.ArtworkOptionsResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserMediaArtworkService {
    private static final String LANGUAGE = "pt-BR";

    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final UserMediaArtworkPreferenceRepository preferenceRepository;
    private final MediaArtworkCatalogProviderRegistry providerRegistry;

    @Transactional(readOnly = true)
    public ArtworkOptionsResponse findOptions(UUID userId, UUID mediaId) {
        requirePro(userId);
        SelectionContext context = context(mediaId);
        UserMediaArtworkPreference preference = preferenceRepository.findByUserIdAndMediaId(userId, mediaId)
                .orElse(null);
        return response(context, preference);
    }

    @Transactional
    public ArtworkOptionsResponse upsert(
            UUID userId,
            UUID mediaId,
            UpsertUserMediaArtworkRequest request
    ) {
        User user = requirePro(userId);
        SelectionContext context = context(mediaId);
        String coverKey = trimToNull(request.coverKey());
        String backdropKey = trimToNull(request.backdropKey());
        if (context.media().getType() == MediaType.ALBUM && backdropKey != null) {
            throw unsupported("Álbuns não possuem backdrop personalizável");
        }

        UserMediaArtworkPreference preference = preferenceRepository.findByUserIdAndMediaId(userId, mediaId)
                .orElseGet(UserMediaArtworkPreference::new);
        ArtworkAsset cover = selected(
                context.catalog().covers(), coverKey, "capa",
                preference.getCoverKey(), preference.getCoverUrl());
        ArtworkAsset backdrop = selected(
                context.catalog().backdrops(), backdropKey, "backdrop",
                preference.getBackdropKey(), preference.getBackdropUrl());

        if (cover == null && backdrop == null) {
            if (preference.getId() != null) preferenceRepository.delete(preference);
            return response(context, null);
        }

        preference.setUser(user);
        preference.setMedia(context.media());
        preference.setCoverProvider(cover == null ? null : context.catalog().provider());
        preference.setCoverKey(cover == null ? null : cover.key());
        preference.setCoverUrl(cover == null ? null : cover.url());
        preference.setBackdropProvider(backdrop == null ? null : context.catalog().provider());
        preference.setBackdropKey(backdrop == null ? null : backdrop.key());
        preference.setBackdropUrl(backdrop == null ? null : backdrop.url());
        UserMediaArtworkPreference saved = preferenceRepository.saveAndFlush(preference);
        return response(context, saved);
    }

    @Transactional
    public void delete(UUID userId, UUID mediaId) {
        requirePro(userId);
        if (!mediaRepository.existsById(mediaId)) throw mediaNotFound();
        preferenceRepository.findByUserIdAndMediaId(userId, mediaId).ifPresent(preferenceRepository::delete);
    }

    private SelectionContext context(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(this::mediaNotFound);
        ExternalSource source = switch (media.getType()) {
            case MOVIE, SERIES -> ExternalSource.TMDB;
            case ALBUM -> ExternalSource.MUSICBRAINZ;
            default -> throw unsupported("Este tipo de mídia não oferece personalização de arte");
        };
        MediaArtworkCatalogProvider provider = providerRegistry.find(source, media.getType())
                .orElseThrow(() -> unsupported("Não há um provedor de arte para esta mídia"));
        ExternalReference reference = externalReferenceRepository.findByMediaIdAndSource(mediaId, source)
                .orElseThrow(() -> unsupported("Esta mídia não possui uma referência compatível"));
        ArtworkCatalog catalog = provider.find(media.getType(), reference.getExternalId(), LANGUAGE);
        return new SelectionContext(media, catalog);
    }

    private ArtworkAsset selected(
            List<ArtworkAsset> options,
            String key,
            String label,
            String existingKey,
            String existingUrl
    ) {
        if (key == null) return null;
        ArtworkAsset current = options.stream().filter(option -> option.key().equals(key)).findFirst().orElse(null);
        if (current != null) return current;
        if (key.equals(existingKey) && existingUrl != null) {
            return new ArtworkAsset(key, existingUrl, existingUrl, null, null, null);
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARTWORK_SELECTION",
                "A opção de %s não está disponível".formatted(label)
        );
    }

    private ArtworkOptionsResponse response(
            SelectionContext context,
            UserMediaArtworkPreference preference
    ) {
        return new ArtworkOptionsResponse(
                context.media().getId(),
                context.catalog().provider(),
                context.media().getCoverUrl(),
                context.media().getBackdropUrl(),
                preference == null ? null : preference.getCoverKey(),
                preference == null ? null : preference.getBackdropKey(),
                preference == null ? null : preference.getCoverUrl(),
                preference == null ? null : preference.getBackdropUrl(),
                context.catalog().covers().stream().map(ArtworkOptionResponse::from).toList(),
                context.catalog().backdrops().stream().map(ArtworkOptionResponse::from).toList()
        );
    }

    private User requirePro(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado"));
        if (!Boolean.TRUE.equals(user.getActive()) || user.getAccountTier() != AccountTier.PRO) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PRO_REQUIRED",
                    "Este recurso está disponível para usuários Pro"
            );
        }
        return user;
    }

    private ApiException mediaNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada");
    }

    private ApiException unsupported(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA_CAPABILITY", message);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record SelectionContext(Media media, ArtworkCatalog catalog) {}
}
