package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.user.entity.UserMediaArtworkPreference;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.repository.UserMediaArtworkPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserArtworkResolver {
    private final UserMediaArtworkPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public Map<UUID, ResolvedArtwork> resolve(UUID viewerId, Collection<Media> mediaItems) {
        if (mediaItems == null || mediaItems.isEmpty()) return Map.of();

        Map<UUID, Media> mediaById = mediaItems.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Media::getId, Function.identity(), (first, ignored) -> first));
        Map<UUID, UserMediaArtworkPreference> preferences = viewerId == null
                ? Map.of()
                : preferenceRepository.findActiveByUserAndMediaIds(
                                viewerId, AccountTier.PRO, mediaById.keySet())
                        .stream()
                        .collect(Collectors.toMap(
                                preference -> preference.getMedia().getId(),
                                Function.identity()
                        ));

        Map<UUID, ResolvedArtwork> result = new LinkedHashMap<>();
        mediaById.forEach((mediaId, media) -> {
            UserMediaArtworkPreference preference = preferences.get(mediaId);
            result.put(mediaId, new ResolvedArtwork(
                    preference != null && preference.getCoverUrl() != null
                            ? preference.getCoverUrl() : media.getCoverUrl(),
                    preference != null && preference.getBackdropUrl() != null
                            ? preference.getBackdropUrl() : media.getBackdropUrl(),
                    preference != null && preference.getCoverUrl() != null,
                    preference != null && preference.getBackdropUrl() != null
            ));
        });
        return result;
    }

    @Transactional(readOnly = true)
    public ResolvedArtwork resolve(UUID viewerId, Media media) {
        return resolve(viewerId, List.of(media)).get(media.getId());
    }

    public record ResolvedArtwork(
            String coverUrl,
            String backdropUrl,
            boolean customCover,
            boolean customBackdrop
    ) {}
}
