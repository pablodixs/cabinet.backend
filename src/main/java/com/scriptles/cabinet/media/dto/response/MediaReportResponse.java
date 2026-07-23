package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.entity.MediaReport;
import com.scriptles.cabinet.media.enums.*;
import com.scriptles.cabinet.user.enums.AccountTier;

import java.time.Instant;
import java.util.UUID;

public record MediaReportResponse(
        UUID id,
        UUID mediaId,
        ExternalSource source,
        String externalId,
        MediaType mediaType,
        String mediaTitle,
        MediaReportCategory category,
        String description,
        SuggestedTarget suggestedTarget,
        MediaReportStatus status,
        UserSummary reportedBy,
        UserSummary reviewedBy,
        String resolutionNote,
        Instant createdAt,
        Instant resolvedAt
) {
    public static MediaReportResponse from(MediaReport report) {
        SuggestedTarget target = report.getSuggestedTargetSource() == null ? null : new SuggestedTarget(
                report.getSuggestedTargetSource(), report.getSuggestedTargetExternalId(),
                report.getSuggestedTargetType(), report.getSuggestedTargetTitle(), report.getSuggestedRelationType());
        return new MediaReportResponse(
                report.getId(), report.getMedia() == null ? null : report.getMedia().getId(),
                report.getSource(), report.getExternalId(), report.getMediaType(), report.getMediaTitle(),
                report.getCategory(), report.getDescription(), target, report.getStatus(),
                new UserSummary(report.getReportedBy().getId(), report.getReportedBy().getDisplayName(),
                        report.getReportedBy().getUsername(),
                        report.getReportedBy().getAccountTier() == AccountTier.PRO),
                report.getReviewedBy() == null ? null : new UserSummary(
                        report.getReviewedBy().getId(), report.getReviewedBy().getDisplayName(),
                        report.getReviewedBy().getUsername(),
                        report.getReviewedBy().getAccountTier() == AccountTier.PRO),
                report.getResolutionNote(), report.getCreatedAt(), report.getResolvedAt());
    }

    public record SuggestedTarget(
            ExternalSource source,
            String externalId,
            MediaType type,
            String title,
            MediaRelationType relationType
    ) {
    }

    public record UserSummary(UUID id, String displayName, String username, boolean pro) {
    }
}
