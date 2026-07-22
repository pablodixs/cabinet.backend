package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.importer.LetterboxdImportItem;
import com.scriptles.cabinet.user.importer.LetterboxdImportItemState;

import java.util.UUID;

public record LetterboxdImportItemResponse(
        UUID id,
        String letterboxdUri,
        String title,
        Integer releaseYear,
        LetterboxdImportItemState state,
        UUID selectedMediaId,
        String selectedMediaTitle,
        String selectedMediaCoverUrl,
        String selectedTmdbId,
        String payload,
        String matchCandidates,
        boolean overrideStatus,
        boolean overrideRating,
        boolean overrideReview,
        boolean statusConflict,
        boolean ratingConflict,
        boolean reviewConflict,
        String errorMessage
) {
    public static LetterboxdImportItemResponse from(LetterboxdImportItem item) {
        return new LetterboxdImportItemResponse(item.getId(), item.getLetterboxdUri(), item.getTitle(),
                item.getReleaseYear(), item.getState(),
                item.getSelectedMedia() == null ? null : item.getSelectedMedia().getId(),
                item.getSelectedMedia() == null ? null : item.getSelectedMedia().getTitle(),
                item.getSelectedMedia() == null ? null : item.getSelectedMedia().getCoverUrl(),
                item.getSelectedTmdbId(), item.getPayload(), item.getMatchCandidates(),
                item.isOverrideStatus(), item.isOverrideRating(), item.isOverrideReview(),
                item.isStatusConflict(), item.isRatingConflict(), item.isReviewConflict(), item.getErrorMessage());
    }
}
