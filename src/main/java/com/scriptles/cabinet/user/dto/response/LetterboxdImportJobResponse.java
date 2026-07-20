package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.importer.LetterboxdImportJob;
import com.scriptles.cabinet.user.importer.LetterboxdImportJobState;

import java.time.Instant;
import java.util.UUID;

public record LetterboxdImportJobResponse(
        UUID id,
        LetterboxdImportJobState state,
        int totalItems,
        int matchedItems,
        int reviewItems,
        int importedItems,
        int preservedItems,
        int skippedItems,
        int failedItems,
        String errorMessage,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public static LetterboxdImportJobResponse from(LetterboxdImportJob job) {
        return new LetterboxdImportJobResponse(job.getId(), job.getState(), job.getTotalItems(),
                job.getMatchedItems(), job.getReviewItems(), job.getImportedItems(), job.getPreservedItems(),
                job.getSkippedItems(), job.getFailedItems(), job.getErrorMessage(), job.getExpiresAt(),
                job.getCreatedAt(), job.getUpdatedAt(), job.getCompletedAt());
    }
}
