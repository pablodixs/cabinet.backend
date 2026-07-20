package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.enums.UserMediaStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EpisodeWatchResponse(
        UUID episodeId,
        boolean watched,
        Instant watchedAt,
        List<UUID> changedEpisodeIds,
        UserMediaStatus seriesStatus
) {
}
