package com.scriptles.cabinet.media.enrichment;

import com.scriptles.cabinet.media.entity.CatalogOutboxEvent;
import com.scriptles.cabinet.media.catalog.CatalogEventPayload;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.external.WikidataClient;
import com.scriptles.cabinet.media.repository.CatalogOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogOutboxWorker {
    private final CatalogOutboxRepository repository;
    private final CatalogOutboxClaimService claimService;
    private final ExternalMediaProviderRegistry providerRegistry;
    private final WikidataClient wikidataClient;
    private final CatalogEnrichmentPersistenceService persistenceService;

    @Scheduled(fixedDelayString = "${catalog.outbox.poll-delay:1000}")
    public void poll() {
        String workerId = ManagementFactory.getRuntimeMXBean().getName();
        for (UUID eventId : claimService.claim(workerId, 25)) {
            process(eventId);
        }
    }

    void process(UUID eventId) {
        CatalogOutboxEvent event = repository.findById(eventId).orElse(null);
        if (event == null) return;
        try {
            CatalogEventPayload payload = event.getPayload();
            persistenceService.markEnriching(event.getAggregateId());
            ExternalMedia external = providerRegistry.get(payload.source(), payload.mediaType())
                    .findEnrichmentById(payload.mediaType(), payload.externalId(), payload.locale())
                    .orElseThrow(() -> new IllegalArgumentException("External media not found"));
            Optional<WikidataClient.WikidataEnrichment> wikidata = findWikidata(payload, external);
            if (wikidata.isPresent()) {
                external = external.withEnrichment(
                        wikidata.get().logoUrl(),
                        wikidata.get().genres()
                );
            }
            String wikidataId = external.wikidataId() != null
                    ? external.wikidataId()
                    : wikidata.map(WikidataClient.WikidataEnrichment::wikidataId).orElse(null);
            persistenceService.complete(event.getAggregateId(), external, payload.locale(), wikidataId);
            fetchSecondaryTranslation(event.getAggregateId(), payload);
            claimService.complete(eventId);
        } catch (RuntimeException failure) {
            boolean retrying = claimService.retry(eventId, failure);
            if (!retrying) {
                persistenceService.markFailed(event.getAggregateId(), failure.getMessage());
            }
            log.warn("Catalog enrichment event {} failed: {}", eventId, failure.getMessage());
        }
    }

    private void fetchSecondaryTranslation(
            UUID mediaId,
            CatalogEventPayload payload
    ) {
        String secondaryLocale = "pt-BR".equals(payload.locale()) ? "en-US" : "pt-BR";
        try {
            providerRegistry.get(payload.source(), payload.mediaType())
                    .findCoreById(payload.mediaType(), payload.externalId(), secondaryLocale)
                    .ifPresent(media -> persistenceService.saveTranslation(mediaId, media, secondaryLocale));
        } catch (RuntimeException failure) {
            log.warn("Secondary translation for media {} failed: {}", mediaId, failure.getMessage());
        }
    }

    private Optional<WikidataClient.WikidataEnrichment> findWikidata(
            CatalogEventPayload payload,
            ExternalMedia external
    ) {
        if (payload.mediaType() == MediaType.BOOK) {
            return wikidataClient.findBook(
                    payload.externalId(), external.isbn13(), external.isbn10(), payload.locale());
        }
        if (external.wikidataId() != null) {
            return wikidataClient.findById(external.wikidataId(), payload.locale());
        }
        return wikidataClient.find(
                payload.source(), payload.mediaType(), payload.externalId(), payload.locale());
    }
}
