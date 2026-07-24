package com.scriptles.cabinet.lists.dto.request;

import com.scriptles.cabinet.media.dto.request.MediaTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddTargetMediaListItemRequest(
        @NotNull @Valid MediaTarget media,
        @Size(max = 2000) String notes
) {
}
