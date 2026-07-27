package com.scriptles.cabinet.media.catalog;

import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.entity.*;
import com.scriptles.cabinet.media.enums.*;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.repository.*;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;

@Component
@RequiredArgsConstructor
public class CatalogMaterializationService {
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final MediaTranslationRepository translationRepository;
    private final MovieDetailsRepository movieDetailsRepository;
    private final SeriesDetailsRepository seriesDetailsRepository;
    private final AlbumDetailsRepository albumDetailsRepository;
    private final BookDetailsRepository bookDetailsRepository;
    private final TrackDetailsRepository trackDetailsRepository;
    private final CatalogLocaleResolver localeResolver;

    public Media findOrCreateCore(MediaTarget target, CatalogResolver.Resolution resolution) {
        if (resolution.alreadyMaterialized()) {
            return mediaRepository.findById(resolution.mediaId())
                    .orElseThrow(() -> new IllegalArgumentException("Media not found"));
        }

        ExternalMedia external = resolution.snapshot().media();
        String locale = localeResolver.normalize(resolution.locale());
        Media media = new Media();
        media.setType(external.type());
        media.setTitle(firstNonBlank(external.title(), external.originalTitle(), external.externalId()));
        media.setOriginalTitle(firstNonBlank(external.originalTitle(), external.title()));
        media.setDescription(external.description());
        media.setTagline(external.tagline());
        media.setCoverUrl(external.coverUrl());
        media.setBackdropUrl(external.backdropUrl());
        media.setGenres(external.genres() == null
                ? new LinkedHashSet<>()
                : external.genres().stream()
                        .map(ExternalMedia.ExternalGenre::name)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        media.setReleaseDate(external.releaseDate());
        media.setOriginalLanguage(external.originalLanguage());
        media.setDefaultLocale(locale);
        media.setCountryCode(external.countryCode());
        media.setCatalogStatus(CatalogStatus.CORE_READY);
        media.setCoreSyncedAt(Instant.now());
        Media saved = mediaRepository.saveAndFlush(media);

        saveMinimalDetails(saved, external);
        saveTranslation(saved, external, locale);

        ExternalReference reference = new ExternalReference();
        reference.setMedia(saved);
        reference.setSource(target.source());
        reference.setExternalId(target.externalId());
        reference.setExternalUrl(external.externalUrl());
        reference.setPrimaryReference(true);
        reference.setLastSyncedAt(Instant.now());
        externalReferenceRepository.saveAndFlush(reference);
        return saved;
    }

    private void saveTranslation(Media media, ExternalMedia external, String locale) {
        MediaTranslation translation = new MediaTranslation();
        translation.setMedia(media);
        translation.setLocale(locale);
        translation.setTitle(firstNonBlank(external.title(), external.originalTitle(), external.externalId()));
        translation.setDescription(external.description());
        translation.setTagline(external.tagline());
        translation.setCoverUrl(external.coverUrl());
        translation.setSource(external.source());
        translation.setOriginalLanguage(external.originalLanguage());
        translation.setTranslationStatus(translationStatus(external));
        translation.setLastSyncedAt(Instant.now());
        translationRepository.save(translation);
    }

    private void saveMinimalDetails(Media media, ExternalMedia external) {
        switch (external.type()) {
            case MOVIE -> {
                MovieDetails details = new MovieDetails();
                details.setMedia(media);
                details.setRuntimeMinutes(external.runtimeMinutes());
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
            }
            case ALBUM -> {
                AlbumDetails details = new AlbumDetails();
                details.setMedia(media);
                details.setAlbumType(albumType(external.albumType()));
                details.setNumberOfTracks(external.numberOfTracks());
                details.setReleaseDate(external.releaseDate());
                albumDetailsRepository.save(details);
            }
            case BOOK -> {
                BookDetails details = new BookDetails();
                details.setMedia(media);
                details.setIsbn10(external.isbn10());
                details.setIsbn13(external.isbn13());
                details.setPageCount(external.pageCount());
                details.setPublisher(external.publisher());
                details.setPublicationDate(external.releaseDate());
                bookDetailsRepository.save(details);
            }
            case TRACK -> {
                TrackDetails details = new TrackDetails();
                details.setMedia(media);
                details.setDurationSeconds(external.durationSeconds());
                details.setExplicit(Boolean.TRUE.equals(external.explicit()));
                trackDetailsRepository.save(details);
            }
            case EPISODE -> throw new IllegalArgumentException("External episode materialization is unsupported");
        }
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

    private TranslationStatus translationStatus(ExternalMedia external) {
        boolean hasTitle = firstNonBlank(external.title(), external.originalTitle()) != null;
        boolean hasOptionalText = firstNonBlank(external.description(), external.tagline()) != null;
        return hasTitle && hasOptionalText ? TranslationStatus.AVAILABLE : TranslationStatus.PARTIAL;
    }
}
