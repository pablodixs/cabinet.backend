package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.WikidataLinkResponse;
import com.scriptles.cabinet.media.catalog.GenreCatalogService;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.WikidataClient;
import com.scriptles.cabinet.media.repository.BookDetailsRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WikidataLinkService {
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final BookDetailsRepository bookDetailsRepository;
    private final WikidataClient wikidataClient;
    private final GenreCatalogService genreCatalogService;

    @Transactional
    public WikidataLinkResponse link(UUID mediaId, String wikidataId, String language) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found"));

        externalReferenceRepository.findBySourceAndExternalId(ExternalSource.WIKIDATA, wikidataId)
                .filter(reference -> !reference.getMedia().getId().equals(mediaId))
                .ifPresent(reference -> {
                    throw new IllegalArgumentException("Wikidata ID is already linked to another media");
                });

        ExternalReference reference = externalReferenceRepository
                .findByMediaIdAndSource(mediaId, ExternalSource.WIKIDATA)
                .orElseGet(ExternalReference::new);
        reference.setMedia(media);
        reference.setSource(ExternalSource.WIKIDATA);
        reference.setExternalId(wikidataId);
        reference.setExternalUrl("https://www.wikidata.org/wiki/" + wikidataId);
        reference.setPrimaryReference(false);
        reference.setLastSyncedAt(Instant.now());

        Optional<WikidataClient.WikidataEnrichment> enrichment = wikidataClient.findById(wikidataId, language);
        enrichment.ifPresent(value -> {
            applyEnrichment(media, value);
            genreCatalogService.add(mediaId, value.genres(), language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("en") ? "en-US" : "pt-BR");
        });
        if (media.getType() == MediaType.BOOK) {
            bookDetailsRepository.findById(mediaId).ifPresent(details -> {
                details.setCanonicalWorkWikidataId(enrichment
                        .map(WikidataClient.WikidataEnrichment::canonicalWorkWikidataId)
                        .orElse(wikidataId));
                bookDetailsRepository.save(details);
            });
        }
        media.setWikidataId(wikidataId);

        mediaRepository.save(media);
        externalReferenceRepository.save(reference);
        return new WikidataLinkResponse(mediaId, wikidataId, reference.getExternalUrl(), enrichment.isPresent());
    }

    private void applyEnrichment(Media media, WikidataClient.WikidataEnrichment enrichment) {
        if (media.getLogoUrl() == null) {
            media.setLogoUrl(enrichment.logoUrl());
        }
        LinkedHashSet<String> genres = new LinkedHashSet<>(media.getGenres());
        enrichment.genres().stream().map(ExternalMedia.ExternalGenre::name).forEach(genres::add);
        media.setGenres(genres);
    }
}
