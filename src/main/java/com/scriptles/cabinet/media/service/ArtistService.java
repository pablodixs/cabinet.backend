package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.ArtistResponse;
import com.scriptles.cabinet.media.dto.response.ArtistWorkResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.catalog.api.CollectionSummaryResponse;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.repository.CollectionArtistRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistService {
    private final PersonRepository personRepository;
    private final MediaCreditRepository mediaCreditRepository;
    private final RatingSummaryService ratingSummaryService;
    private CollectionArtistRepository collectionArtistRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void setCollectionArtistRepository(CollectionArtistRepository repository) { this.collectionArtistRepository = repository; }

    public ArtistResponse findDetails(UUID artistId) {
        Person artist = findArtist(artistId);
        List<CreditRole> roles = mediaCreditRepository.findDistinctRolesByPersonId(artistId)
                .stream()
                .sorted(Comparator.comparingInt(CreditRole::ordinal))
                .toList();
        List<UUID> mediaIds = mediaCreditRepository.findDistinctMediaIdsByPersonId(artistId)
                .stream()
                .distinct()
                .toList();
        Double averageRating = ratingSummaryService.aggregate(mediaIds).averageRating();

        return new ArtistResponse(
                artist.getId(),
                artist.getName(),
                artist.getBiography(),
                artist.getImageUrl(),
                artist.getExternalSource(),
                artist.getExternalId(),
                mediaCreditRepository.countDistinctMediaByPersonId(artistId),
                roles,
                (collectionArtistRepository == null ? List.<com.scriptles.cabinet.catalog.entity.CollectionArtist>of() : collectionArtistRepository.findByArtistId(artistId)).stream()
                        .filter(link -> link.getCollection().getType() == CollectionType.DISCOGRAPHY)
                        .map(link -> new CollectionSummaryResponse(link.getCollection().getId(), link.getCollection().getSlug(), link.getCollection().getTitle(), link.getCollection().getType().name(), null, null))
                        .toList(),
                averageRating
        );
    }

    public PageResponse<ArtistWorkResponse> findWorks(UUID artistId, int page, int size) {
        findArtist(artistId);
        PageRequest pageable = PageRequest.of(page, size);
        Page<Media> mediaPage = mediaCreditRepository.findMediaByPersonId(artistId, pageable);
        List<UUID> mediaIds = mediaPage.getContent().stream().map(Media::getId).toList();
        Map<UUID, List<ArtistWorkResponse.CreditResponse>> creditsByMedia = creditsByMedia(
                artistId,
                mediaIds
        );

        return PageResponse.from(mediaPage.map(media -> new ArtistWorkResponse(
                media.getId(),
                media.getType(),
                media.getTitle(),
                media.getCoverUrl(),
                media.getReleaseDate(),
                List.copyOf(creditsByMedia.getOrDefault(media.getId(), List.of()))
        )));
    }

    private Person findArtist(UUID artistId) {
        return personRepository.findById(artistId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "ARTIST_NOT_FOUND",
                "Artista não encontrado"
        ));
    }

    private Map<UUID, List<ArtistWorkResponse.CreditResponse>> creditsByMedia(
            UUID artistId,
            List<UUID> mediaIds
    ) {
        if (mediaIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<ArtistWorkResponse.CreditResponse>> creditsByMedia = new LinkedHashMap<>();
        for (MediaCredit credit : mediaCreditRepository
                .findAllByPersonIdAndMediaIdInOrderByPositionAsc(artistId, mediaIds)) {
            creditsByMedia.computeIfAbsent(credit.getMedia().getId(), ignored -> new ArrayList<>())
                    .add(new ArtistWorkResponse.CreditResponse(
                            credit.getRole(),
                            credit.getCharacterName()
                    ));
        }
        return creditsByMedia;
    }
}
