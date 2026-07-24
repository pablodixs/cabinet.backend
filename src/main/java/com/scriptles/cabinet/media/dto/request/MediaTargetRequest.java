package com.scriptles.cabinet.media.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MediaTargetRequest(@NotNull @Valid MediaTarget media) {
}
