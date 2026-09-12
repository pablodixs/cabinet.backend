package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.dto.response.AlbumTracksResponse;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.MediaCommunityUserResponse;
import com.scriptles.cabinet.media.dto.response.MediaCommunityResponse;
import com.scriptles.cabinet.media.dto.response.PublicMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.UserMediaStateResponse;
import com.scriptles.cabinet.media.entity.AlbumTrack;
import com.scriptles.cabinet.media.entity.BookDetails;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MediaType;
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
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.CatalogTranslationLoader;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import com.scriptles.cabinet.media.translation.ResolvedMediaTranslation;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.Cacheable;
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
    private final ReviewRepository reviewRepository;
    private final MediaLikeRepository mediaLikeRepository;
    private final MediaListItemRepository mediaListItemRepository;
    private final UserMediaRepository userMediaRepository;
    private final MediaCreditService mediaCreditService;
    private final RatingSummaryService ratingSummaryService;
    private final UserArtworkResolver userArtworkResolver;
    private final CatalogLocaleResolver catalogLocaleResolver;
    private final CatalogTranslationLoader catalogTranslationLoader;
    private final MediaTranslationResolver mediaTranslationResolver;

    public PublicMediaDetailsResponse findDetails(UUID mediaId) {
        return findDetails(mediaId, "pt-BR");
    }

    @Cacheable(
            cacheNames = "mediaDetails",
            key = "#mediaId + ':' + #locale",
            unless = "#result.translationFallback() || "
                    + "#result.catalogStatus() != T(com.scriptles.cabinet.media.enums.CatalogStatus).READY"
    )
    public PublicMediaDetailsResponse findDetails(UUID mediaId, String locale) {
        String requestedLocale = catalogLocaleResolver.normalize(locale);
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

        catalogTranslationLoader.loadIfMissing(media, primaryReference, requestedLocale);
        List<ExternalMediaDetailsResponse.GenreResponse> genres = media.getGenres().stream()
                .map(name -> new ExternalMediaDetailsResponse.GenreResponse(null, name, ExternalSource.MANUAL))
                .toList();
        ResolvedMediaTranslation translation = mediaTranslationResolver.resolve(media, requestedLocale);
        MediaCreditService.CreditSummary creditSummary = mediaCreditService.summary(media);
        return new PublicMediaDetailsResponse(
                media.getId(),
                externalId,
                source,
                media.getType(),
                translation.title(),
                media.getOriginalTitle(),
                creditSummary.creator(),
                translation.description(),
                translation.tagline(),
                translation.coverUrl(),
                media.getBackdropUrl(),
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
                details(media, creditSummary),
                requestedLocale,
                translation.resolvedLocale(),
                translation.fallback(),
                media.getCatalogStatus()
        );
    }

    public ExternalMediaDetailsResponse findLegacyDetails(UUID mediaId) {
        PublicMediaDetailsResponse media = findDetails(mediaId);
        MediaCommunityResponse community = findCommunity(mediaId);
        return new ExternalMediaDetailsResponse(
                media.id(),
                media.externalId(),
                media.source(),
                media.type(),
                media.title(),
                media.originalTitle(),
                media.creator(),
                media.description(),
                media.tagline(),
                media.coverUrl(),
                media.backdropUrl(),
                media.logoUrl(),
                media.externalUrl(),
                media.releaseDate(),
                media.originalLanguage(),
                media.countryCode(),
                media.wikidataId(),
                media.externalReferences(),
                media.genres(),
                media.credits(),
                media.imported(),
                community.likeCount(),
                community.recentLikers(),
                community.averageRating(),
                community.ratingDistribution(),
                community.listCount(),
                community.completedCount(),
                community.recentCompleters(),
                media.details()
        );
    }

    public AlbumTracksResponse findAlbumTracks(UUID albumId, UUID userId) {
        Media album = mediaRepository.findById(albumId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Mídia não encontrada"
        ));
        if (album.getType() != MediaType.ALBUM) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MEDIA_NOT_ALBUM",
                    "A mídia informada não é um álbum"
            );
        }

        List<AlbumTrack> tracks = albumTrackRepository
                .findAllByAlbumIdOrderByDiscNumberAscTrackNumberAsc(albumId);
        List<UUID> trackIds = tracks.stream()
                .map(track -> track.getTrackMedia().getId())
                .toList();
        Map<UUID, RatingSummaryService.ItemStats> stats = ratingSummaryService.items(trackIds, userId);

        return new AlbumTracksResponse(tracks.stream()
                .map(track -> toTrackResponse(
                        track,
                        stats.getOrDefault(track.getTrackMedia().getId(), RatingSummaryService.ItemStats.empty())
                ))
                .toList());
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

    private Object details(Media media, MediaCreditService.CreditSummary creditSummary) {
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
                        return new ExternalMediaDetailsResponse.AlbumDetails(
                                details.getAlbumType() == null ? null : details.getAlbumType().name(),
                                details.getNumberOfTracks(), details.getAnimatedCoverUrl(), tracks.stream()
                                .map(track -> toTrackResponse(
                                        track, RatingSummaryService.ItemStats.empty()))
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
                                    .map(this::toSeasonResponse)
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

    private ExternalMediaDetailsResponse.SeasonResponse toSeasonResponse(SeriesSeason season) {
        RatingSummaryService.SeasonStats stats = RatingSummaryService.SeasonStats.empty();
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

    @Cacheable(cacheNames = "mediaCommunity", key = "#mediaId")
    public MediaCommunityResponse findCommunity(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada"));
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

        var recentLikes = mediaLikeRepository.findTop3ByMediaIdOrderByLikedAtDescIdDesc(mediaId);
        var recentCompletions = userMediaRepository
                .findTop3ByMediaIdAndStatusAndPrivateEntryFalseAndCompletedAtIsNotNullOrderByCompletedAtDescIdDesc(
                        mediaId, UserMediaStatus.COMPLETED);

        return new MediaCommunityResponse(
                mediaLikeRepository.countByMediaId(mediaId),
                recentLikes.stream()
                        .map(like -> toCommunityUser(like.getUser()))
                        .toList(),
                averageRating,
                List.copyOf(ratingDistribution),
                childRatings(media),
                mediaListItemRepository.countByMediaIdAndListVisibility(mediaId, Visibility.PUBLIC),
                userMediaRepository.countByMediaIdAndStatusAndPrivateEntryFalse(
                        mediaId,
                        UserMediaStatus.COMPLETED
                ),
                recentCompletions.stream()
                        .map(entry -> toCommunityUser(entry.getUser()))
                        .toList()
        );
    }

    private MediaCommunityResponse.ChildRatingsResponse childRatings(Media media) {
        List<UUID> childIds;
        MediaType itemType;
        if (media.getType() == MediaType.ALBUM) {
            itemType = MediaType.TRACK;
            childIds = albumTrackRepository.findTrackMediaIdsByAlbumId(media.getId());
        } else if (media.getType() == MediaType.SERIES) {
            itemType = MediaType.EPISODE;
            childIds = seriesEpisodeRepository.findEligibleEpisodeMediaIdsBySeriesId(
                    media.getId(), CabinetTime.today());
        } else {
            return null;
        }

        RatingSummaryService.AggregateStats stats = ratingSummaryService.aggregate(childIds);
        return new MediaCommunityResponse.ChildRatingsResponse(
                itemType,
                stats.averageRating(),
                stats.ratingCount(),
                stats.ratingDistribution()
        );
    }

    public UserMediaStateResponse findUserState(UUID mediaId, UUID userId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada"));
        var libraryEntry = userMediaRepository.findByUserIdAndMediaId(userId, mediaId).orElse(null);
        var rating = ratingRepository.findByUserIdAndMediaId(userId, mediaId).orElse(null);
        var review = reviewRepository.findByUserIdAndMediaId(userId, mediaId).orElse(null);
        UserArtworkResolver.ResolvedArtwork artwork = userArtworkResolver.resolve(userId, media);

        return new UserMediaStateResponse(
                mediaLikeRepository.existsByUserIdAndMediaId(userId, mediaId),
                libraryEntry == null ? null : libraryEntry.getStatus(),
                rating == null ? null : rating.getValue().doubleValue(),
                review == null ? null : review.getId(),
                mediaListItemRepository.findListIdsByMediaIdAndOwnerId(mediaId, userId),
                artwork.customCover() ? artwork.coverUrl() : null,
                artwork.customBackdrop() ? artwork.backdropUrl() : null
        );
    }

    private MediaCommunityUserResponse toCommunityUser(User user) {
        return new MediaCommunityUserResponse(
                user.getId(),
                user.getUsername(),
                user.getAvatarUlr(),
                user.getAccountTier() == com.scriptles.cabinet.user.enums.AccountTier.PRO
        );
    }

}
