package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.dto.request.ImportExternalMediaRequest;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.ExternalMediaResponse;
import com.scriptles.cabinet.media.dto.response.MediaCommunityUserResponse;
import com.scriptles.cabinet.media.dto.response.RelatedMediaResponse;
import com.scriptles.cabinet.media.dto.response.SeasonEpisodesResponse;
import com.scriptles.cabinet.media.entity.AlbumDetails;
import com.scriptles.cabinet.media.entity.AlbumTrack;
import com.scriptles.cabinet.media.entity.BookDetails;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaRelation;
import com.scriptles.cabinet.media.entity.MovieDetails;
import com.scriptles.cabinet.media.entity.SeriesDetails;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.entity.TrackDetails;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.AlbumType;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.SeriesStatus;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.ExternalMediaProvider;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.external.WikidataClient;
import com.scriptles.cabinet.media.repository.AlbumDetailsRepository;
import com.scriptles.cabinet.media.repository.AlbumTrackRepository;
import com.scriptles.cabinet.media.repository.BookDetailsRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.MediaRelationRepository;
import com.scriptles.cabinet.media.repository.MovieDetailsRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.media.repository.TrackDetailsRepository;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalMediaService {
    private final ExternalMediaProviderRegistry providerRegistry;
    private final MediaRepository mediaRepository;
    private final MediaRelationRepository mediaRelationRepository;
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
    private final WikidataClient wikidataClient;
    private final TmdbClient tmdbClient;
    private final MediaQueryService mediaQueryService;
    private final MediaCreditService mediaCreditService;
    private final UserArtworkResolver userArtworkResolver;

    @Transactional(readOnly = true)
    public List<ExternalMediaResponse> search(MediaType mediaType, String query, String language, int offset, int limit) {
        if (mediaType == null) {
            List<ExternalMedia> tmdbResults = providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)
                    .searchAll(query, language, offset, limit);
            List<ExternalMedia> albumResults = searchAlbums(query, offset, limit);
            List<ExternalMedia> bookResults = searchBooks(query, language, offset, limit);
            return toSearchResponses(interleave(List.of(tmdbResults, albumResults, bookResults), limit));
        }
        ExternalMediaProvider provider = providerRegistry.get(defaultSource(mediaType), mediaType);
        return toSearchResponses(provider.search(mediaType, query, language, offset, limit));
    }

    @Transactional(readOnly = true)
    public ExternalMediaDetailsResponse findDetails(
            ExternalSource source,
            MediaType mediaType,
            String externalId,
            String language
    ) {
        ExternalReference storedReference = externalReferenceRepository
                .findBySourceAndExternalId(source, externalId)
                .orElse(null);
        if (storedReference != null) {
            return mediaQueryService.findDetails(storedReference.getMedia().getId());
        }

        ExternalMedia external = providerRegistry.get(source, mediaType)
                .findById(mediaType, externalId, language)
                .orElseThrow(() -> new IllegalArgumentException("External media not found"));
        Optional<WikidataClient.WikidataEnrichment> enrichment = findWikidataEnrichment(
                external, source, mediaType, externalId, language);
        if (enrichment.isPresent()) {
            external = external.withEnrichment(enrichment.get().logoUrl(), enrichment.get().genres());
        }

        UUID importedId = externalReferenceRepository.findBySourceAndExternalId(source, externalId)
                .map(reference -> reference.getMedia().getId())
                .orElse(null);
        String wikidataId = preferredWikidataId(external, enrichment);
        String canonicalWorkWikidataId = canonicalWorkWikidataId(importedId, mediaType, wikidataId, enrichment);
        Map<String, String> references = new LinkedHashMap<>();
        references.put(source.name().toLowerCase(), externalId);
        if (wikidataId != null) references.put("wikidata", wikidataId);
        MediaCommunityStats communityStats = communityStats(importedId);

        return new ExternalMediaDetailsResponse(
                importedId,
                external.externalId(),
                external.source(),
                external.type(),
                external.title(),
                external.originalTitle(),
                external.creator(),
                external.description(),
                external.tagline(),
                external.coverUrl(),
                external.backdropUrl(),
                external.logoUrl(),
                external.externalUrl(),
                external.releaseDate(),
                external.originalLanguage(),
                external.countryCode(),
                wikidataId,
                references,
                external.genres().stream().map(genre -> new ExternalMediaDetailsResponse.GenreResponse(
                        genre.id(), genre.name(), genre.source())).toList(),
                toCreditResponses(external.credits()),
                importedId != null,
                communityStats.likeCount(),
                communityStats.recentLikers(),
                communityStats.averageRating(),
                communityStats.ratingDistribution(),
                communityStats.listCount(),
                communityStats.completedCount(),
                communityStats.recentCompleters(),
                details(external, canonicalWorkWikidataId)
        );
    }

    private MediaCommunityStats communityStats(UUID mediaId) {
        if (mediaId == null) {
            return MediaCommunityStats.empty();
        }

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
        long likeCount = mediaLikeRepository.countByMediaId(mediaId);
        List<MediaCommunityUserResponse> recentLikers = mediaLikeRepository
                .findTop3ByMediaIdOrderByLikedAtDescIdDesc(mediaId)
                .stream()
                .map(like -> toCommunityUser(like.getUser()))
                .toList();
        long listCount = mediaListItemRepository
                .countByMediaIdAndListVisibility(mediaId, Visibility.PUBLIC);
        long completedCount = userMediaRepository
                .countByMediaIdAndStatusAndPrivateEntryFalse(mediaId, UserMediaStatus.COMPLETED);
        List<MediaCommunityUserResponse> recentCompleters = userMediaRepository
                .findTop3ByMediaIdAndStatusAndPrivateEntryFalseAndCompletedAtIsNotNullOrderByCompletedAtDescIdDesc(
                        mediaId,
                        UserMediaStatus.COMPLETED
                )
                .stream()
                .map(entry -> toCommunityUser(entry.getUser()))
                .toList();

        return new MediaCommunityStats(
                likeCount,
                recentLikers,
                averageRating,
                List.copyOf(ratingDistribution),
                listCount,
                completedCount,
                recentCompleters
        );
    }

    private MediaCommunityUserResponse toCommunityUser(User user) {
        return new MediaCommunityUserResponse(user.getId(), user.getUsername(), user.getAvatarUlr());
    }

    @Transactional(readOnly = true)
    public RelatedMediaResponse findRelations(
            ExternalSource source,
            MediaType mediaType,
            String externalId,
            String language,
            int maxResults
    ) {
        return findRelations(source, mediaType, externalId, language, maxResults, null);
    }

    @Transactional(readOnly = true)
    public RelatedMediaResponse findRelations(
            ExternalSource source,
            MediaType mediaType,
            String externalId,
            String language,
            int maxResults,
            UUID viewerId
    ) {
        ExternalMedia external = providerRegistry.get(source, mediaType)
                .findById(mediaType, externalId, language)
                .orElseThrow(() -> new IllegalArgumentException("External media not found"));
        WikidataClient.WikidataRelations relations = wikidataClient.findRelations(
                new WikidataClient.RelationLookup(
                        source,
                        mediaType,
                        externalId,
                        external.wikidataId(),
                        external.isbn13(),
                        external.isbn10(),
                        language,
                        maxResults
                )
        );

        List<RelatedMediaResponse.Item> manualItems = manualRelations(source, externalId);

        if (relations.items().isEmpty()) {
            return new RelatedMediaResponse(
                    manualItems.isEmpty() ? ExternalSource.WIKIDATA : ExternalSource.MANUAL,
                    relations.incomplete(),
                    applyArtwork(manualItems, viewerId)
            );
        }

        Set<ExternalSource> sources = relations.items().stream()
                .map(WikidataClient.RelatedMedia::providerSource)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        sources.add(ExternalSource.WIKIDATA);
        Set<String> externalIds = relations.items().stream()
                .flatMap(item -> java.util.stream.Stream.of(item.providerExternalId(), item.wikidataId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<ExternalKey, UUID> importedReferences = externalReferenceRepository
                .findAllBySourceInAndExternalIdIn(sources, externalIds)
                .stream()
                .collect(Collectors.toMap(
                        reference -> new ExternalKey(reference.getSource(), reference.getExternalId()),
                        reference -> reference.getMedia().getId(),
                        (first, ignored) -> first
                ));

        Set<String> relatedBookWorkIds = relations.items().stream()
                .filter(item -> item.type() == MediaType.BOOK)
                .map(WikidataClient.RelatedMedia::wikidataId)
                .collect(Collectors.toSet());
        Map<String, UUID> importedBookWorkIds = relatedBookWorkIds.isEmpty()
                ? Map.of()
                : bookDetailsRepository.findAllByCanonicalWorkWikidataIdIn(relatedBookWorkIds).stream()
                .collect(Collectors.toMap(
                        BookDetails::getCanonicalWorkWikidataId,
                        BookDetails::getId,
                        (first, ignored) -> first
                ));

        List<RelatedMediaResponse.Item> items = new ArrayList<>(relations.items().stream()
                .map(item -> {
                    UUID importedId = importedId(item, importedReferences, importedBookWorkIds);
                    return new RelatedMediaResponse.Item(
                            importedId,
                            item.relationType(),
                            item.type(),
                            item.title(),
                            item.releaseDate(),
                            item.coverUrl(),
                            item.wikidataId(),
                            item.providerSource(),
                            item.providerExternalId(),
                            item.externalUrl(),
                            importedId != null
                    );
                })
                .toList());
        Set<String> automaticKeys = items.stream()
                .map(item -> item.relationType() + ":" + (item.id() != null ? item.id() : item.wikidataId()))
                .collect(Collectors.toSet());
        manualItems.stream()
                .filter(item -> automaticKeys.add(item.relationType() + ":" + item.id()))
                .forEach(items::add);
        return new RelatedMediaResponse(
                ExternalSource.WIKIDATA, relations.incomplete(), applyArtwork(items, viewerId));
    }

    private List<RelatedMediaResponse.Item> applyArtwork(
            List<RelatedMediaResponse.Item> items,
            UUID viewerId
    ) {
        if (viewerId == null || items.isEmpty()) return items;
        Map<UUID, Media> mediaById = mediaRepository.findAllById(items.stream()
                        .map(RelatedMediaResponse.Item::id)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Media::getId, java.util.function.Function.identity()));
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = userArtworkResolver.resolve(
                viewerId, mediaById.values());
        return items.stream().map(item -> {
            UserArtworkResolver.ResolvedArtwork artwork = item.id() == null ? null : artworks.get(item.id());
            if (artwork == null) return item;
            return new RelatedMediaResponse.Item(
                    item.id(), item.relationType(), item.type(), item.title(), item.releaseDate(),
                    artwork.coverUrl(), item.wikidataId(), item.providerSource(), item.providerExternalId(),
                    item.externalUrl(), item.imported()
            );
        }).toList();
    }

    private List<RelatedMediaResponse.Item> manualRelations(ExternalSource source, String externalId) {
        UUID mediaId = externalReferenceRepository.findBySourceAndExternalId(source, externalId)
                .map(reference -> reference.getMedia().getId())
                .orElse(null);
        if (mediaId == null) {
            return List.of();
        }

        return mediaRelationRepository.findAllBySourceMediaIdOrderByCreatedAtAsc(mediaId).stream()
                .map(this::manualRelationItem)
                .toList();
    }

    private RelatedMediaResponse.Item manualRelationItem(MediaRelation relation) {
        Media target = relation.getTargetMedia();
        ExternalReference reference = externalReferenceRepository.findAllByMediaId(target.getId()).stream()
                .filter(ExternalReference::isPrimaryReference)
                .findFirst()
                .orElse(null);
        return new RelatedMediaResponse.Item(
                target.getId(),
                relation.getRelationType(),
                target.getType(),
                target.getTitle(),
                target.getReleaseDate(),
                target.getCoverUrl(),
                target.getWikidataId(),
                reference == null ? ExternalSource.MANUAL : reference.getSource(),
                reference == null ? target.getId().toString() : reference.getExternalId(),
                reference == null ? null : reference.getExternalUrl(),
                true
        );
    }

    public SeasonEpisodesResponse findSeasonEpisodes(String seriesId, int seasonNumber, String language) {
        return new SeasonEpisodesResponse(null, seriesId, null, seasonNumber,
                null, 0, null, 0, 0,
                tmdbClient.findSeasonEpisodes(seriesId, seasonNumber, language).stream()
                        .map(episode -> new SeasonEpisodesResponse.EpisodeResponse(
                                null, episode.externalId(), episode.episodeNumber(), episode.title(), episode.description(),
                            episode.stillUrl(), episode.airDate(), episode.runtimeMinutes(),
                            null, 0, null, episode.airDate() == null
                                || !episode.airDate().isAfter(java.time.LocalDate.now()), false, 0)).toList());
    }

    private Object details(ExternalMedia external, String canonicalWorkWikidataId) {
        return switch (external.type()) {
            case MOVIE -> new ExternalMediaDetailsResponse.MovieDetails(
                    external.runtimeMinutes(), external.budget(), external.revenue(), external.creator());
            case TRACK -> new ExternalMediaDetailsResponse.TrackDetails(
                    external.durationSeconds(), external.explicit());
            case ALBUM -> new ExternalMediaDetailsResponse.AlbumDetails(
                    external.albumType(), external.numberOfTracks(), null, external.tracks().stream()
                    .map(track -> new ExternalMediaDetailsResponse.TrackResponse(
                            null, track.externalId(), track.title(), track.discNumber(), track.trackNumber(),
                            track.durationSeconds(), track.explicit(), null, 0, null)).toList());
            case SERIES -> new ExternalMediaDetailsResponse.SeriesDetails(
                    external.seriesStatus(), external.numberOfSeasons(), external.numberOfEpisodes(),
                    external.lastAirDate(), external.seasons().stream()
                    .map(season -> new ExternalMediaDetailsResponse.SeasonResponse(
                            null, season.externalId(), season.seasonNumber(), season.name(), season.description(),
                            season.coverUrl(), season.episodeCount(), season.airDate(),
                            null, 0, null, 0, 0)).toList());
            case BOOK -> new ExternalMediaDetailsResponse.BookDetails(
                    external.isbn10(), external.isbn13(), external.pageCount(), external.publisher(),
                    canonicalWorkWikidataId);
            default -> Map.of();
        };
    }

    private List<ExternalMedia> searchAlbums(String query, int offset, int limit) {
        try {
            return providerRegistry.get(ExternalSource.MUSICBRAINZ, MediaType.ALBUM)
                    .search(MediaType.ALBUM, query, null, offset, limit);
        } catch (ExternalMediaException exception) {
            return List.of();
        }
    }

    private List<ExternalMedia> searchBooks(String query, String language, int offset, int limit) {
        try {
            return providerRegistry.get(ExternalSource.GOOGLE_BOOKS, MediaType.BOOK)
                    .search(MediaType.BOOK, query, language, offset, limit);
        } catch (ExternalMediaException exception) {
            return List.of();
        }
    }

    private List<ExternalMedia> interleave(List<List<ExternalMedia>> sources, int limit) {
        List<ExternalMedia> results = new ArrayList<>(limit);
        for (int index = 0; results.size() < limit; index++) {
            boolean added = false;
            for (List<ExternalMedia> source : sources) {
                if (index < source.size() && results.size() < limit) {
                    results.add(source.get(index));
                    added = true;
                }
            }
            if (!added) {
                break;
            }
        }
        return results;
    }

    @Transactional
    public ExternalMediaResponse importMedia(ImportExternalMediaRequest request) {
        ExternalReference existing = externalReferenceRepository
                .findBySourceAndExternalId(request.source(), request.externalId())
                .orElse(null);
        if (existing != null) {
            return toImportedResponse(
                    existing.getMedia(),
                    existing,
                    storedCreatorOrBackfill(existing.getMedia(), request),
                    null,
                    existing.getMedia().getWikidataId()
            );
        }

        ExternalMediaProvider provider = providerRegistry.get(request.source(), request.mediaType());
        ExternalMedia external = provider.findById(request.mediaType(), request.externalId(), "pt-BR")
                .orElseThrow(() -> new IllegalArgumentException("External media not found"));
        Optional<WikidataClient.WikidataEnrichment> enrichment = findWikidataEnrichment(
                external, request.source(), request.mediaType(), request.externalId(), "pt-BR");
        if (enrichment.isPresent()) {
            external = external.withEnrichment(enrichment.get().logoUrl(), enrichment.get().genres());
        }

        String wikidataId = preferredWikidataId(external, enrichment);
        String canonicalWorkWikidataId = canonicalWorkWikidataId(
                null,
                request.mediaType(),
                wikidataId,
                enrichment
        );
        Media unsavedMedia = toMedia(external);
        unsavedMedia.setWikidataId(wikidataId);
        Media media = mediaRepository.save(unsavedMedia);
        saveDetails(media, external, canonicalWorkWikidataId);
        mediaCreditService.save(media, external.credits());

        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(external.source());
        reference.setExternalId(external.externalId());
        reference.setExternalUrl(external.externalUrl());
        reference.setPrimaryReference(true);
        reference.setLastSyncedAt(Instant.now());
        externalReferenceRepository.save(reference);

        Optional.ofNullable(wikidataId).ifPresent(value -> {
            ExternalReference wikidataReference = new ExternalReference();
            wikidataReference.setMedia(media);
            wikidataReference.setSource(ExternalSource.WIKIDATA);
            wikidataReference.setExternalId(value);
            wikidataReference.setExternalUrl("https://www.wikidata.org/wiki/" + value);
            wikidataReference.setPrimaryReference(false);
            wikidataReference.setLastSyncedAt(Instant.now());
            externalReferenceRepository.save(wikidataReference);
        });

        return toImportedResponse(
                media, reference, external.creator(), external.durationSeconds(), wikidataId);
    }

    private String storedCreatorOrBackfill(Media media, ImportExternalMediaRequest request) {
        MediaCreditService.CreditSummary stored = mediaCreditService.summary(media);
        if (!stored.credits().isEmpty()) {
            mediaCreditService.reconcile(media);
            return mediaCreditService.summary(media).creator();
        }

        try {
            Optional<ExternalMedia> external = providerRegistry.get(request.source(), request.mediaType())
                    .findById(request.mediaType(), request.externalId(), "pt-BR");
            if (external.isPresent()) {
                mediaCreditService.save(media, external.get().credits());
                return external.get().creator();
            }
        } catch (ExternalMediaException | IllegalArgumentException exception) {
            log.warn("Unable to backfill credits for imported media {}", media.getId(), exception);
        }
        return stored.creator();
    }

    private ExternalSource defaultSource(MediaType mediaType) {
        return switch (mediaType) {
            case BOOK -> ExternalSource.GOOGLE_BOOKS;
            case MOVIE, SERIES -> ExternalSource.TMDB;
            case TRACK, ALBUM -> ExternalSource.MUSICBRAINZ;
            default -> throw new IllegalArgumentException("External search is unavailable for type " + mediaType);
        };
    }

    private List<ExternalMediaResponse> toSearchResponses(List<ExternalMedia> results) {
        if (results.isEmpty()) {
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
        Map<UUID, MediaCreditService.CreditSummary> creditSummaries = mediaCreditService.summaries(
                references.values().stream().map(ExternalReference::getMedia).distinct().toList()
        );

        return results.stream().map(external -> {
            ExternalReference reference = references.get(new ExternalKey(external.source(), external.externalId()));
            if (reference != null) {
                Media media = reference.getMedia();
                return toImportedResponse(
                        media,
                        reference,
                        external.creator() != null
                                ? external.creator()
                                : creditSummaries.getOrDefault(
                                        media.getId(), MediaCreditService.CreditSummary.empty()).creator(),
                        external.durationSeconds(),
                        media.getWikidataId() != null ? media.getWikidataId() : external.wikidataId()
                );
            }
            return new ExternalMediaResponse(
                        null, external.externalId(), external.source(), external.type(), external.title(),
                        external.creator(), external.description(), external.coverUrl(), external.releaseDate(),
                        external.durationSeconds(), external.wikidataId(), false
                );
        }).toList();
    }

    private Media toMedia(ExternalMedia external) {
        Media media = new Media();
        media.setType(external.type());
        media.setTitle(external.title());
        media.setOriginalTitle(external.originalTitle());
        media.setDescription(external.description());
        media.setTagline(external.tagline());
        media.setCoverUrl(external.coverUrl());
        media.setBackdropUrl(external.backdropUrl());
        media.setLogoUrl(external.logoUrl());
        media.setGenres(external.genres().stream().map(ExternalMedia.ExternalGenre::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
        media.setReleaseDate(external.releaseDate());
        media.setOriginalLanguage(external.originalLanguage());
        media.setCountryCode(external.countryCode());
        return media;
    }

    private void saveDetails(Media media, ExternalMedia external, String canonicalWorkWikidataId) {
        switch (external.type()) {
            case TRACK -> {
                TrackDetails details = new TrackDetails();
                details.setMedia(media);
                details.setDurationSeconds(external.durationSeconds());
                details.setExplicit(Boolean.TRUE.equals(external.explicit()));
                trackDetailsRepository.save(details);
            }
            case ALBUM -> {
                AlbumDetails details = new AlbumDetails();
                details.setMedia(media);
                details.setAlbumType(albumType(external.albumType()));
                details.setNumberOfTracks(external.numberOfTracks());
                details.setReleaseDate(external.releaseDate());
                albumDetailsRepository.save(details);
                external.tracks().forEach(track -> {
                    AlbumTrack entity = new AlbumTrack();
                    entity.setAlbum(media);
                    entity.setTrackMedia(resolveTrackMedia(track, external.releaseDate()));
                    entity.setExternalId(track.externalId());
                    entity.setTitle(track.title());
                    entity.setDiscNumber(track.discNumber());
                    entity.setTrackNumber(track.trackNumber());
                    entity.setDurationSeconds(track.durationSeconds());
                    entity.setExplicit(track.explicit());
                    albumTrackRepository.save(entity);
                });
            }
            case BOOK -> {
                BookDetails details = new BookDetails();
                details.setMedia(media);
                details.setIsbn10(external.isbn10());
                details.setIsbn13(external.isbn13());
                details.setPageCount(external.pageCount());
                details.setPublisher(external.publisher());
                details.setPublicationDate(external.releaseDate());
                details.setCanonicalWorkWikidataId(canonicalWorkWikidataId);
                bookDetailsRepository.save(details);
            }
            case MOVIE -> {
                MovieDetails details = new MovieDetails();
                details.setMedia(media);
                details.setRuntimeMinutes(external.runtimeMinutes());
                details.setBudget(external.budget());
                details.setRevenue(external.revenue());
                details.setReleaseDate(external.releaseDate());
                movieDetailsRepository.save(details);
            }
            case SERIES -> {
                SeriesDetails details = new SeriesDetails();
                details.setMedia(media);
                details.setStatus(seriesStatus(external.seriesStatus()));
                details.setNumberOfSeasons(external.numberOfSeasons());
                details.setNumberOfEpisodes(external.numberOfEpisodes());
                details.setFirstAirDate(external.releaseDate());
                details.setLastAirDate(external.lastAirDate());
                seriesDetailsRepository.save(details);
                external.seasons().forEach(season -> {
                    SeriesSeason entity = new SeriesSeason();
                    entity.setSeries(media);
                    entity.setExternalId(season.externalId());
                    entity.setSeasonNumber(season.seasonNumber());
                    entity.setName(season.name());
                    entity.setDescription(season.description());
                    entity.setCoverUrl(season.coverUrl());
                    entity.setEpisodeCount(season.episodeCount());
                    entity.setAirDate(season.airDate());
                    seriesSeasonRepository.save(entity);
                });
            }
            default -> throw new IllegalArgumentException("Import is unavailable for type " + external.type());
        }
    }

    private Media resolveTrackMedia(ExternalMedia.ExternalTrack track, LocalDate releaseDate) {
        if (track.externalId() != null) {
            ExternalReference existing = externalReferenceRepository
                    .findBySourceAndExternalId(ExternalSource.MUSICBRAINZ, track.externalId())
                    .orElse(null);
            if (existing != null && existing.getMedia().getType() == MediaType.TRACK) {
                return existing.getMedia();
            }
        }

        Media media = new Media();
        media.setType(MediaType.TRACK);
        media.setTitle(track.title());
        media.setReleaseDate(releaseDate);
        Media saved = mediaRepository.save(media);

        TrackDetails details = new TrackDetails();
        details.setMedia(saved);
        details.setDurationSeconds(track.durationSeconds());
        details.setExplicit(Boolean.TRUE.equals(track.explicit()));
        trackDetailsRepository.save(details);

        if (track.externalId() != null) {
            ExternalReference reference = new ExternalReference();
            reference.setMedia(saved);
            reference.setSource(ExternalSource.MUSICBRAINZ);
            reference.setExternalId(track.externalId());
            reference.setPrimaryReference(true);
            reference.setLastSyncedAt(Instant.now());
            externalReferenceRepository.save(reference);
        }
        return saved;
    }

    private SeriesStatus seriesStatus(String status) {
        if (status == null) {
            return SeriesStatus.UNKNOWN;
        }
        return switch (status.toUpperCase()) {
            case "PLANNED", "IN PRODUCTION", "POST PRODUCTION" -> SeriesStatus.PLANNED;
            case "RETURNING SERIES", "PILOT" -> SeriesStatus.AIRING;
            case "ENDED" -> SeriesStatus.ENDED;
            case "CANCELED" -> SeriesStatus.CANCELLED;
            default -> SeriesStatus.UNKNOWN;
        };
    }

    private AlbumType albumType(String type) {
        if (type == null) {
            return AlbumType.ALBUM;
        }
        return switch (type.toUpperCase()) {
            case "ALBUM" -> AlbumType.ALBUM;
            case "SINGLE" -> AlbumType.SINGLE;
            case "EP" -> AlbumType.EP;
            case "COMPILATION" -> AlbumType.COMPILATION;
            case "SOUNDTRACK" -> AlbumType.SOUNDTRACK;
            default -> AlbumType.ALBUM;
        };
    }

    private Optional<WikidataClient.WikidataEnrichment> findWikidataEnrichment(
            ExternalMedia external,
            ExternalSource source,
            MediaType mediaType,
            String externalId,
            String language
    ) {
        if (mediaType == MediaType.BOOK) {
            return wikidataClient.findBook(externalId, external.isbn13(), external.isbn10(), language);
        }
        return external.wikidataId() != null
                ? wikidataClient.findById(external.wikidataId(), language)
                : wikidataClient.find(source, mediaType, externalId, language);
    }

    private String canonicalWorkWikidataId(
            UUID importedId,
            MediaType mediaType,
            String wikidataId,
            Optional<WikidataClient.WikidataEnrichment> enrichment
    ) {
        if (mediaType != MediaType.BOOK) {
            return null;
        }
        if (importedId != null) {
            String stored = bookDetailsRepository.findById(importedId)
                    .map(BookDetails::getCanonicalWorkWikidataId)
                    .orElse(null);
            if (stored != null) {
                return stored;
            }
        }
        return enrichment.map(WikidataClient.WikidataEnrichment::canonicalWorkWikidataId)
                .orElse(wikidataId);
    }

    private UUID importedId(
            WikidataClient.RelatedMedia item,
            Map<ExternalKey, UUID> importedReferences,
            Map<String, UUID> importedBookWorkIds
    ) {
        UUID wikidataMatch = importedReferences.get(
                new ExternalKey(ExternalSource.WIKIDATA, item.wikidataId()));
        if (wikidataMatch != null) {
            return wikidataMatch;
        }
        if (item.providerSource() != null && item.providerExternalId() != null) {
            UUID providerMatch = importedReferences.get(
                    new ExternalKey(item.providerSource(), item.providerExternalId()));
            if (providerMatch != null) {
                return providerMatch;
            }
        }
        return item.type() == MediaType.BOOK ? importedBookWorkIds.get(item.wikidataId()) : null;
    }

    private String preferredWikidataId(
            ExternalMedia external,
            Optional<WikidataClient.WikidataEnrichment> enrichment
    ) {
        return external.wikidataId() != null
                ? external.wikidataId()
                : enrichment.map(WikidataClient.WikidataEnrichment::wikidataId).orElse(null);
    }

    private ExternalMediaResponse toImportedResponse(
            Media media,
            ExternalReference reference,
            String creator,
            Integer durationSeconds,
            String wikidataId
    ) {
        return new ExternalMediaResponse(
                media.getId(), reference.getExternalId(), reference.getSource(), media.getType(), media.getTitle(),
                creator, media.getDescription(), media.getCoverUrl(), media.getReleaseDate(),
                durationSeconds, wikidataId, true
        );
    }

    private List<ExternalMediaDetailsResponse.CreditResponse> toCreditResponses(
            List<ExternalMedia.ExternalCredit> credits
    ) {
        if (credits == null || credits.isEmpty()) {
            return List.of();
        }
        return credits.stream().map(credit -> new ExternalMediaDetailsResponse.CreditResponse(
                null,
                credit.name(),
                credit.role(),
                credit.characterName(),
                credit.position(),
                credit.imageUrl(),
                credit.source(),
                credit.externalId()
        )).toList();
    }

    private record ExternalKey(ExternalSource source, String externalId) {
    }

    private record MediaCommunityStats(
            long likeCount,
            List<MediaCommunityUserResponse> recentLikers,
            Double averageRating,
            List<ExternalMediaDetailsResponse.RatingDistributionBucket> ratingDistribution,
            long listCount,
            long completedCount,
            List<MediaCommunityUserResponse> recentCompleters
    ) {
        private static MediaCommunityStats empty() {
            List<ExternalMediaDetailsResponse.RatingDistributionBucket> ratingDistribution = new ArrayList<>(10);
            for (int step = 1; step <= 10; step++) {
                ratingDistribution.add(new ExternalMediaDetailsResponse.RatingDistributionBucket(step / 2.0, 0));
            }
            return new MediaCommunityStats(
                    0, List.of(), null, List.copyOf(ratingDistribution), 0, 0, List.of());
        }
    }
}
