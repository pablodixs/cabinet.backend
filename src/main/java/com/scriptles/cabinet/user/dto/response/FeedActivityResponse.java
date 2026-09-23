package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.UserFeedActivity;
import com.scriptles.cabinet.user.enums.FeedActionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FeedActivityResponse(
        UUID id,
        FeedActionType action,
        Instant occurredAt,
        UUID actorId,
        String actorUsername,
        String actorDisplayName,
        String actorAvatarUrl,
        UUID mediaId,
        MediaType mediaType,
        String title,
        String coverUrl,
        BigDecimal rating,
        String review,
        boolean containsSpoilers,
        boolean liked,
        boolean reviewed
) {
    public static FeedActivityResponse from(UserFeedActivity activity, BigDecimal rating, String review,
                                            boolean containsSpoilers, boolean liked, boolean reviewed) {
        var actor = activity.getUser();
        var media = activity.getMedia();
        return new FeedActivityResponse(activity.getId(), activity.getActionType(), activity.getOccurredAt(),
                actor.getId(), actor.getUsername(), actor.getDisplayName(), actor.getAvatarUlr(),
                media.getId(), media.getType(), media.getTitle(), media.getCoverUrl(),
                rating, review, containsSpoilers, liked, reviewed);
    }
}
