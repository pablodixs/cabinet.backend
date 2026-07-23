package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.HeaderSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.HeaderSearchResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.HeaderSearchEntityType;
import com.scriptles.cabinet.media.enums.HeaderSearchScope;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeaderSearchService {
    private static final int RESULT_LIMIT = 5;

    private final MediaRepository mediaRepository;
    private final PersonRepository personRepository;
    private final MediaCreditService mediaCreditService;

    public HeaderSearchResponse search(String query, HeaderSearchScope scope, MediaType type) {
        String trimmedQuery = query.trim();
        List<RankedItem> rankedItems = new ArrayList<>(RESULT_LIMIT * 2);

        if (scope != HeaderSearchScope.ARTIST) {
            addMedia(rankedItems, trimmedQuery, type);
        }
        if (scope != HeaderSearchScope.MEDIA) {
            addArtists(rankedItems, trimmedQuery, type);
        }

        List<HeaderSearchItemResponse> items = rankedItems.stream()
                .sorted(Comparator.comparingInt(RankedItem::relevance)
                        .thenComparing(RankedItem::normalizedTitle)
                        .thenComparing(item -> item.response().entityType())
                        .thenComparing(item -> item.response().id()))
                .limit(RESULT_LIMIT)
                .map(RankedItem::response)
                .toList();
        return new HeaderSearchResponse(items);
    }

    private void addMedia(List<RankedItem> target, String query, MediaType type) {
        List<Media> candidates = mediaRepository.findHeaderSearchCandidates(
                query,
                type == null ? null : type.name(),
                PageRequest.of(0, RESULT_LIMIT)
        );
        Map<UUID, MediaCreditService.CreditSummary> credits = mediaCreditService.summaries(candidates);

        for (Media media : candidates) {
            HeaderSearchItemResponse response = new HeaderSearchItemResponse(
                    media.getId(),
                    HeaderSearchEntityType.MEDIA,
                    media.getTitle(),
                    credits.getOrDefault(media.getId(), MediaCreditService.CreditSummary.empty()).creator(),
                    media.getCoverUrl(),
                    media.getReleaseDate() == null ? null : media.getReleaseDate().getYear()
            );
            target.add(new RankedItem(
                    response,
                    relevance(query, media.getTitle(), media.getOriginalTitle()),
                    normalize(media.getTitle())
            ));
        }
    }

    private void addArtists(List<RankedItem> target, String query, MediaType type) {
        List<Person> candidates = personRepository.findHeaderSearchCandidates(
                query,
                type == null ? null : type.name(),
                PageRequest.of(0, RESULT_LIMIT)
        );
        for (Person artist : candidates) {
            HeaderSearchItemResponse response = new HeaderSearchItemResponse(
                    artist.getId(),
                    HeaderSearchEntityType.ARTIST,
                    artist.getName(),
                    null,
                    artist.getImageUrl(),
                    null
            );
            target.add(new RankedItem(
                    response,
                    relevance(query, artist.getName()),
                    normalize(artist.getName())
            ));
        }
    }

    private int relevance(String query, String... values) {
        String normalizedQuery = normalize(query);
        int best = Integer.MAX_VALUE;
        for (String value : values) {
            String normalizedValue = normalize(value);
            if (normalizedValue.equals(normalizedQuery)) {
                best = Math.min(best, 0);
            } else if (normalizedValue.startsWith(normalizedQuery)) {
                best = Math.min(best, 1);
            } else if (normalizedValue.contains(" " + normalizedQuery)) {
                best = Math.min(best, 2);
            } else if (normalizedValue.contains(normalizedQuery)) {
                best = Math.min(best, 3);
            }
        }
        return best;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private record RankedItem(
            HeaderSearchItemResponse response,
            int relevance,
            String normalizedTitle
    ) {
    }
}
