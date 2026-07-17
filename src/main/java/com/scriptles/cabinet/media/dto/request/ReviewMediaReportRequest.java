package com.scriptles.cabinet.media.dto.request;

import com.scriptles.cabinet.media.enums.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewMediaReportRequest(
        @NotNull MediaReportDecision decision,
        @Size(max = 2000) String resolutionNote,
        ExternalSource targetSource,
        @Size(max = 300) String targetExternalId,
        MediaType targetType,
        MediaRelationType relationType
) {
}
