package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.user.enums.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaSearchItemAssembler {
    private final ExternalReferenceRepository externalReferenceRepository;
    private final RatingRepository ratingRepository;
    private final MediaCreditService mediaCreditService;
    private final UserArtworkResolver userArtworkResolver;

    public List<MediaSearchItemResponse> fromExternal(List<ExternalMedia> results) {
        return fromExternal(results, null);
    }

    public List<MediaSearchItemResponse> fromExternal(List<ExternalMedia> results, UUID viewerId) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        Set<ExternalSource> sources = results.stream().map(ExternalMedia::source).collect(Collectors.toSet());
        Set<String> externalIds = results.stream().map(ExternalMedia::externalId).collect(Collectors.toSet());
        Map<ExternalKey, ExternalReference> references = externalReferenceRepository
                .findAllBySourceInAndExternalIdIn(sources, externalIds)
                .stream()
                .collect(Collectors.toMap(
                        reference -> new ExternalKey(reference.getSource(), reference.getExternalId()),
                        reference -> reference,
                        (first, ignored) -> first
                ));

        List<Media> imported = references.values().stream()
                .map(ExternalReference::getMedia)
                .distinct()
                .toList();
        Map<UUID, RatingSummary> ratings = ratings(imported);
        Map<UUID, MediaCreditService.CreditSummary> creditSummaries = mediaCreditService.summaries(imported);
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = userArtworkResolver.resolve(viewerId, imported);

        return results.stream().map(result -> {
            ExternalReference reference = references.get(new ExternalKey(result.source(), result.externalId()));
            Media media = reference == null ? null : reference.getMedia();
            RatingSummary rating = media == null ? null : ratings.get(media.getId());
            UserArtworkResolver.ResolvedArtwork artwork = media == null ? null : artworks.get(media.getId());
            return new MediaSearchItemResponse(
                    media == null ? null : media.getId(),
                    result.externalId(),
                    result.source(),
                    result.type(),
                    result.title(),
                    result.creator() != null || media == null
                            ? result.creator()
                            : creditSummaries.getOrDefault(
                                    media.getId(), MediaCreditService.CreditSummary.empty()).creator(),
                    result.description(),
                    artwork != null && artwork.customCover()
                            ? artwork.coverUrl()
                            : result.coverUrl() != null || media == null ? result.coverUrl() : media.getCoverUrl(),
                    result.releaseDate(),
                    media != null,
                    rating == null ? null : rating.average(),
                    rating == null ? 0 : rating.count()
            );
        }).toList();
    }

    public List<MediaSearchItemResponse> fromImported(List<Media> mediaItems) {
        return fromImported(mediaItems, null);
    }

    public List<MediaSearchItemResponse> fromImported(List<Media> mediaItems, UUID viewerId) {
        if (mediaItems == null || mediaItems.isEmpty()) {
            return List.of();
        }
        Map<UUID, ExternalReference> references = externalReferenceRepository
                .findAllByMediaIdInAndPrimaryReferenceTrue(mediaItems.stream().map(Media::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        reference -> reference.getMedia().getId(),
                        reference -> reference,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        Map<UUID, RatingSummary> ratings = ratings(mediaItems);
        Map<UUID, MediaCreditService.CreditSummary> creditSummaries = mediaCreditService.summaries(mediaItems);
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = userArtworkResolver.resolve(viewerId, mediaItems);

        return mediaItems.stream().map(media -> {
            ExternalReference reference = references.get(media.getId());
            RatingSummary rating = ratings.get(media.getId());
            return new MediaSearchItemResponse(
                    media.getId(),
                    reference == null ? media.getId().toString() : reference.getExternalId(),
                    reference == null ? ExternalSource.MANUAL : reference.getSource(),
                    media.getType(),
                    media.getTitle(),
                    creditSummaries.getOrDefault(media.getId(), MediaCreditService.CreditSummary.empty()).creator(),
                    media.getDescription(),
                    artworks.get(media.getId()).coverUrl(),
                    media.getReleaseDate(),
                    true,
                    rating == null ? null : rating.average(),
                    rating == null ? 0 : rating.count()
            );
        }).toList();
    }

    private Map<UUID, RatingSummary> ratings(List<Media> mediaItems) {
        List<UUID> ids = mediaItems.stream().map(Media::getId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ratingRepository.summarizeRatings(ids, Visibility.PUBLIC).stream()
                .collect(Collectors.toMap(
                        RatingRepository.MediaRatingProjection::getMediaId,
                        projection -> new RatingSummary(
                                projection.getAverageRating(),
                                projection.getRatingCount()
                        )
                ));
    }

    private record ExternalKey(ExternalSource source, String externalId) {
    }

    private record RatingSummary(Double average, long count) {
    }
}
