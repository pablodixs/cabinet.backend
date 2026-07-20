package com.scriptles.cabinet.media.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SeasonEpisodesResponse(UUID seriesId, String seriesExternalId, UUID seasonId, Integer seasonNumber,
                                     Double averageRating, long ratingCount, Double myRating,
                                     long myRatedEpisodeCount, long eligibleEpisodeCount,
                                     List<EpisodeResponse> episodes) {
    public record EpisodeResponse(UUID id, String externalId, Integer episodeNumber, String title, String description,
                                  String stillUrl, LocalDate airDate, Integer runtimeMinutes,
                                  Double averageRating, long ratingCount, Double myRating, boolean ratingAllowed,
                                  boolean watched, long unwatchedPreviousCount) {}
}
