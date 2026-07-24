package com.scriptles.cabinet.user.dto.request;

import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpsertTargetLibraryEntryRequest(
        @NotNull @Valid MediaTarget media,
        @NotNull UserMediaStatus status
) {
}
