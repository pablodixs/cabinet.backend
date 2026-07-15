package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;

import java.time.Instant;
import java.util.UUID;

public record LibraryEntryResponse(
        UUID id,
        UUID mediaId,
        UserMediaStatus status,
        Instant startedAt,
        Instant completedAt,
        Instant lastInteractionAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static LibraryEntryResponse from(UserMedia entry) {
        return new LibraryEntryResponse(
                entry.getId(),
                entry.getMedia().getId(),
                entry.getStatus(),
                entry.getStartedAt(),
                entry.getCompletedAt(),
                entry.getLastInteractionAt(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
