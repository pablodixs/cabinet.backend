package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.entity.AlbumTrack;
import com.scriptles.cabinet.media.entity.BookDetails;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.repository.AlbumDetailsRepository;
import com.scriptles.cabinet.media.repository.AlbumTrackRepository;
import com.scriptles.cabinet.media.repository.BookDetailsRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.MovieDetailsRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.media.repository.TrackDetailsRepository;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaQueryService {
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final AlbumDetailsRepository albumDetailsRepository;
    private final BookDetailsRepository bookDetailsRepository;
    private final MovieDetailsRepository movieDetailsRepository;
    private final SeriesDetailsRepository seriesDetailsRepository;
    private final AlbumTrackRepository albumTrackRepository;
    private final SeriesSeasonRepository seriesSeasonRepository;
    private final TrackDetailsRepository trackDetailsRepository;
    private final ReviewRepository reviewRepository;
    private final MediaLikeRepository mediaLikeRepository;
    private final MediaListItemRepository mediaListItemRepository;
    private final UserMediaRepository userMediaRepository;

    public ExternalMediaDetailsResponse findDetails(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Mídia não encontrada"
        ));
        List<ExternalReference> references = externalReferenceRepository.findAllByMediaId(mediaId);
        ExternalReference primaryReference = references.stream()
                .filter(ExternalReference::isPrimaryReference)
                .findFirst()
                .orElseGet(() -> references.stream().findFirst().orElse(null));
        CommunityStats community = communityStats(mediaId);

        ExternalSource source = primaryReference == null ? ExternalSource.MANUAL : primaryReference.getSource();
        String externalId = primaryReference == null ? mediaId.toString() : primaryReference.getExternalId();
        String externalUrl = primaryReference == null ? null : primaryReference.getExternalUrl();

        Map<String, String> externalReferences = references.stream()
                .filter(reference -> reference.getExternalId() != null)
                .collect(Collectors.toMap(
                        reference -> reference.getSource().name().toLowerCase(),
                        ExternalReference::getExternalId,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        if (media.getWikidataId() != null) {
            externalReferences.putIfAbsent("wikidata", media.getWikidataId());
        }

        List<ExternalMediaDetailsResponse.GenreResponse> genres = media.getGenres().stream()
                .map(name -> new ExternalMediaDetailsResponse.GenreResponse(null, name, ExternalSource.MANUAL))
                .toList();

        return new ExternalMediaDetailsResponse(
                media.getId(),
                externalId,
                source,
                media.getType(),
                media.getTitle(),
                media.getOriginalTitle(),
                null,
                media.getDescription(),
                media.getTagline(),
                media.getCoverUrl(),
                media.getBackdropUrl(),
                media.getLogoUrl(),
                externalUrl,
                media.getReleaseDate(),
                media.getOriginalLanguage(),
                media.getCountryCode(),
                media.getWikidataId(),
                externalReferences,
                genres,
                true,
                community.likeCount(),
                community.averageRating(),
                community.ratingDistribution(),
                community.listCount(),
                community.completedCount(),
                details(media)
        );
    }

    private Object details(Media media) {
        return switch (media.getType()) {
            case MOVIE -> movieDetailsRepository.findById(media.getId())
                    .map(details -> new ExternalMediaDetailsResponse.MovieDetails(
                            details.getRuntimeMinutes(),
                            details.getBudget(),
                            details.getRevenue(),
                            null
                    ))
                    .orElseGet(() -> new ExternalMediaDetailsResponse.MovieDetails(null, null, null, null));
            case TRACK -> trackDetailsRepository.findById(media.getId())
                    .map(details -> new ExternalMediaDetailsResponse.TrackDetails(
                            details.getDurationSeconds(),
                            details.getExplicit()
                    ))
                    .orElseGet(() -> new ExternalMediaDetailsResponse.TrackDetails(null, null));
            case ALBUM -> albumDetailsRepository.findById(media.getId())
                    .map(details -> new ExternalMediaDetailsResponse.AlbumDetails(
                            details.getAlbumType() == null ? null : details.getAlbumType().name(),
                            details.getNumberOfTracks(),
                            albumTrackRepository.findAllByAlbumIdOrderByDiscNumberAscTrackNumberAsc(media.getId())
                                    .stream()
                                    .map(this::toTrackResponse)
                                    .toList()
                    ))
                    .orElseGet(() -> new ExternalMediaDetailsResponse.AlbumDetails(null, null, List.of()));
            case SERIES -> seriesDetailsRepository.findById(media.getId())
                    .map(details -> new ExternalMediaDetailsResponse.SeriesDetails(
                            details.getStatus() == null ? null : details.getStatus().name(),
                            details.getNumberOfSeasons(),
                            details.getNumberOfEpisodes(),
                            details.getLastAirDate(),
                            seriesSeasonRepository.findAllBySeriesIdOrderBySeasonNumberAsc(media.getId())
                                    .stream()
                                    .map(this::toSeasonResponse)
                                    .toList()
                    ))
                    .orElseGet(() -> new ExternalMediaDetailsResponse.SeriesDetails(
                            null, null, null, null, List.of()));
            case BOOK -> bookDetailsRepository.findById(media.getId())
                    .map(this::toBookDetails)
                    .orElseGet(() -> new ExternalMediaDetailsResponse.BookDetails(
                            null, null, null, null, media.getWikidataId()));
        };
    }

    private ExternalMediaDetailsResponse.TrackResponse toTrackResponse(AlbumTrack track) {
        return new ExternalMediaDetailsResponse.TrackResponse(
                track.getExternalId(),
                track.getTitle(),
                track.getDiscNumber(),
                track.getTrackNumber(),
                track.getDurationSeconds(),
                track.getExplicit()
        );
    }

    private ExternalMediaDetailsResponse.SeasonResponse toSeasonResponse(SeriesSeason season) {
        return new ExternalMediaDetailsResponse.SeasonResponse(
                season.getExternalId(),
                season.getSeasonNumber(),
                season.getName(),
                season.getDescription(),
                season.getCoverUrl(),
                season.getEpisodeCount(),
                season.getAirDate()
        );
    }

    private ExternalMediaDetailsResponse.BookDetails toBookDetails(BookDetails details) {
        return new ExternalMediaDetailsResponse.BookDetails(
                details.getIsbn10(),
                details.getIsbn13(),
                details.getPageCount(),
                details.getPublisher(),
                details.getCanonicalWorkWikidataId()
        );
    }

    private CommunityStats communityStats(UUID mediaId) {
        Double averageRating = reviewRepository.summarizeRatings(List.of(mediaId), Visibility.PUBLIC)
                .stream()
                .findFirst()
                .map(ReviewRepository.MediaRatingProjection::getAverageRating)
                .orElse(null);
        Map<BigDecimal, Long> ratingCounts = reviewRepository
                .ratingDistribution(mediaId, Visibility.PUBLIC)
                .stream()
                .collect(Collectors.toMap(
                        projection -> projection.getRating().stripTrailingZeros(),
                        ReviewRepository.RatingDistributionProjection::getRatingCount
                ));
        List<ExternalMediaDetailsResponse.RatingDistributionBucket> ratingDistribution = new ArrayList<>(10);
        for (int step = 1; step <= 10; step++) {
            BigDecimal rating = BigDecimal.valueOf(step).divide(BigDecimal.valueOf(2));
            ratingDistribution.add(new ExternalMediaDetailsResponse.RatingDistributionBucket(
                    rating.doubleValue(),
                    ratingCounts.getOrDefault(rating.stripTrailingZeros(), 0L)
            ));
        }

        return new CommunityStats(
                mediaLikeRepository.countByMediaId(mediaId),
                averageRating,
                List.copyOf(ratingDistribution),
                mediaListItemRepository.countByMediaIdAndListVisibility(mediaId, Visibility.PUBLIC),
                userMediaRepository.countByMediaIdAndStatusAndPrivateEntryFalse(
                        mediaId,
                        UserMediaStatus.COMPLETED
                )
        );
    }

    private record CommunityStats(
            long likeCount,
            Double averageRating,
            List<ExternalMediaDetailsResponse.RatingDistributionBucket> ratingDistribution,
            long listCount,
            long completedCount
    ) {
    }
}
