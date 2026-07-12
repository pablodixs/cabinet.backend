package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.request.ImportExternalMediaRequest;
import com.scriptles.cabinet.media.dto.response.ExternalMediaResponse;
import com.scriptles.cabinet.media.entity.AlbumDetails;
import com.scriptles.cabinet.media.entity.BookDetails;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MovieDetails;
import com.scriptles.cabinet.media.entity.SeriesDetails;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.AlbumType;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.SeriesStatus;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaProvider;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.external.ExternalMediaRateLimitException;
import com.scriptles.cabinet.media.repository.BookDetailsRepository;
import com.scriptles.cabinet.media.repository.AlbumDetailsRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.MovieDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExternalMediaService {
    private final ExternalMediaProviderRegistry providerRegistry;
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final AlbumDetailsRepository albumDetailsRepository;
    private final BookDetailsRepository bookDetailsRepository;
    private final MovieDetailsRepository movieDetailsRepository;
    private final SeriesDetailsRepository seriesDetailsRepository;

    @Transactional(readOnly = true)
    public List<ExternalMediaResponse> search(MediaType mediaType, String query, String language, int offset, int limit) {
        if (mediaType == MediaType.BOOK) {
            throw new IllegalArgumentException("Book search is temporarily disabled");
        }
        if (mediaType == null) {
            List<ExternalMedia> tmdbResults = providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)
                    .searchAll(query, language, offset, limit);
            List<ExternalMedia> albumResults = searchAlbums(query, offset, limit);
            return interleave(List.of(tmdbResults, albumResults), limit).stream()
                    .map(this::toSearchResponse)
                    .toList();
        }
        ExternalMediaProvider provider = providerRegistry.get(defaultSource(mediaType), mediaType);
        return provider.search(mediaType, query, language, offset, limit).stream()
                .map(this::toSearchResponse)
                .toList();
    }

    private List<ExternalMedia> searchAlbums(String query, int offset, int limit) {
        try {
            return providerRegistry.get(ExternalSource.MUSICBRAINZ, MediaType.ALBUM)
                    .search(MediaType.ALBUM, query, null, offset, limit);
        } catch (ExternalMediaRateLimitException exception) {
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
            return toImportedResponse(existing.getMedia(), existing, null);
        }

        ExternalMediaProvider provider = providerRegistry.get(request.source(), request.mediaType());
        ExternalMedia external = provider.findById(request.mediaType(), request.externalId())
                .orElseThrow(() -> new IllegalArgumentException("External media not found"));

        Media media = mediaRepository.save(toMedia(external));
        saveDetails(media, external);

        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(external.source());
        reference.setExternalId(external.externalId());
        reference.setExternalUrl(external.externalUrl());
        reference.setPrimaryReference(true);
        reference.setLastSyncedAt(Instant.now());
        externalReferenceRepository.save(reference);

        return toImportedResponse(media, reference, external.creator());
    }

    private ExternalSource defaultSource(MediaType mediaType) {
        return switch (mediaType) {
            case BOOK -> ExternalSource.GOOGLE_BOOKS;
            case MOVIE, SERIES -> ExternalSource.TMDB;
            case ALBUM -> ExternalSource.MUSICBRAINZ;
            default -> throw new IllegalArgumentException("External search is unavailable for type " + mediaType);
        };
    }

    private ExternalMediaResponse toSearchResponse(ExternalMedia external) {
        return externalReferenceRepository.findBySourceAndExternalId(external.source(), external.externalId())
                .map(reference -> toImportedResponse(reference.getMedia(), reference, external.creator()))
                .orElseGet(() -> new ExternalMediaResponse(
                        null, external.externalId(), external.source(), external.type(), external.title(),
                        external.creator(), external.description(), external.coverUrl(), external.releaseDate(), false
                ));
    }

    private Media toMedia(ExternalMedia external) {
        Media media = new Media();
        media.setType(external.type());
        media.setTitle(external.title());
        media.setOriginalTitle(external.originalTitle());
        media.setDescription(external.description());
        media.setCoverUrl(external.coverUrl());
        media.setReleaseDate(external.releaseDate());
        media.setOriginalLanguage(external.originalLanguage());
        media.setCountryCode(external.countryCode());
        return media;
    }

    private void saveDetails(Media media, ExternalMedia external) {
        switch (external.type()) {
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
            }
            default -> throw new IllegalArgumentException("Import is unavailable for type " + external.type());
        }
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

    private ExternalMediaResponse toImportedResponse(Media media, ExternalReference reference, String creator) {
        return new ExternalMediaResponse(
                media.getId(), reference.getExternalId(), reference.getSource(), media.getType(), media.getTitle(),
                creator, media.getDescription(), media.getCoverUrl(), media.getReleaseDate(), true
        );
    }
}
