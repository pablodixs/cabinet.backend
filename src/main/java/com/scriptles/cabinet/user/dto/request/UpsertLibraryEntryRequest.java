package com.scriptles.cabinet.user.dto.request;

import com.scriptles.cabinet.user.enums.UserMediaStatus;
import jakarta.validation.constraints.NotNull;

public record UpsertLibraryEntryRequest(
        @NotNull UserMediaStatus status
) {
}
