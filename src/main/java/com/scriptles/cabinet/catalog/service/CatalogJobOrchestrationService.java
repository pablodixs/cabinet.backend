package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.api.CatalogOperationAcceptedResponse;
import com.scriptles.cabinet.catalog.repository.CatalogJobRepository;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.enums.SupportedLocale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static com.scriptles.cabinet.catalog.service.CatalogJobTypes.*;

@Service
@RequiredArgsConstructor
public class CatalogJobOrchestrationService {
    private static final List<String> ACTIVE = List.of(PENDING, PROCESSING, RETRY);
    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ofPattern("MM_dd_uuuu");

    private final CatalogJobRequestWriter writer;
    private final CatalogJobRepository jobRepository;
    private final CollectionExternalReferenceRepository collectionReferences;

    public CatalogOperationAcceptedResponse importCollection(String provider, String externalId, String locale,
                                                              UUID requestedBy) {
        if (provider != null && !provider.isBlank() && !"TMDB".equalsIgnoreCase(provider.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_PROVIDER_UNSUPPORTED",
                    "Only TMDB collection imports are currently supported");
        }
        try {
            externalId = TmdbCollectionIdNormalizer.normalize(externalId);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TMDB_COLLECTION_ID_INVALID", invalid.getMessage());
        }
        String normalizedLocale = normalizeLocale(locale);
        String normalizedExternalId = externalId;
        String dedupeKey = "TMDB_COLLECTION_HYDRATE:TMDB:" + externalId;
        try {
            return writer.createImportCollection(normalizedExternalId, normalizedLocale, "MANUAL", requestedBy);
        } catch (DataIntegrityViolationException race) {
            return jobRepository.findFirstByDeduplicationKeyAndStatusInOrderByCreatedAtAsc(dedupeKey, ACTIVE)
                    .map(job -> new CatalogOperationAcceptedResponse(job.getOperationId(), QUEUED,
                            "TMDB", normalizedExternalId))
                    .orElseThrow(() -> race);
        }
    }

    public CatalogOperationAcceptedResponse syncCollection(UUID collectionId, String locale, String trigger,
                                                            int priority, UUID requestedBy) {
        String normalizedLocale = normalizeLocale(locale);
        try {
            return writer.createCollectionSync(collectionId, normalizedLocale, trigger, priority, requestedBy);
        } catch (DataIntegrityViolationException race) {
            String externalId = collectionReferences.findByCollectionId(collectionId).stream()
                    .filter(reference -> reference.getProvider() == com.scriptles.cabinet.media.enums.ExternalSource.TMDB)
                    .map(reference -> reference.getExternalId()).findFirst().orElseThrow(() -> race);
            String key = "TMDB_COLLECTION_HYDRATE:TMDB:" + externalId;
            return jobRepository.findFirstByDeduplicationKeyAndStatusInOrderByCreatedAtAsc(key, ACTIVE)
                    .map(job -> new CatalogOperationAcceptedResponse(job.getOperationId(), QUEUED,
                            "TMDB", externalId)).orElseThrow(() -> race);
        }
    }

    public CatalogOperationAcceptedResponse enqueueDailyCollectionIndex() {
        String date = EXPORT_DATE.format(LocalDate.now(ZoneOffset.UTC));
        String key = "EXTERNAL_CATALOG_INDEX:TMDB:COLLECTION:" + date;
        try {
            return writer.enqueueIndex(date);
        } catch (DataIntegrityViolationException race) {
            return jobRepository.findFirstByDeduplicationKeyAndStatusInOrderByCreatedAtAsc(key, ACTIVE)
                    .map(job -> new CatalogOperationAcceptedResponse(job.getOperationId(), QUEUED,
                            "TMDB", date))
                    .orElseThrow(() -> race);
        }
    }

    private String normalizeLocale(String locale) {
        try {
            return SupportedLocale.from(locale).tag();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCALE_UNSUPPORTED", "Unsupported catalog locale");
        }
    }
}
