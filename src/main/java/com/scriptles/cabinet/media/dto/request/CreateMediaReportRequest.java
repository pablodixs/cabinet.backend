package com.scriptles.cabinet.media.dto.request;

import com.scriptles.cabinet.media.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateMediaReportRequest(
        UUID mediaId,
        @NotNull ExternalSource source,
        @NotBlank @Size(max = 300) String externalId,
        @NotNull MediaType mediaType,
        @NotBlank @Size(max = 300) String mediaTitle,
        @NotNull MediaReportCategory category,
        @NotBlank @Size(max = 2000) String description,
        ExternalSource suggestedTargetSource,
        @Size(max = 300) String suggestedTargetExternalId,
        MediaType suggestedTargetType,
        @Size(max = 300) String suggestedTargetTitle,
        MediaRelationType suggestedRelationType
) {
}
