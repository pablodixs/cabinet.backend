package com.scriptles.cabinet.media.enrichment;

import com.scriptles.cabinet.common.outbox.DomainEventType;
import com.scriptles.cabinet.common.outbox.DomainOutboxPublisher;
import com.scriptles.cabinet.media.entity.*;
import com.scriptles.cabinet.media.catalog.GenreCatalogService;
import com.scriptles.cabinet.media.enums.*;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.repository.*;
import com.scriptles.cabinet.media.service.MediaCreditService;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogEnrichmentPersistenceService {
    private final MediaRepository mediaRepository;
    private final MediaTranslationRepository translationRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final MovieDetailsRepository movieDetailsRepository;
    private final SeriesDetailsRepository seriesDetailsRepository;
    private final AlbumDetailsRepository albumDetailsRepository;
    private final BookDetailsRepository bookDetailsRepository;
    private final TrackDetailsRepository trackDetailsRepository;
    private final AlbumTrackRepository albumTrackRepository;
    private final SeriesSeasonRepository seasonRepository;
    private final MediaCreditService creditService;
    private final CatalogLocaleResolver localeResolver;
    private final GenreCatalogService genreCatalogService;
    private final DomainOutboxPublisher domainOutboxPublisher;

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "mediaDetails", key = "#mediaId + ':pt-BR'"),
            @CacheEvict(cacheNames = "mediaDetails", key = "#mediaId + ':en-US'")
    })
    public void complete(UUID mediaId, ExternalMedia external, String locale, String wikidataId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found"));
        String normalizedLocale = localeResolver.normalize(locale);
        if (normalizedLocale.equals(media.getDefaultLocale())) {
            media.setTitle(firstNonBlank(external.title(), media.getTitle()));
            media.setDescription(external.description());
            media.setTagline(external.tagline());
        }
        media.setOriginalTitle(firstNonBlank(external.originalTitle(), media.getOriginalTitle()));
        media.setCoverUrl(firstNonBlank(external.coverUrl(), media.getCoverUrl()));
        media.setBackdropUrl(firstNonBlank(external.backdropUrl(), media.getBackdropUrl()));
        media.setLogoUrl(firstNonBlank(external.logoUrl(), media.getLogoUrl()));
        media.setReleaseDate(external.releaseDate());
        media.setOriginalLanguage(external.originalLanguage());
        media.setCountryCode(external.countryCode());
        media.setWikidataId(wikidataId);
        if (external.genres() != null && !external.genres().isEmpty()) {
            genreCatalogService.replace(mediaId, external.genres(), normalizedLocale);
            media.setGenres(external.genres().stream()
                    .map(ExternalMedia.ExternalGenre::name)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        }
        updateDetails(media, external);
        upsertTranslation(media, external, normalizedLocale);
        linkWikidata(media, wikidataId);
        creditService.saveWithoutIdentityEnrichment(media, external.credits());
        media.setCatalogStatus(CatalogStatus.READY);
        media.setEnrichmentSyncedAt(Instant.now());
        media.setSyncVersion(media.getSyncVersion() + 1);
        media.setLastSyncError(null);
        domainOutboxPublisher.publishMediaEvent(DomainEventType.MEDIA_METADATA_CHANGED, mediaId,
                java.util.Map.of("syncVersion", media.getSyncVersion(), "locale", normalizedLocale));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "mediaDetails", key = "#mediaId + ':pt-BR'"),
            @CacheEvict(cacheNames = "mediaDetails", key = "#mediaId + ':en-US'")
    })
    public void saveStructure(UUID mediaId, ExternalMedia external) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found"));
        updateDetails(media, external);
        domainOutboxPublisher.publishMediaEvent(DomainEventType.MEDIA_METADATA_CHANGED, mediaId,
                java.util.Map.of("structureUpdated", true));
    }

    @Transactional
    @CacheEvict(cacheNames = "mediaDetails", key = "#mediaId + ':' + #locale")
    public void saveTranslation(UUID mediaId, ExternalMedia external, String locale) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found"));
        String normalizedLocale = localeResolver.normalize(locale);
        upsertTranslation(media, external, normalizedLocale);
        genreCatalogService.add(mediaId, external.genres(), normalizedLocale);
        domainOutboxPublisher.publishMediaEvent(DomainEventType.MEDIA_METADATA_CHANGED, mediaId,
                java.util.Map.of("locale", normalizedLocale));
    }

    @Transactional
    public void markFailed(UUID mediaId, String error) {
        mediaRepository.findById(mediaId).ifPresent(media -> {
            media.setCatalogStatus(CatalogStatus.FAILED);
            media.setLastSyncError(truncate(error, 2_000));
        });
    }

    @Transactional
    public void markEnriching(UUID mediaId) {
        mediaRepository.findById(mediaId).ifPresent(media -> media.setCatalogStatus(CatalogStatus.ENRICHING));
    }

    private void upsertTranslation(Media media, ExternalMedia external, String locale) {
        MediaTranslation translation = translationRepository.findByMediaIdAndLocale(media.getId(), locale)
                .orElseGet(MediaTranslation::new);
        translation.setMedia(media);
        translation.setLocale(locale);
        translation.setTitle(firstNonBlank(external.title(), external.originalTitle(), media.getTitle()));
        translation.setDescription(external.description());
        translation.setTagline(external.tagline());
        translation.setCoverUrl(external.coverUrl());
        translation.setSource(external.source());
        translation.setOriginalLanguage(external.originalLanguage());
        translation.setTranslationStatus(translationStatus(external));
        translation.setLastSyncedAt(Instant.now());
        translationRepository.save(translation);
    }

    private void linkWikidata(Media media, String wikidataId) {
        if (wikidataId == null || wikidataId.isBlank()) return;
        ExternalReference reference = externalReferenceRepository
                .findBySourceAndExternalId(ExternalSource.WIKIDATA, wikidataId)
                .orElseGet(ExternalReference::new);
        reference.setMedia(media);
        reference.setSource(ExternalSource.WIKIDATA);
        reference.setExternalId(wikidataId);
        reference.setExternalUrl("https://www.wikidata.org/wiki/" + wikidataId);
        reference.setPrimaryReference(false);
        reference.setLastSyncedAt(Instant.now());
        externalReferenceRepository.save(reference);
    }

    private void updateDetails(Media media, ExternalMedia external) {
        switch (media.getType()) {
            case MOVIE -> movieDetailsRepository.findById(media.getId()).ifPresent(details -> {
                details.setRuntimeMinutes(external.runtimeMinutes());
                details.setBudget(external.budget());
                details.setRevenue(external.revenue());
                details.setReleaseDate(external.releaseDate());
            });
            case SERIES -> {
                seriesDetailsRepository.findById(media.getId()).ifPresent(details -> {
                    details.setStatus(seriesStatus(external.seriesStatus()));
                    details.setNumberOfSeasons(external.numberOfSeasons());
                    details.setNumberOfEpisodes(external.numberOfEpisodes());
                    details.setFirstAirDate(external.releaseDate());
                    details.setLastAirDate(external.lastAirDate());
                });
                if (external.seasons() != null) {
                    for (ExternalMedia.ExternalSeason source : external.seasons()) {
                        SeriesSeason season = seasonRepository
                                .findBySeriesIdAndSeasonNumber(media.getId(), source.seasonNumber())
                                .orElseGet(SeriesSeason::new);
                        season.setSeries(media);
                        season.setExternalId(source.externalId());
                        season.setSeasonNumber(source.seasonNumber());
                        season.setName(source.name());
                        season.setDescription(source.description());
                        season.setCoverUrl(source.coverUrl());
                        season.setEpisodeCount(source.episodeCount());
                        season.setAirDate(source.airDate());
                        seasonRepository.save(season);
                    }
                }
            }
            case ALBUM -> {
                albumDetailsRepository.findById(media.getId()).ifPresent(details -> {
                    details.setAlbumType(albumType(external.albumType()));
                    details.setNumberOfTracks(external.numberOfTracks());
                    details.setReleaseDate(external.releaseDate());
                });
                if (external.tracks() != null) {
                    external.tracks().forEach(track -> upsertTrack(media, external, track));
                }
            }
            case BOOK -> bookDetailsRepository.findById(media.getId()).ifPresent(details -> {
                details.setIsbn10(external.isbn10());
                details.setIsbn13(external.isbn13());
                details.setPageCount(external.pageCount());
                details.setPublisher(external.publisher());
                details.setPublicationDate(external.releaseDate());
            });
            case TRACK -> trackDetailsRepository.findById(media.getId()).ifPresent(details -> {
                details.setDurationSeconds(external.durationSeconds());
                details.setExplicit(Boolean.TRUE.equals(external.explicit()));
            });
            case EPISODE -> {
            }
        }
    }

    private void upsertTrack(
            Media album,
            ExternalMedia external,
            ExternalMedia.ExternalTrack source
    ) {
        Media trackMedia = source.externalId() == null
                ? null
                : externalReferenceRepository
                        .findBySourceAndExternalId(ExternalSource.MUSICBRAINZ, source.externalId())
                        .map(ExternalReference::getMedia)
                        .orElse(null);
        if (trackMedia == null) {
            trackMedia = new Media();
            trackMedia.setType(MediaType.TRACK);
            trackMedia.setTitle(firstNonBlank(source.title(), source.externalId()));
            trackMedia.setOriginalTitle(source.title());
            trackMedia.setDefaultLocale(album.getDefaultLocale());
            trackMedia.setReleaseDate(external.releaseDate());
            trackMedia.setCatalogStatus(CatalogStatus.CORE_READY);
            trackMedia.setCoreSyncedAt(Instant.now());
            trackMedia = mediaRepository.save(trackMedia);

            TrackDetails details = new TrackDetails();
            details.setMedia(trackMedia);
            details.setDurationSeconds(source.durationSeconds());
            details.setExplicit(Boolean.TRUE.equals(source.explicit()));
            trackDetailsRepository.save(details);

            if (source.externalId() != null) {
                ExternalReference reference = new ExternalReference();
                reference.setMedia(trackMedia);
                reference.setSource(ExternalSource.MUSICBRAINZ);
                reference.setExternalId(source.externalId());
                reference.setPrimaryReference(true);
                reference.setLastSyncedAt(Instant.now());
                externalReferenceRepository.save(reference);
            }
        }

        AlbumTrack track = albumTrackRepository.findByAlbumIdAndDiscNumberAndTrackNumber(
                        album.getId(), source.discNumber(), source.trackNumber())
                .orElseGet(AlbumTrack::new);
        track.setAlbum(album);
        track.setTrackMedia(trackMedia);
        track.setExternalId(source.externalId());
        track.setTitle(firstNonBlank(source.title(), source.externalId()));
        track.setDiscNumber(source.discNumber());
        track.setTrackNumber(source.trackNumber());
        track.setDurationSeconds(source.durationSeconds());
        track.setExplicit(source.explicit());
        albumTrackRepository.save(track);
    }

    private SeriesStatus seriesStatus(String status) {
        if (status == null) return SeriesStatus.UNKNOWN;
        return switch (status.toUpperCase()) {
            case "PLANNED", "IN PRODUCTION", "POST PRODUCTION" -> SeriesStatus.PLANNED;
            case "RETURNING SERIES", "PILOT" -> SeriesStatus.AIRING;
            case "ENDED" -> SeriesStatus.ENDED;
            case "CANCELED" -> SeriesStatus.CANCELLED;
            default -> SeriesStatus.UNKNOWN;
        };
    }

    private AlbumType albumType(String type) {
        if (type == null) return AlbumType.ALBUM;
        try {
            return AlbumType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AlbumType.ALBUM;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String truncate(String value, int size) {
        if (value == null || value.length() <= size) return value;
        return value.substring(0, size);
    }

    private TranslationStatus translationStatus(ExternalMedia external) {
        boolean hasTitle = firstNonBlank(external.title(), external.originalTitle()) != null;
        boolean hasOptionalText = firstNonBlank(external.description(), external.tagline()) != null;
        return hasTitle && hasOptionalText ? TranslationStatus.AVAILABLE : TranslationStatus.PARTIAL;
    }
}
