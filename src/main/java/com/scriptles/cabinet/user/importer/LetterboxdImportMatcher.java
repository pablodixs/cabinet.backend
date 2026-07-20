package com.scriptles.cabinet.user.importer;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.ExternalMediaRateLimitException;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LetterboxdImportMatcher {
    private final LetterboxdImportItemRepository itemRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final TmdbClient tmdbClient;
    private final ObjectMapper objectMapper;
    private final UserMediaRepository userMediaRepository;
    private final RatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;

    public void match(LetterboxdImportItem item) {
        if (item.getLetterboxdUri() != null) {
            ExternalReference known = externalReferenceRepository
                    .findBySourceAndExternalId(ExternalSource.LETTERBOXD, item.getLetterboxdUri())
                    .orElse(null);
            if (known != null) {
                item.setSelectedMedia(known.getMedia());
                detectConflicts(item);
                item.setState(hasConflicts(item)
                        ? LetterboxdImportItemState.NEEDS_REVIEW
                        : LetterboxdImportItemState.AUTO_MATCHED);
                item.setMatchCandidates("[]");
                itemRepository.save(item);
                return;
            }
        }

        List<ExternalMedia> results = searchWithRetry(item.getTitle());
        List<Candidate> candidates = results.stream()
                .map(media -> new Candidate(media.externalId(), media.title(), media.originalTitle(),
                        year(media.releaseDate()), media.coverUrl()))
                .toList();
        item.setMatchCandidates(objectMapper.writeValueAsString(candidates));

        List<ExternalMedia> exact = results.stream()
                .filter(media -> item.getReleaseYear() != null
                        && item.getReleaseYear().equals(year(media.releaseDate()))
                        && (LetterboxdExportParser.normalize(item.getTitle()).equals(
                                LetterboxdExportParser.normalize(media.title()))
                            || LetterboxdExportParser.normalize(item.getTitle()).equals(
                                LetterboxdExportParser.normalize(media.originalTitle()))))
                .toList();
        if (exact.size() == 1) {
            ExternalReference imported = externalReferenceRepository.findBySourceAndExternalId(
                    ExternalSource.TMDB, exact.getFirst().externalId()).orElse(null);
            if (imported != null) {
                item.setSelectedMedia(imported.getMedia());
                detectConflicts(item);
                item.setState(hasConflicts(item)
                        ? LetterboxdImportItemState.NEEDS_REVIEW
                        : LetterboxdImportItemState.AUTO_MATCHED);
            } else {
                item.setSelectedTmdbId(exact.getFirst().externalId());
                item.setState(LetterboxdImportItemState.AUTO_MATCHED);
            }
        } else {
            item.setState(LetterboxdImportItemState.NEEDS_REVIEW);
        }
        itemRepository.save(item);
    }

    private void detectConflicts(LetterboxdImportItem item) {
        if (item.getSelectedMedia() == null) return;
        LetterboxdItemPayload payload = objectMapper.readValue(item.getPayload(), LetterboxdItemPayload.class);
        var userId = item.getJob().getUser().getId();
        var mediaId = item.getSelectedMedia().getId();
        item.setStatusConflict((payload.watched() || payload.watchlist())
                && userMediaRepository.existsByUserIdAndMediaId(userId, mediaId));
        item.setRatingConflict(payload.rating() != null
                && ratingRepository.findByUserIdAndMediaId(userId, mediaId).isPresent());
        item.setReviewConflict(payload.review() != null
                && reviewRepository.findByUserIdAndMediaId(userId, mediaId).isPresent());
    }

    private boolean hasConflicts(LetterboxdImportItem item) {
        return item.isStatusConflict() || item.isRatingConflict() || item.isReviewConflict();
    }

    private List<ExternalMedia> searchWithRetry(String title) {
        List<RuntimeException> failures = new ArrayList<>();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return tmdbClient.search(MediaType.MOVIE, title, "pt-BR", 0, 10);
            } catch (ExternalMediaRateLimitException exception) {
                failures.add(exception);
                pause(400L * (1L << attempt));
            } catch (ExternalMediaException exception) {
                failures.add(exception);
                if (attempt < 2) pause(250L * (1L << attempt));
            }
        }
        throw failures.getLast();
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis + java.util.concurrent.ThreadLocalRandom.current().nextLong(150));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalMediaException("Matching interrompido", exception);
        }
    }

    private Integer year(LocalDate date) {
        return date == null ? null : date.getYear();
    }

    public record Candidate(String tmdbId, String title, String originalTitle, Integer year, String coverUrl) {
    }
}
