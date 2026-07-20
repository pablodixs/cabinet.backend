package com.scriptles.cabinet.user.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LetterboxdItemPayload(
        boolean watched,
        LocalDate watchedMarkedOn,
        boolean watchlist,
        LocalDate watchlistAddedOn,
        BigDecimal rating,
        LocalDate ratingOn,
        String review,
        LocalDate reviewOn,
        boolean liked,
        LocalDate likedOn,
        List<Activity> activities,
        List<ListMembership> lists
) {
    public LetterboxdItemPayload {
        activities = activities == null ? List.of() : List.copyOf(activities);
        lists = lists == null ? List.of() : List.copyOf(lists);
    }

    public record Activity(
            String sourceKey,
            LocalDate occurredOn,
            LocalDate loggedOn,
            boolean rewatch,
            BigDecimal rating,
            String review,
            List<String> tags,
            boolean markedWatchedOnly
    ) {
        public Activity {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    public record ListMembership(
            String sourceKey,
            String name,
            int position,
            String notes
    ) {
    }
}
