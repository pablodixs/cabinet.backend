package com.scriptles.cabinet.user.dto.response;

public record ProfileStatsResponse(
        long watchedMinutes,
        long pagesRead,
        long episodesWatched,
        long albumsConsumed,
        long moviesConsumed,
        long seriesConsumed,
        long booksConsumed
) {
}
