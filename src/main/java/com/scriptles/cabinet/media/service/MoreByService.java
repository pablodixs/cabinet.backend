package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.MoreByResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.entity.PersonExternalReference;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.MoreByState;
import com.scriptles.cabinet.media.external.AlbumCoverService;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalPersonWorksProvider;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonExternalReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MoreByService {
    private static final int LOCAL_FETCH_LIMIT = 120;

    private final MediaRepository mediaRepository;
    private final MediaCreditRepository mediaCreditRepository;
    private final PersonExternalReferenceRepository personExternalReferenceRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final PersonWorksCatalogService catalogService;
    private final MediaSearchItemAssembler itemAssembler;
    private final AlbumCoverService albumCoverService;

    public MoreByResponse find(UUID mediaId, String language, int limit) {
        return find(mediaId, language, limit, null, false);
    }

    public MoreByResponse find(UUID mediaId, String language, int limit, UUID viewerId) {
        return find(mediaId, language, limit, viewerId, true);
    }

    private MoreByResponse find(
            UUID mediaId,
            String language,
            int limit,
            UUID viewerId,
            boolean personalized
    ) {
        Media current = mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Mídia não encontrada"
        ));
        Eligibility eligibility = eligibility(current.getType());
        if (eligibility == null) {
            return response(MoreByState.UNSUPPORTED, null, null, false, List.of());
        }

        MediaCredit principal = mediaCreditRepository.findPrincipalByMediaIdAndRole(
                        mediaId, eligibility.role(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
        if (principal == null) {
            return response(MoreByState.EMPTY, eligibility.role(), null, false, List.of());
        }

        Person person = principal.getPerson();
        List<Media> localMedia = mediaCreditRepository.findLocalWorks(
                person.getId(),
                eligibility.role(),
                eligibility.resultType(),
                mediaId,
                PageRequest.of(0, LOCAL_FETCH_LIMIT)
        );
        List<MediaSearchItemResponse> localItems = personalized
                ? itemAssembler.fromImported(localMedia, viewerId)
                : itemAssembler.fromImported(localMedia);

        String personExternalId = externalId(person, principal, eligibility.source());
        boolean incomplete = personExternalId == null;
        List<ExternalPersonWorksProvider.Work> externalWorks = List.of();
        if (personExternalId != null) {
            PersonWorksCatalogService.CatalogResult catalog = catalogService.find(
                    eligibility.source(), personExternalId, language);
            externalWorks = catalog.items();
            incomplete = catalog.incomplete();
        }

        Set<ExternalKey> currentReferences = currentReferences(mediaId);
        List<ExternalMedia> externalMedia = externalWorks.stream()
                .map(ExternalPersonWorksProvider.Work::media)
                .filter(media -> media.type() == eligibility.resultType())
                .filter(media -> !currentReferences.contains(new ExternalKey(media.source(), media.externalId())))
                .map(media -> media.withCreator(person.getName()))
                .limit(limit)
                .map(media -> eligibility.source() == ExternalSource.MUSICBRAINZ && media.coverUrl() == null
                        ? media.withCoverUrl(albumCoverService.findCoverUrl(media.externalId()))
                        : media)
                .toList();
        List<MediaSearchItemResponse> externalItems = (personalized
                ? itemAssembler.fromExternal(externalMedia, viewerId)
                : itemAssembler.fromExternal(externalMedia)).stream()
                .filter(item -> !mediaId.equals(item.id()))
                .toList();

        List<MediaSearchItemResponse> items = eligibility.source() == ExternalSource.TMDB
                ? mergeMovies(externalItems, localItems, limit)
                : mergeMusic(externalItems, localItems, limit);
        MoreByState state = items.isEmpty() ? MoreByState.EMPTY : MoreByState.READY;
        return response(state, eligibility.role(), person, incomplete, items);
    }

    private List<MediaSearchItemResponse> mergeMovies(
            List<MediaSearchItemResponse> external,
            List<MediaSearchItemResponse> local,
            int limit
    ) {
        LinkedHashMap<String, MediaSearchItemResponse> result = new LinkedHashMap<>();
        external.forEach(item -> result.putIfAbsent(itemKey(item), item));
        local.forEach(item -> result.putIfAbsent(itemKey(item), item));
        return result.values().stream().limit(limit).toList();
    }

    private List<MediaSearchItemResponse> mergeMusic(
            List<MediaSearchItemResponse> external,
            List<MediaSearchItemResponse> local,
            int limit
    ) {
        Map<String, MediaSearchItemResponse> result = new LinkedHashMap<>();
        external.forEach(item -> result.putIfAbsent(itemKey(item), item));
        local.forEach(item -> result.putIfAbsent(itemKey(item), item));
        return result.values().stream()
                .sorted(Comparator
                        .comparing(
                                MediaSearchItemResponse::releaseDate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MediaSearchItemResponse::title, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MediaSearchItemResponse::externalId))
                .limit(limit)
                .toList();
    }

    private String itemKey(MediaSearchItemResponse item) {
        return item.id() != null
                ? "media:" + item.id()
                : item.source() + ":" + item.externalId();
    }

    private Set<ExternalKey> currentReferences(UUID mediaId) {
        Set<ExternalKey> references = new LinkedHashSet<>();
        for (ExternalReference reference : externalReferenceRepository.findAllByMediaId(mediaId)) {
            references.add(new ExternalKey(reference.getSource(), reference.getExternalId()));
        }
        return references;
    }

    private String externalId(Person person, MediaCredit credit, ExternalSource source) {
        String referenced = personExternalReferenceRepository.findFirstByPersonIdAndSource(person.getId(), source)
                .map(PersonExternalReference::getExternalId)
                .filter(this::hasText)
                .orElse(null);
        if (referenced != null) {
            return referenced;
        }
        if (credit.getSource() == source && hasText(credit.getExternalId())) {
            return credit.getExternalId();
        }
        if (person.getExternalSource() == source && hasText(person.getExternalId())) {
            return person.getExternalId();
        }
        return null;
    }

    private Eligibility eligibility(MediaType type) {
        return switch (type) {
            case MOVIE -> new Eligibility(CreditRole.DIRECTOR, MediaType.MOVIE, ExternalSource.TMDB);
            case ALBUM, TRACK -> new Eligibility(CreditRole.ARTIST, MediaType.ALBUM, ExternalSource.MUSICBRAINZ);
            case BOOK, SERIES, EPISODE -> null;
        };
    }

    private MoreByResponse response(
            MoreByState state,
            CreditRole role,
            Person person,
            boolean incomplete,
            List<MediaSearchItemResponse> items
    ) {
        MoreByResponse.PersonResponse personResponse = person == null
                ? null
                : new MoreByResponse.PersonResponse(person.getId(), person.getName(), person.getImageUrl());
        return new MoreByResponse(state, role, personResponse, incomplete, List.copyOf(items));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Eligibility(CreditRole role, MediaType resultType, ExternalSource source) {
    }

    private record ExternalKey(ExternalSource source, String externalId) {
    }
}
