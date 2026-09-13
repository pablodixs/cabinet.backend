package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.PersonWorkResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.AlbumCoverService;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalPersonWorksProvider;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import com.scriptles.cabinet.media.translation.ResolvedMediaTranslation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonWorksService {
    private static final int LOCAL_PAGE_SIZE = 200;
    private static final List<ExternalSource> WORK_SOURCES = List.of(
            ExternalSource.TMDB,
            ExternalSource.MUSICBRAINZ
    );

    private final PersonRepository personRepository;
    private final MediaCreditRepository mediaCreditRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final PersonExternalIdentityResolver identityResolver;
    private final PersonWorksCatalogService catalogService;
    private final AlbumCoverService albumCoverService;
    private final CatalogLocaleResolver localeResolver;
    private final MediaTranslationResolver translationResolver;

    public PageResponse<PersonWorkResponse> findWorks(
            UUID personId,
            int page,
            int size,
            String language,
            MediaType type
    ) {
        findPerson(personId);
        String requestedLocale = localeResolver.normalize(
                language == null ? CatalogLocaleResolver.DEFAULT_LOCALE : language
        );

        List<Media> localMedia = findAllLocalMedia(personId).stream()
                .filter(media -> type == null || media.getType() == type)
                .toList();
        Map<UUID, ResolvedMediaTranslation> translationsByMedia = translationsByMedia(
                localMedia, requestedLocale);
        Map<UUID, List<ExternalReference>> referencesByMedia = referencesByMedia(localMedia);
        Map<UUID, List<PersonWorkResponse.CreditResponse>> creditsByMedia = creditsByMedia(
                personId,
                localMedia.stream().map(Media::getId).toList()
        );
        Map<ExternalKey, UUID> importedByExternalKey = importedByExternalKey(referencesByMedia);

        List<WorkCandidate> candidates = new ArrayList<>();
        for (Media media : localMedia) {
            candidates.add(localCandidate(
                    media,
                    referencesByMedia.getOrDefault(media.getId(), List.of()),
                    creditsByMedia.getOrDefault(media.getId(), List.of()),
                    translationsByMedia.get(media.getId())
            ));
        }

        Set<ExternalKey> seenExternalKeys = new LinkedHashSet<>(importedByExternalKey.keySet());
        for (ExternalSource source : WORK_SOURCES) {
            if (!supports(source, type)) {
                continue;
            }
            identityResolver.findExternalId(personId, source).ifPresent(externalId -> {
                PersonWorksCatalogService.CatalogResult catalog = catalogService.find(
                        source, externalId, requestedLocale);
                if (catalog == null || catalog.items() == null) {
                    return;
                }
                for (ExternalPersonWorksProvider.Work work : catalog.items()) {
                    if (work == null || work.media() == null || work.media().externalId() == null
                            || work.media().source() == null
                            || (type != null && work.media().type() != type)) {
                        continue;
                    }
                    ExternalKey key = new ExternalKey(work.media().source(), work.media().externalId());
                    if (!seenExternalKeys.add(key)) {
                        continue;
                    }
                    candidates.add(externalCandidate(work));
                }
            });
        }

        candidates.sort(workOrder());
        long requestedFrom = (long) page * size;
        int fromIndex = requestedFrom >= candidates.size()
                ? candidates.size()
                : (int) requestedFrom;
        int toIndex = Math.min(fromIndex + size, candidates.size());
        List<PersonWorkResponse> items = candidates.subList(fromIndex, toIndex).stream()
                .map(this::toResponse)
                .toList();
        long totalElements = candidates.size();
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1L) / size);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }

    private Person findPerson(UUID personId) {
        return personRepository.findById(personId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "PERSON_NOT_FOUND",
                "Pessoa não encontrada"
        ));
    }

    private List<Media> findAllLocalMedia(UUID personId) {
        List<Media> result = new ArrayList<>();
        int currentPage = 0;
        while (true) {
            Page<Media> page = mediaCreditRepository.findMediaByPersonId(
                    personId,
                    PageRequest.of(currentPage, LOCAL_PAGE_SIZE)
            );
            if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
                break;
            }
            result.addAll(page.getContent());
            if (currentPage + 1 >= page.getTotalPages()) {
                break;
            }
            currentPage++;
        }
        return result.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(Media::getId, media -> media, (first, ignored) -> first,
                                LinkedHashMap::new),
                        values -> new ArrayList<>(values.values())
                ));
    }

    private Map<UUID, List<ExternalReference>> referencesByMedia(List<Media> mediaItems) {
        List<UUID> mediaIds = mediaItems.stream().map(Media::getId).filter(Objects::nonNull).toList();
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        List<ExternalReference> references = externalReferenceRepository.findAllByMediaIdIn(mediaIds);
        if (references == null || references.isEmpty()) {
            // The primary-only query is retained as a fallback for installations
            // whose repository implementation predates the all-references query.
            references = externalReferenceRepository
                    .findAllByMediaIdInAndPrimaryReferenceTrue(mediaIds);
            if (references == null || references.isEmpty()) {
                return Map.of();
            }
        }
        return references.stream()
                .filter(reference -> reference.getMedia() != null && reference.getMedia().getId() != null)
                .collect(Collectors.groupingBy(
                        reference -> reference.getMedia().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<UUID, List<PersonWorkResponse.CreditResponse>> creditsByMedia(
            UUID personId,
            List<UUID> mediaIds
    ) {
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        List<MediaCredit> credits = mediaCreditRepository
                .findAllByPersonIdAndMediaIdInOrderByPositionAsc(personId, mediaIds);
        if (credits == null || credits.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<PersonWorkResponse.CreditResponse>> result = new LinkedHashMap<>();
        for (MediaCredit credit : credits) {
            if (credit.getMedia() == null || credit.getMedia().getId() == null) {
                continue;
            }
            result.computeIfAbsent(credit.getMedia().getId(), ignored -> new ArrayList<>())
                    .add(new PersonWorkResponse.CreditResponse(
                            credit.getRole(),
                            credit.getCharacterName()
                    ));
        }
        result.replaceAll((ignored, values) -> values.stream().distinct().toList());
        return result;
    }

    private Map<ExternalKey, UUID> importedByExternalKey(
            Map<UUID, List<ExternalReference>> referencesByMedia
    ) {
        Map<ExternalKey, UUID> result = new LinkedHashMap<>();
        referencesByMedia.forEach((mediaId, references) -> references.stream()
                .filter(reference -> reference.getSource() != null && hasText(reference.getExternalId()))
                .forEach(reference -> result.putIfAbsent(
                        new ExternalKey(reference.getSource(), reference.getExternalId()), mediaId
                )));
        return result;
    }

    private WorkCandidate localCandidate(
            Media media,
            List<ExternalReference> references,
            List<PersonWorkResponse.CreditResponse> credits,
            ResolvedMediaTranslation translation
    ) {
        ExternalReference primary = references.stream()
                .filter(ExternalReference::isPrimaryReference)
                .findFirst()
                .orElseGet(() -> references.stream().findFirst().orElse(null));
        ExternalSource source = primary == null ? ExternalSource.MANUAL : primary.getSource();
        String externalId = primary == null || !hasText(primary.getExternalId())
                ? media.getId().toString()
                : primary.getExternalId();
        return new WorkCandidate(
                media.getId(),
                externalId,
                source,
                media.getType(),
                translation == null ? media.getTitle() : translation.title(),
                translation == null ? media.getCoverUrl() : translation.coverUrl(),
                media.getReleaseDate(),
                true,
                credits,
                null
        );
    }

    private WorkCandidate externalCandidate(ExternalPersonWorksProvider.Work work) {
        ExternalMedia media = work.media();
        return new WorkCandidate(
                null,
                media.externalId(),
                media.source(),
                media.type(),
                media.title(),
                media.coverUrl(),
                media.releaseDate(),
                false,
                List.of(new PersonWorkResponse.CreditResponse(work.role(), work.characterName())),
                media
        );
    }

    private Map<UUID, ResolvedMediaTranslation> translationsByMedia(
            List<Media> mediaItems,
            String locale
    ) {
        if (mediaItems.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ResolvedMediaTranslation> translations = translationResolver.resolveAll(
                mediaItems, locale);
        return translations == null ? Map.of() : translations;
    }

    private PersonWorkResponse toResponse(WorkCandidate candidate) {
        String coverUrl = candidate.coverUrl();
        if (!candidate.imported()
                && candidate.externalMedia() != null
                && candidate.externalMedia().source() == ExternalSource.MUSICBRAINZ
                && coverUrl == null) {
            coverUrl = albumCoverService.findCoverUrl(candidate.externalId());
        }
        return new PersonWorkResponse(
                candidate.id(),
                candidate.externalId(),
                candidate.source(),
                candidate.type(),
                candidate.title(),
                coverUrl,
                candidate.releaseDate(),
                candidate.imported(),
                List.copyOf(candidate.credits())
        );
    }

    private Comparator<WorkCandidate> workOrder() {
        return Comparator
                .comparing(WorkCandidate::releaseDate,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(WorkCandidate::imported, Comparator.reverseOrder())
                .thenComparing(WorkCandidate::title,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(WorkCandidate::source,
                        Comparator.nullsLast(Comparator.comparing(Enum::name)))
                .thenComparing(WorkCandidate::externalId,
                        Comparator.nullsLast(String::compareTo));
    }

    private boolean supports(ExternalSource source, MediaType type) {
        if (type == null) {
            return true;
        }
        return switch (source) {
            case TMDB -> EnumSet.of(MediaType.MOVIE, MediaType.SERIES).contains(type);
            case MUSICBRAINZ -> EnumSet.of(MediaType.ALBUM, MediaType.TRACK).contains(type);
            default -> false;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ExternalKey(ExternalSource source, String externalId) {
    }

    private record WorkCandidate(
            UUID id,
            String externalId,
            ExternalSource source,
            MediaType type,
            String title,
            String coverUrl,
            LocalDate releaseDate,
            boolean imported,
            List<PersonWorkResponse.CreditResponse> credits,
            ExternalMedia externalMedia
    ) {
    }
}
