package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.api.CatalogOperationAcceptedResponse;
import com.scriptles.cabinet.catalog.entity.CatalogJob;
import com.scriptles.cabinet.catalog.entity.CatalogOperation;
import com.scriptles.cabinet.catalog.entity.CatalogOperationEvent;
import com.scriptles.cabinet.catalog.entity.CollectionExternalReference;
import com.scriptles.cabinet.catalog.repository.CatalogJobRepository;
import com.scriptles.cabinet.catalog.repository.CatalogOperationEventRepository;
import com.scriptles.cabinet.catalog.repository.CatalogOperationRepository;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.media.enums.ExternalSource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.scriptles.cabinet.catalog.service.CatalogJobTypes.*;

@Service
@RequiredArgsConstructor
public class CatalogJobRequestWriter {
    private static final List<String> ACTIVE = List.of(PENDING, PROCESSING, RETRY);

    private final CatalogJobRepository jobRepository;
    private final CatalogOperationRepository operationRepository;
    private final CatalogOperationEventRepository eventRepository;
    private final CollectionExternalReferenceRepository collectionReferenceRepository;

    @Transactional
    public CatalogOperationAcceptedResponse createImportCollection(String externalId, String locale, String trigger,
                                                                     UUID requestedBy) {
        return createCollectionOperation("COLLECTION_IMPORT", TMDB_COLLECTION_HYDRATE, externalId,
                null, locale, trigger, PRIORITY_DIRECT_DEMAND, requestedBy);
    }

    @Transactional
    public CatalogOperationAcceptedResponse createBackfillCollection(String externalId, String locale) {
        return createCollectionOperation("COLLECTION_IMPORT", TMDB_COLLECTION_HYDRATE, externalId,
                null, locale, "BACKFILL", PRIORITY_BACKFILL, null);
    }

    @Transactional
    public CatalogOperationAcceptedResponse createCollectionSync(UUID collectionId, String locale, String trigger,
                                                                   int priority, UUID requestedBy) {
        CollectionExternalReference reference = collectionReferenceRepository
                .findByCollectionId(collectionId).stream()
                .filter(item -> item.getProvider() == ExternalSource.TMDB)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Collection does not have a TMDB reference"));
        return createCollectionOperation("COLLECTION_SYNC", TMDB_COLLECTION_HYDRATE,
                reference.getExternalId(), collectionId, locale, trigger, priority, requestedBy);
    }

    @Transactional
    public CatalogOperationAcceptedResponse enqueueIndex(String dateKey) {
        String key = "EXTERNAL_CATALOG_INDEX:TMDB:COLLECTION:" + dateKey;
        CatalogJob active = jobRepository.findFirstByDeduplicationKeyAndStatusInOrderByCreatedAtAsc(key, ACTIVE)
                .orElse(null);
        if (active != null) return accepted(active.getOperationId(), "TMDB", dateKey);
        Instant now = Instant.now();
        CatalogOperation operation = operation("EXTERNAL_CATALOG_INDEX", "TMDB", "SCHEDULED", "QUEUED",
                null, "COLLECTION", dateKey, null, now);
        operationRepository.save(operation);
        CatalogJob job = job(CatalogJobTypes.EXTERNAL_CATALOG_INDEX, "TMDB", "COLLECTION", dateKey,
                null, operation.getId(), "SCHEDULED", 10, key, Map.of("date", dateKey), now);
        jobRepository.save(job);
        event(operation.getId(), job.getId(), "OPERATION_CREATED", "INFO", "TMDB collection index queued", now);
        return accepted(operation.getId(), "TMDB", dateKey);
    }

    private CatalogOperationAcceptedResponse createCollectionOperation(String operationType, String jobType,
            String externalId, UUID collectionId, String locale, String trigger, int priority, UUID requestedBy) {
        String key = "TMDB_COLLECTION_HYDRATE:TMDB:" + externalId;
        CatalogJob existing = jobRepository.findFirstByDeduplicationKeyAndStatusInOrderByCreatedAtAsc(key, ACTIVE)
                .orElse(null);
        if (existing != null) return accepted(existing.getOperationId(), "TMDB", externalId);

        Instant now = Instant.now();
        CatalogOperation operation = operation(operationType, "TMDB", trigger, "QUEUED", requestedBy,
                "COLLECTION", externalId, collectionId, now);
        operationRepository.save(operation);
        CatalogJob job = job(jobType, "TMDB", "COLLECTION", externalId, collectionId,
                operation.getId(), trigger, priority, key, Map.of("locale", locale), now);
        jobRepository.save(job);
        if (collectionId != null) {
            collectionReferenceRepository.findByProviderAndExternalId(ExternalSource.TMDB, externalId)
                    .ifPresent(reference -> {
                        reference.setSyncStatus("QUEUED");
                        reference.setLastOperationId(operation.getId());
                        reference.setLastSyncAttemptAt(now);
                        reference.setSyncPriority(priority >= PRIORITY_DIRECT_DEMAND ? "HOT" : "WARM");
                        reference.setLastError(null);
                    });
        }
        event(operation.getId(), job.getId(), "OPERATION_CREATED", "INFO",
                "TMDB collection synchronization queued", now);
        return accepted(operation.getId(), "TMDB", externalId);
    }

    private CatalogOperation operation(String type, String provider, String trigger, String status,
            UUID requestedBy, String rootType, String externalId, UUID collectionId, Instant now) {
        CatalogOperation operation = new CatalogOperation();
        operation.setId(UUID.randomUUID());
        operation.setType(type);
        operation.setProvider(provider);
        operation.setTrigger(trigger);
        operation.setStatus(status);
        operation.setRequestedBy(requestedBy);
        operation.setRootEntityType(rootType);
        operation.setRootExternalId(externalId);
        operation.setRootCollectionId(collectionId);
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        return operation;
    }

    private CatalogJob job(String type, String provider, String entityType, String externalId,
            UUID collectionId, UUID operationId, String trigger, int priority, String dedupe,
            Map<String, Object> payload, Instant now) {
        CatalogJob job = new CatalogJob();
        job.setId(UUID.randomUUID());
        job.setOperationId(operationId);
        job.setJobType(type);
        job.setProvider(provider);
        job.setEntityType(entityType);
        job.setExternalId(externalId);
        job.setCollectionId(collectionId);
        job.setPriority(priority);
        job.setTrigger(trigger);
        job.setStatus(PENDING);
        job.setDeduplicationKey(dedupe);
        job.setPayload(payload);
        job.setAvailableAt(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return job;
    }

    private void event(UUID operationId, UUID jobId, String type, String severity, String message, Instant now) {
        CatalogOperationEvent event = new CatalogOperationEvent();
        event.setId(UUID.randomUUID());
        event.setOperationId(operationId);
        event.setJobId(jobId);
        event.setEventType(type);
        event.setSeverity(severity);
        event.setMessage(message);
        event.setOccurredAt(now);
        eventRepository.save(event);
    }

    private CatalogOperationAcceptedResponse accepted(UUID operationId, String provider, String externalId) {
        return new CatalogOperationAcceptedResponse(operationId, "QUEUED", provider, externalId);
    }
}
