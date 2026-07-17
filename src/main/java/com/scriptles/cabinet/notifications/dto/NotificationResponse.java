package com.scriptles.cabinet.notifications.dto;

import com.scriptles.cabinet.media.enums.MediaReportStatus;
import com.scriptles.cabinet.notifications.entity.Notification;
import com.scriptles.cabinet.notifications.enums.NotificationType;

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
                notification.getActor().getAvatarUlr()
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
            return new SubjectResponse("LIST", notification.getMediaList().getId(), notification.getMediaList().getName());
        }
        if (notification.getReview() != null) {
            return new SubjectResponse("REVIEW", notification.getReview().getId(), notification.getReview().getMedia().getTitle());
        }
        if (notification.getReport() != null) {
            return new SubjectResponse("REPORT", notification.getReport().getId(), notification.getReport().getMediaTitle());
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
        return "/notificacoes";
    }

    public record ActorResponse(UUID id, String username, String displayName, String avatarUrl) {
    }

    public record SubjectResponse(String kind, UUID id, String title) {
    }
}
