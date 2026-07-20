package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.MediaCommunityUserResponse;
import com.scriptles.cabinet.media.entity.AlbumTrack;
import com.scriptles.cabinet.media.entity.BookDetails;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.repository.AlbumDetailsRepository;
import com.scriptles.cabinet.media.repository.AlbumTrackRepository;
import com.scriptles.cabinet.media.repository.BookDetailsRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.MovieDetailsRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.media.repository.TrackDetailsRepository;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.entity.User;
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
    private final SeriesEpisodeRepository seriesEpisodeRepository;
    private final TrackDetailsRepository trackDetailsRepository;
    private final RatingRepository ratingRepository;
    private final MediaLikeRepository mediaLikeRepository;
    private final MediaListItemRepository mediaListItemRepository;
    private final UserMediaRepository userMediaRepository;
    private final MediaCreditService mediaCreditService;
    private final RatingSummaryService ratingSummaryService;
    private final UserArtworkResolver userArtworkResolver;

    public ExternalMediaDetailsResponse findDetails(UUID mediaId) {
        return findDetails(mediaId, null);
    }

    public ExternalMediaDetailsResponse findDetails(UUID mediaId, UUID userId) {
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
        MediaCreditService.CreditSummary creditSummary = mediaCreditService.summary(media);
        UserArtworkResolver.ResolvedArtwork artwork = userArtworkResolver == null
                ? canonicalArtwork(media)
                : userArtworkResolver.resolve(userId, media);

        return new ExternalMediaDetailsResponse(
                media.getId(),
                externalId,
                source,
                media.getType(),
                media.getTitle(),
                media.getOriginalTitle(),
                creditSummary.creator(),
                media.getDescription(),
                media.getTagline(),
                artwork.coverUrl(),
                artwork.backdropUrl(),
                media.getLogoUrl(),
                externalUrl,
                media.getReleaseDate(),
                media.getOriginalLanguage(),
                media.getCountryCode(),
                media.getWikidataId(),
                externalReferences,
                genres,
                creditSummary.credits().stream().map(this::toCreditResponse).toList(),
                true,
                community.likeCount(),
                community.recentLikers(),
                community.averageRating(),
                community.ratingDistribution(),
                community.listCount(),
                community.completedCount(),
                community.recentCompleters(),
                details(media, creditSummary, userId)
        );
    }

    private UserArtworkResolver.ResolvedArtwork canonicalArtwork(Media media) {
        return new UserArtworkResolver.ResolvedArtwork(
                media.getCoverUrl(), media.getBackdropUrl(), false, false);
    }

    public PageResponse<ExternalMediaDetailsResponse.CreditResponse> findCredits(
            UUID mediaId,
            CreditRole role,
            int page,
            int limit
    ) {
        if (!mediaRepository.existsById(mediaId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada");
        }
        return PageResponse.from(mediaCreditService.findByRole(mediaId, role, page, limit)
                .map(this::toCreditResponse));
    }

    private Object details(Media media, MediaCreditService.CreditSummary creditSummary, UUID userId) {
        return switch (media.getType()) {
            case MOVIE -> movieDetailsRepository.findById(media.getId())
                    .map(details -> new ExternalMediaDetailsResponse.MovieDetails(
                            details.getRuntimeMinutes(),
                            details.getBudget(),
                            details.getRevenue(),
                            creditSummary.director()
                    ))
                    .orElseGet(() -> new ExternalMediaDetailsResponse.MovieDetails(
                            null, null, null, creditSummary.director()));
            case TRACK -> trackDetailsRepository.findById(media.getId())
                    .map(details -> new ExternalMediaDetailsResponse.TrackDetails(
                            details.getDurationSeconds(),
                            details.getExplicit()
                    ))
                    .orElseGet(() -> new ExternalMediaDetailsResponse.TrackDetails(null, null));
            case ALBUM -> albumDetailsRepository.findById(media.getId())
                    .map(details -> {
                        List<AlbumTrack> tracks = albumTrackRepository
                                .findAllByAlbumIdOrderByDiscNumberAscTrackNumberAsc(media.getId());
                        Map<UUID, RatingSummaryService.ItemStats> stats = ratingSummaryService.items(
                                tracks.stream().map(t -> t.getTrackMedia().getId()).toList(), userId);
                        return new ExternalMediaDetailsResponse.AlbumDetails(
                                details.getAlbumType() == null ? null : details.getAlbumType().name(),
                                details.getNumberOfTracks(), details.getAnimatedCoverUrl(), tracks.stream()
                                .map(track -> toTrackResponse(track, stats.getOrDefault(
                                        track.getTrackMedia().getId(), RatingSummaryService.ItemStats.empty())))
                                .toList());
                    })
                    .orElseGet(() -> new ExternalMediaDetailsResponse.AlbumDetails(null, null, null, List.of()));
            case SERIES -> seriesDetailsRepository.findById(media.getId())
                    .map(details -> new ExternalMediaDetailsResponse.SeriesDetails(
                            details.getStatus() == null ? null : details.getStatus().name(),
                            details.getNumberOfSeasons(),
                            details.getNumberOfEpisodes(),
                            details.getLastAirDate(),
                            seriesSeasonRepository.findAllBySeriesIdOrderBySeasonNumberAsc(media.getId())
                                    .stream()
                                    .map(season -> toSeasonResponse(season, userId))
                                    .toList()
                    ))
                    .orElseGet(() -> new ExternalMediaDetailsResponse.SeriesDetails(
                            null, null, null, null, List.of()));
            case BOOK -> bookDetailsRepository.findById(media.getId())
                    .map(this::toBookDetails)
                    .orElseGet(() -> new ExternalMediaDetailsResponse.BookDetails(
                            null, null, null, null, media.getWikidataId()));
            case EPISODE -> seriesEpisodeRepository.findByEpisodeMediaId(media.getId())
                    .map(episode -> new ExternalMediaDetailsResponse.TrackDetails(
                            episode.getRuntimeMinutes() == null ? null : episode.getRuntimeMinutes() * 60, false))
                    .orElseGet(() -> new ExternalMediaDetailsResponse.TrackDetails(null, false));
        };
    }

    private ExternalMediaDetailsResponse.TrackResponse toTrackResponse(
            AlbumTrack track, RatingSummaryService.ItemStats stats) {
        return new ExternalMediaDetailsResponse.TrackResponse(
                track.getTrackMedia().getId(),
                track.getExternalId(),
                track.getTitle(),
                track.getDiscNumber(),
                track.getTrackNumber(),
                track.getDurationSeconds(),
                track.getExplicit(),
                stats.averageRating(),
                stats.ratingCount(),
                stats.myRating()
        );
    }

    private ExternalMediaDetailsResponse.CreditResponse toCreditResponse(MediaCreditService.CreditView credit) {
        return new ExternalMediaDetailsResponse.CreditResponse(
                credit.personId(),
                credit.name(),
                credit.role(),
                credit.characterName(),
                credit.position(),
                credit.imageUrl(),
                credit.source(),
                credit.externalId()
        );
    }

    private ExternalMediaDetailsResponse.SeasonResponse toSeasonResponse(SeriesSeason season, UUID userId) {
        RatingSummaryService.SeasonStats stats = ratingSummaryService.season(
                seriesEpisodeRepository.findAllBySeasonIdOrderByEpisodeNumberAsc(season.getId()), userId);
        return new ExternalMediaDetailsResponse.SeasonResponse(
                season.getId(),
                season.getExternalId(),
                season.getSeasonNumber(),
                season.getName(),
                season.getDescription(),
                season.getCoverUrl(),
                season.getEpisodeCount(),
                season.getAirDate(),
                stats.averageRating(),
                stats.ratingCount(),
                stats.myRating(),
                stats.myRatedEpisodeCount(),
                stats.eligibleEpisodeCount()
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
        Double averageRating = ratingRepository.summarizeRatings(List.of(mediaId), Visibility.PUBLIC)
                .stream()
                .findFirst()
                .map(RatingRepository.MediaRatingProjection::getAverageRating)
                .orElse(null);
        Map<BigDecimal, Long> ratingCounts = ratingRepository
                .ratingDistribution(mediaId, Visibility.PUBLIC)
                .stream()
                .collect(Collectors.toMap(
                        projection -> projection.getRating().stripTrailingZeros(),
                        RatingRepository.RatingDistributionProjection::getRatingCount
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
                mediaLikeRepository.findTop3ByMediaIdOrderByLikedAtDescIdDesc(mediaId)
                        .stream()
                        .map(like -> toCommunityUser(like.getUser()))
                        .toList(),
                averageRating,
                List.copyOf(ratingDistribution),
                mediaListItemRepository.countByMediaIdAndListVisibility(mediaId, Visibility.PUBLIC),
                userMediaRepository.countByMediaIdAndStatusAndPrivateEntryFalse(
                        mediaId,
                        UserMediaStatus.COMPLETED
                ),
                userMediaRepository
                        .findTop3ByMediaIdAndStatusAndPrivateEntryFalseAndCompletedAtIsNotNullOrderByCompletedAtDescIdDesc(
                                mediaId,
                                UserMediaStatus.COMPLETED
                        )
                        .stream()
                        .map(entry -> toCommunityUser(entry.getUser()))
                        .toList()
        );
    }

    private MediaCommunityUserResponse toCommunityUser(User user) {
        return new MediaCommunityUserResponse(user.getId(), user.getUsername(), user.getAvatarUlr());
    }

    private record CommunityStats(
            long likeCount,
            List<MediaCommunityUserResponse> recentLikers,
            Double averageRating,
            List<ExternalMediaDetailsResponse.RatingDistributionBucket> ratingDistribution,
            long listCount,
            long completedCount,
            List<MediaCommunityUserResponse> recentCompleters
    ) {
    }
}
