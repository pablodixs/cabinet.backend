package com.scriptles.cabinet.notifications.dto;

import com.scriptles.cabinet.media.enums.MediaReportStatus;
import com.scriptles.cabinet.notifications.entity.Notification;
import com.scriptles.cabinet.notifications.enums.NotificationType;
import com.scriptles.cabinet.user.enums.AccountTier;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        ActorResponse actor,
        long actorCount,
        SubjectResponse subject,
        String preview,
        MediaReportStatus reportStatus,
        String resolutionNote,
        Instant occurredAt,
        boolean read,
        String href
) {
    public static NotificationResponse from(Notification notification) {
        ActorResponse actor = notification.getActor() == null ? null : new ActorResponse(
                notification.getActor().getId(),
                notification.getActor().getUsername(),
                notification.getActor().getDisplayName(),
                notification.getActor().getAvatarUlr(),
                notification.getActor().getAccountTier() == AccountTier.PRO
        );

        SubjectResponse subject = subject(notification);
        String preview = notification.getComment() == null || notification.getComment().getDeletedAt() != null
                ? null : notification.getComment().getContent();
        MediaReportStatus reportStatus = notification.getReport() == null
                ? null : notification.getReport().getStatus();
        String resolutionNote = notification.getReport() == null
                ? null : notification.getReport().getResolutionNote();

        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                actor,
                notification.getActorCount(),
                subject,
                preview,
                reportStatus,
                resolutionNote,
                notification.getActivityAt(),
                notification.getReadAt() != null,
                href(notification)
        );
    }

    private static SubjectResponse subject(Notification notification) {
        if (notification.getMediaList() != null) {
            return new SubjectResponse("LIST", notification.getMediaList().getId(), notification.getMediaList().getName(), null);
        }
        if (notification.getReview() != null) {
            return new SubjectResponse("REVIEW", notification.getReview().getId(), notification.getReview().getMedia().getTitle(),
                    notification.getReview().getMedia().getTypeValue());
        }
        if (notification.getReport() != null) {
            var reportMedia = notification.getReport().getMedia();
            return new SubjectResponse("REPORT", reportMedia == null ? notification.getReport().getId() : reportMedia.getId(),
                    notification.getReport().getMediaTitle(), reportMedia == null ? null : reportMedia.getTypeValue());
        }
        if (notification.getSeriesEpisode() != null) {
            return new SubjectResponse(
                    "EPISODE",
                    notification.getSeriesEpisode().getEpisodeMedia().getId(),
                    notification.getSeriesEpisode().getSeason().getSeries().getTitle(), "SERIES"
            );
        }
        if (notification.getLetterboxdImportJob() != null) {
            return new SubjectResponse(
                    "LETTERBOXD_IMPORT",
                    notification.getLetterboxdImportJob().getId(),
                    "Importação do Letterboxd", null
            );
        }
        if (notification.getType() == NotificationType.FOLLOWED && notification.getActor() != null) {
            return new SubjectResponse("PROFILE", notification.getActor().getId(),
                    notification.getActor().getUsername(), null);
        }
        if (notification.getMedia() != null) {
            return new SubjectResponse("MEDIA", notification.getMedia().getId(), notification.getMedia().getTitle(),
                    notification.getMedia().getTypeValue());
        }
        return null;
    }

    private static String href(Notification notification) {
        if (notification.getMediaList() != null) {
            return "/listas/" + notification.getMediaList().getId() + "#comments";
        }
        if (notification.getReview() != null) {
            return "/reviews/" + notification.getReview().getId() + "#comments";
        }
        if (notification.getReport() != null && notification.getReport().getMedia() != null) {
            return "/media/" + notification.getReport().getMedia().getId();
        }
        if (notification.getSeriesEpisode() != null) {
            var episode = notification.getSeriesEpisode();
            return "/media/" + episode.getSeason().getSeries().getId()
                    + "?tab=episodes&season=" + episode.getSeason().getSeasonNumber()
                    + "#episode-" + episode.getEpisodeMedia().getId();
        }
        if (notification.getLetterboxdImportJob() != null) {
            return "/importacoes/letterboxd/" + notification.getLetterboxdImportJob().getId();
        }
        if (notification.getMedia() != null) return "/media/" + notification.getMedia().getId();
        if (notification.getType() == NotificationType.FOLLOWED && notification.getActor() != null) {
            return "/usuarios/" + notification.getActor().getUsername();
        }
        return "/notificacoes";
    }

    public record ActorResponse(
            UUID id,
            String username,
            String displayName,
            String avatarUrl,
            boolean pro
    ) {
    }

    public record SubjectResponse(String kind, UUID id, String title, String mediaType) {
    }
}
