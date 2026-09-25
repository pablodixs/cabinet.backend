package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.CursorPageResponse;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.dto.response.AlbumTracksResponse;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.MediaCommunityUserResponse;
import com.scriptles.cabinet.media.dto.response.MediaCommunityResponse;
import com.scriptles.cabinet.media.dto.response.PublicMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.UserMediaStateResponse;
import com.scriptles.cabinet.catalog.api.CollectionSummaryResponse;
import com.scriptles.cabinet.catalog.api.FranchiseSummaryResponse;
import com.scriptles.cabinet.catalog.entity.CollectionItem;
import com.scriptles.cabinet.catalog.entity.FranchiseMedia;
import com.scriptles.cabinet.catalog.repository.CollectionItemRepository;
import com.scriptles.cabinet.catalog.repository.FranchiseMediaRepository;
import com.scriptles.cabinet.media.catalog.GenreCatalogService;
import com.scriptles.cabinet.media.entity.AlbumTrack;
import com.scriptles.cabinet.media.entity.BookDetails;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MediaDetailLevel;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.AlbumDetailsRepository;
import com.scriptles.cabinet.media.repository.AlbumTrackRepository;
import com.scriptles.cabinet.media.repository.AlbumReleaseVersionRepository;
import com.scriptles.cabinet.media.repository.BookDetailsRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaCommunityStatsRepository;
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
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserAlbumRotationRepository;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.EnumSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaQueryService {
    private final MediaRepository mediaRepository;
    private final GenreCatalogService genreCatalogService;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final AlbumDetailsRepository albumDetailsRepository;
    private final BookDetailsRepository bookDetailsRepository;
    private final MovieDetailsRepository movieDetailsRepository;
    private final SeriesDetailsRepository seriesDetailsRepository;
    private final AlbumTrackRepository albumTrackRepository;
    private final AlbumReleaseVersionRepository albumReleaseVersionRepository;
    private final SeriesSeasonRepository seriesSeasonRepository;
    private final SeriesEpisodeRepository seriesEpisodeRepository;
    private final TrackDetailsRepository trackDetailsRepository;
    private final RatingRepository ratingRepository;
    private final MediaCommunityStatsRepository mediaCommunityStatsRepository;
    private final ReviewRepository reviewRepository;
    private final MediaLikeRepository mediaLikeRepository;
    private final MediaListItemRepository mediaListItemRepository;
    private final UserMediaRepository userMediaRepository;
    private final UserMediaActivityRepository userMediaActivityRepository;
    private final UserAlbumRotationRepository userAlbumRotationRepository;
    private CollectionItemRepository collectionItemRepository;
    private FranchiseMediaRepository franchiseMediaRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void setCatalogRepositories(CollectionItemRepository items, FranchiseMediaRepository media) { this.collectionItemRepository = items; this.franchiseMediaRepository = media; }
    private final MediaCreditService mediaCreditService;
    private final RatingSummaryService ratingSummaryService;
    private final UserArtworkResolver userArtworkResolver;
    private final CatalogLocaleResolver catalogLocaleResolver;
    private final CatalogTranslationLoader catalogTranslationLoader;
    private final MediaTranslationResolver mediaTranslationResolver;
    private final MediaPublicVersionService mediaPublicVersionService;
    private final AlbumMediaPageCursorCodec albumMediaPageCursorCodec;
    private CatalogMetadataRefreshScheduler metadataRefreshScheduler;

    @org.springframework.beans.factory.annotation.Autowired
    void setMetadataRefreshScheduler(CatalogMetadataRefreshScheduler metadataRefreshScheduler) {
        this.metadataRefreshScheduler = metadataRefreshScheduler;
    }

    public PublicMediaDetailsResponse findDetails(UUID mediaId) {
        return findDetails(mediaId, "pt-BR");
    }

    public PublicMediaDetailsResponse findDetails(UUID mediaId, String locale) {
        String publicVersion = mediaPublicVersionService.currentVersion(mediaId, locale).orElse("missing");
        return findDetails(mediaId, locale, publicVersion);
    }

    @Cacheable(
            cacheNames = "mediaDetails",
            key = "#mediaId + ':' + #locale + ':' + #publicVersion",
            unless = "#result.translationFallback() || "
                    + "#result.catalogStatus() != T(com.scriptles.cabinet.media.enums.CatalogStatus).READY"
    )
    public PublicMediaDetailsResponse findDetails(UUID mediaId, String locale, String publicVersion) {
        return findDetails(mediaId, locale, publicVersion, MediaDetailLevel.FULL);
    }

    @Cacheable(
            cacheNames = "mediaDetails",
            key = "#mediaId + ':' + #locale + ':' + #publicVersion + ':' + #detailLevel",
            unless = "#result.translationFallback() || "
                    + "#result.catalogStatus() != T(com.scriptles.cabinet.media.enums.CatalogStatus).READY"
    )
    public PublicMediaDetailsResponse findDetails(
            UUID mediaId,
            String locale,
            String publicVersion,
            MediaDetailLevel detailLevel
    ) {
        if (metadataRefreshScheduler != null) {
            metadataRefreshScheduler.scheduleIfStale(mediaId);
        }
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
        List<ExternalMediaDetailsResponse.GenreResponse> genres = genreCatalogService.forMedia(mediaId, requestedLocale).stream()
                .map(genre -> new ExternalMediaDetailsResponse.GenreResponse(
                        genre.id().toString(), genre.name(), ExternalSource.MANUAL))
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
                true,
                details(media, creditSummary, detailLevel),
                requestedLocale,
                translation.resolvedLocale(),
                translation.fallback(),
                media.getCatalogStatus(),
                (collectionItemRepository == null ? List.<CollectionItem>of() : collectionItemRepository.findByMediaId(mediaId)).stream()
                        .map(item -> new CollectionSummaryResponse(
                                item.getCollection().getId(), item.getCollection().getSlug(), item.getCollection().getTitle(),
                                item.getCollection().getType().name(), item.getPosition(),
                                collectionItemRepository.findByCollectionIdOrderByPositionAsc(item.getCollection().getId()).size()))
                        .distinct().toList(),
                (franchiseMediaRepository == null ? List.<FranchiseMedia>of() : franchiseMediaRepository.findByMediaId(mediaId)).stream()
                        .map(link -> new FranchiseSummaryResponse(link.getFranchise().getId(), link.getFranchise().getSlug(), link.getFranchise().getName(), link.getFranchise().getType().name()))
                        .distinct().toList()
        );
    }

    public String currentPublicVersion(UUID mediaId, String locale) {
        return mediaPublicVersionService.currentVersion(mediaId, locale).orElse(null);
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
                List.of(),
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
        java.util.Set<UUID> likedIds = userId == null || trackIds.isEmpty()
                ? java.util.Set.of()
                : java.util.Set.copyOf(mediaLikeRepository.findLikedMediaIds(userId, trackIds));

        return new AlbumTracksResponse(tracks.stream()
                .map(track -> toTrackResponse(
                        track,
                        stats.getOrDefault(track.getTrackMedia().getId(), RatingSummaryService.ItemStats.empty()),
                        likedIds.contains(track.getTrackMedia().getId())
                ))
                .toList());
    }

    public CursorPageResponse<ExternalMediaDetailsResponse.TrackResponse> findAlbumTrackPage(
            UUID albumId, UUID userId, String cursor, int limit) {
        requireAlbum(albumId);
        AlbumMediaPageCursorCodec.TrackPosition position = cursor == null || cursor.isBlank()
                ? null
                : albumMediaPageCursorCodec.decodeTrack(cursor);
        List<AlbumTrack> fetched = albumTrackRepository.findAlbumPageAfter(
                albumId,
                position == null,
                position != null && position.discNumber() == null,
                position == null ? null : position.discNumber(),
                position != null && position.trackNumber() == null,
                position == null ? null : position.trackNumber(),
                position == null ? new UUID(0, 0) : position.id(),
                org.springframework.data.domain.PageRequest.of(0, limit + 1));
        boolean hasMore = fetched.size() > limit;
        List<AlbumTrack> page = hasMore ? fetched.subList(0, limit) : fetched;
        List<UUID> trackIds = page.stream().map(track -> track.getTrackMedia().getId()).toList();
        Map<UUID, RatingSummaryService.ItemStats> stats = ratingSummaryService.items(trackIds, userId);
        java.util.Set<UUID> likedIds = userId == null || trackIds.isEmpty()
                ? java.util.Set.of()
                : java.util.Set.copyOf(mediaLikeRepository.findLikedMediaIds(userId, trackIds));
        List<ExternalMediaDetailsResponse.TrackResponse> items = page.stream()
                .map(track -> toTrackResponse(track,
                        stats.getOrDefault(track.getTrackMedia().getId(), RatingSummaryService.ItemStats.empty()),
                        likedIds.contains(track.getTrackMedia().getId())))
                .toList();
        String nextCursor = hasMore && !page.isEmpty()
                ? albumMediaPageCursorCodec.encode(page.getLast())
                : null;
        return new CursorPageResponse<>(items, nextCursor, hasMore);
    }

    public CursorPageResponse<ExternalMediaDetailsResponse.ReleaseVersionResponse> findAlbumReleaseVersionPage(
            UUID albumId, String cursor, int limit) {
        requireAlbum(albumId);
        AlbumMediaPageCursorCodec.VersionPosition position = cursor == null || cursor.isBlank()
                ? null
                : albumMediaPageCursorCodec.decodeVersion(cursor);
        List<com.scriptles.cabinet.media.entity.AlbumReleaseVersion> fetched = albumReleaseVersionRepository
                .findPageForAlbumAfter(albumId, position == null ? null : position.id(),
                        org.springframework.data.domain.PageRequest.of(0, limit + 1));
        boolean hasMore = fetched.size() > limit;
        List<com.scriptles.cabinet.media.entity.AlbumReleaseVersion> page = hasMore
                ? fetched.subList(0, limit)
                : fetched;
        List<ExternalMediaDetailsResponse.ReleaseVersionResponse> items = page.stream()
                .map(this::toReleaseVersionResponse)
                .toList();
        String nextCursor = hasMore && !page.isEmpty()
                ? albumMediaPageCursorCodec.encode(page.getLast())
                : null;
        return new CursorPageResponse<>(items, nextCursor, hasMore);
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

    private Object details(Media media, MediaCreditService.CreditSummary creditSummary,
                           MediaDetailLevel detailLevel) {
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
                        boolean includeExtendedData = detailLevel == MediaDetailLevel.FULL;
                        List<AlbumTrack> tracks = includeExtendedData
                                ? albumTrackRepository.findAllByAlbumIdOrderByDiscNumberAscTrackNumberAsc(media.getId())
                                : List.of();
                        return new ExternalMediaDetailsResponse.AlbumDetails(
                                details.getAlbumType() == null ? null : details.getAlbumType().name(),
                                details.getNumberOfTracks(), details.getAnimatedCoverUrl(), tracks.stream()
                                .map(track -> toTrackResponse(
                                        track, RatingSummaryService.ItemStats.empty(), false))
                                .toList(), includeExtendedData ? releaseVersions(media.getId()) : List.of());
                    })
                    .orElseGet(() -> new ExternalMediaDetailsResponse.AlbumDetails(
                            null, null, null, List.of(), detailLevel == MediaDetailLevel.FULL
                            ? releaseVersions(media.getId()) : List.of()));
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

    private List<ExternalMediaDetailsResponse.ReleaseVersionResponse> releaseVersions(UUID albumMediaId) {
        return albumReleaseVersionRepository.findAllForAlbum(albumMediaId).stream()
                .map(this::toReleaseVersionResponse)
                .toList();
    }

    private ExternalMediaDetailsResponse.ReleaseVersionResponse toReleaseVersionResponse(
            com.scriptles.cabinet.media.entity.AlbumReleaseVersion version) {
        return new ExternalMediaDetailsResponse.ReleaseVersionResponse(
                version.getId(), version.getMusicBrainzReleaseId().toString(), version.getTitle(),
                version.getCountryCode(), version.getReleaseDate(), version.getFormat(), version.getStatus(),
                version.getBarcode(), version.getCatalogNumber(), version.getLabelName(), version.getCoverUrl(),
                version.getTrackCount(), version.isPrimary());
    }

    private Media requireAlbum(UUID albumId) {
        Media album = mediaRepository.findById(albumId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada"));
        if (album.getType() != MediaType.ALBUM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_NOT_ALBUM", "A mídia informada não é um álbum");
        }
        return album;
    }

    private ExternalMediaDetailsResponse.TrackResponse toTrackResponse(
            AlbumTrack track, RatingSummaryService.ItemStats stats, boolean liked) {
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
                stats.myRating(),
                liked
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
        var communityStats = mediaCommunityStatsRepository.findByMediaId(mediaId)
                .orElseGet(() -> new MediaCommunityStatsRepository.CommunityStats(
                        0, BigDecimal.ZERO, null, 0, 0, 0));
        List<ExternalMediaDetailsResponse.RatingDistributionBucket> ratingDistribution = ratingBuckets(
                mediaCommunityStatsRepository.findDistribution(mediaId));

        var recentLikes = mediaLikeRepository.findTop3ByMediaIdOrderByLikedAtDescIdDesc(mediaId);
        var recentCompletions = userMediaRepository
                .findTop3ByMediaIdAndStatusAndPrivateEntryFalseAndCompletedAtIsNotNullOrderByCompletedAtDescIdDesc(
                        mediaId, UserMediaStatus.COMPLETED);

        return new MediaCommunityResponse(
                communityStats.likeCount(),
                recentLikes.stream()
                        .map(like -> toCommunityUser(like.getUser()))
                        .toList(),
                communityStats.averageRating() == null ? null : communityStats.averageRating().doubleValue(),
                List.copyOf(ratingDistribution),
                childRatings(media),
                communityStats.listCount(),
                communityStats.completedCount(),
                recentCompletions.stream()
                        .map(entry -> toCommunityUser(entry.getUser()))
                        .toList()
        );
    }

    private List<ExternalMediaDetailsResponse.RatingDistributionBucket> ratingBuckets(
            List<MediaCommunityStatsRepository.RatingBucket> counts
    ) {
        Map<BigDecimal, Long> byRating = counts.stream().collect(Collectors.toMap(
                bucket -> bucket.rating().stripTrailingZeros(),
                MediaCommunityStatsRepository.RatingBucket::ratingCount
        ));
        List<ExternalMediaDetailsResponse.RatingDistributionBucket> buckets = new ArrayList<>(10);
        for (int step = 1; step <= 10; step++) {
            BigDecimal rating = BigDecimal.valueOf(step).divide(BigDecimal.valueOf(2));
            buckets.add(new ExternalMediaDetailsResponse.RatingDistributionBucket(
                    rating.doubleValue(), byRating.getOrDefault(rating.stripTrailingZeros(), 0L)));
        }
        return List.copyOf(buckets);
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

        MediaCommunityStatsRepository.CommunityAggregate aggregate =
                mediaCommunityStatsRepository.aggregateByMediaIds(childIds);
        Double averageRating = aggregate.ratingCount() == 0 ? null : BigDecimal.ZERO
                .add(aggregate.ratingSum())
                .divide(BigDecimal.valueOf(aggregate.ratingCount()), 8, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        return new MediaCommunityResponse.ChildRatingsResponse(
                itemType,
                averageRating,
                aggregate.ratingCount(),
                ratingBuckets(mediaCommunityStatsRepository.aggregateDistributionByMediaIds(childIds))
        );
    }

    public UserMediaStateResponse findUserState(UUID mediaId, UUID userId) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada"));
        var libraryEntry = userMediaRepository.findByUserIdAndMediaId(userId, mediaId).orElse(null);
        var rating = ratingRepository.findByUserIdAndMediaId(userId, mediaId).orElse(null);
        var review = reviewRepository.findByUserIdAndMediaId(userId, mediaId).orElse(null);
        UserArtworkResolver.ResolvedArtwork artwork = userArtworkResolver.resolve(userId, media);
        EnumSet<ProfileActivityType> listenTypes = EnumSet.of(
                ProfileActivityType.LOGGED, ProfileActivityType.RELOGGED);
        EnumSet<ProfileActivityType> logTypes = EnumSet.of(
                ProfileActivityType.LOGGED,
                ProfileActivityType.RELOGGED,
                ProfileActivityType.WATCHED,
                ProfileActivityType.REWATCHED
        );
        long logCount = userMediaActivityRepository.countByUserIdAndMediaIdAndTypeIn(
                userId, mediaId, logTypes);
        java.time.LocalDate lastLoggedOn = userMediaActivityRepository
                .findTopByUserIdAndMediaIdAndTypeInOrderByOccurredOnDescCreatedAtDesc(
                        userId, mediaId, logTypes)
                .map(com.scriptles.cabinet.user.entity.UserMediaActivity::getOccurredOn)
                .orElse(null);
        long listenCount = media.getType() == MediaType.ALBUM || media.getType() == MediaType.TRACK
                ? userMediaActivityRepository.countByUserIdAndMediaIdAndTypeIn(userId, mediaId, listenTypes)
                : 0;
        java.time.LocalDate lastListenedOn = media.getType() == MediaType.ALBUM || media.getType() == MediaType.TRACK
                ? userMediaActivityRepository.findTopByUserIdAndMediaIdAndTypeInOrderByOccurredOnDescCreatedAtDesc(
                        userId, mediaId, listenTypes)
                        .map(com.scriptles.cabinet.user.entity.UserMediaActivity::getOccurredOn).orElse(null)
                : null;
        boolean inRotation = media.getType() == MediaType.ALBUM
                && userAlbumRotationRepository.existsByUserIdAndAlbumId(userId, mediaId);

        return new UserMediaStateResponse(
                mediaLikeRepository.existsByUserIdAndMediaId(userId, mediaId),
                libraryEntry == null ? null : libraryEntry.getStatus(),
                rating == null ? null : rating.getValue().doubleValue(),
                review == null ? null : review.getId(),
                logCount,
                lastLoggedOn,
                mediaListItemRepository.findListIdsByMediaIdAndOwnerId(mediaId, userId),
                artwork.customCover() ? artwork.coverUrl() : null,
                artwork.customBackdrop() ? artwork.backdropUrl() : null,
                listenCount,
                lastListenedOn,
                inRotation
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
