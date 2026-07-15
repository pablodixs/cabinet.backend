package com.scriptles.cabinet.media.dto.response;

import java.time.LocalDate;
import java.util.List;

public record SeasonEpisodesResponse(String seriesExternalId, Integer seasonNumber, List<EpisodeResponse> episodes) {
    public record EpisodeResponse(String externalId, Integer episodeNumber, String title, String description,
                                  String stillUrl, LocalDate airDate, Integer runtimeMinutes) {}
}
