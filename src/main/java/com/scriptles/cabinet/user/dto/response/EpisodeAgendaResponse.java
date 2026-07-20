package com.scriptles.cabinet.user.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EpisodeAgendaResponse(
        boolean syncPending,
        Instant lastSyncedAt,
        long overdueCount,
        List<Item> overdue,
        List<Item> upcoming
) {
    public record Item(
            UUID episodeId,
            UUID seriesId,
            String seriesTitle,
            String seriesCoverUrl,
            Integer seasonNumber,
            Integer episodeNumber,
            String episodeTitle,
            String stillUrl,
            LocalDate airDate,
            boolean watched
    ) {
    }
}
