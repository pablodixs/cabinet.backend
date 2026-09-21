package com.scriptles.cabinet.catalog.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CollectionAuditResponse(UUID collectionId, String title, String provider, String externalId,
        Sync sync, SourceSnapshot source, Manifest manifest, List<FieldComparison> fields,
        List<ManifestItem> items) {
    public record Sync(String status, String priority, Instant lastAttemptAt, Instant lastSuccessfulSyncAt,
                       Instant nextSyncAt, String lastError, UUID lastOperationId) {
    }

    public record SourceSnapshot(String contentHash, int itemCount, Instant fetchedAt) {
    }

    public record Manifest(long total, long resolved, long queued, long failed, long removed) {
    }

    public record FieldComparison(String field, String sourceValue, String effectiveValue, String owner) {
    }

    public record ManifestItem(String externalId, String title, UUID mediaId, String cabinetTitle,
            int position, String resolutionStatus, boolean sourcePresent, LocalDate releaseDate,
            Instant firstSeenAt, Instant lastSeenAt, Instant removedAt, String lastError) {
    }
}
