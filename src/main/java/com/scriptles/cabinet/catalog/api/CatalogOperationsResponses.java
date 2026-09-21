package com.scriptles.cabinet.catalog.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogOperationsResponses {
    private CatalogOperationsResponses() {
    }

    public record OperationSummary(UUID id, String type, String provider, String trigger, String status,
                                   UUID requestedBy, String rootEntityType, String rootExternalId,
                                   UUID rootCollectionId, long totalItems, long processedItems,
                                   long createdItems, long updatedItems, long unchangedItems, long failedItems,
                                   Instant createdAt, Instant startedAt, Instant completedAt) {
    }

    public record OperationDetails(OperationSummary operation, List<JobSummary> jobs, List<Event> events) {
    }

    public record JobSummary(UUID id, UUID operationId, String jobType, String provider, String entityType,
                             String externalId, UUID collectionId, UUID mediaId, int priority, String trigger,
                             String status, int attempts, Instant availableAt, Instant lockedAt,
                             String lockedBy, Instant createdAt, Instant startedAt, Instant completedAt,
                             String lastError) {
    }

    public record JobDetails(JobSummary job, String deduplicationKey, Map<String, Object> payload,
                             List<Attempt> attempts) {
    }

    public record Attempt(int attemptNumber, String workerId, String status, Instant startedAt,
                          Instant finishedAt, Long durationMs, String errorClass, String errorMessage,
                          Map<String, Object> metrics) {
    }

    public record Event(String eventType, String severity, String entityType, UUID entityId,
                        String externalId, String message, Map<String, Object> metadata, Instant occurredAt) {
    }

    public record QueueStatus(long pending, long processing, long retry, long dead,
                              Long oldestPendingAgeSeconds, Long oldestRetryAgeSeconds) {
    }

    public record ExternalEntity(UUID id, String provider, String entityType, String externalId,
                                 String displayName, String state, Instant firstSeenAt,
                                 Instant lastSeenAt, Instant removedAt, Map<String, Object> sourceMetadata) {
    }
}
