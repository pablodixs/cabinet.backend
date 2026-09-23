package com.scriptles.cabinet.media.dto.request;

import jakarta.validation.constraints.Size;

public record UpsertReviewBackdropRequest(
        @Size(max = 500) String backdropKey
) {}
