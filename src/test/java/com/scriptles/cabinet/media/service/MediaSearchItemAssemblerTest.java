package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaArtworkPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaSearchItemAssemblerTest {
    @Test
    void assemblesImportedCardsWithPrimaryReferenceCreatorAndCommunityRating() {
        ExternalReferenceRepository externalReferenceRepository = mock(ExternalReferenceRepository.class);
        RatingRepository ratingRepository = mock(RatingRepository.class);
        MediaCreditService mediaCreditService = mock(MediaCreditService.class);
        UserArtworkResolver artworkResolver = new UserArtworkResolver(
                mock(UserMediaArtworkPreferenceRepository.class));
        MediaTranslationResolver translationResolver = mock(MediaTranslationResolver.class);
        MediaSearchItemAssembler assembler = new MediaSearchItemAssembler(
                externalReferenceRepository,
                ratingRepository,
                mediaCreditService,
                artworkResolver,
                translationResolver
        );
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(MediaType.ALBUM);
        media.setTitle("An Album");
        media.setReleaseDate(LocalDate.of(2024, 1, 1));
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(ExternalSource.MUSICBRAINZ);
        reference.setExternalId("album-id");
        RatingRepository.MediaRatingProjection rating = mock(RatingRepository.MediaRatingProjection.class);

        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(List.of(media.getId())))
                .thenReturn(List.of(reference));
        when(ratingRepository.summarizeRatings(List.of(media.getId()), Visibility.PUBLIC))
                .thenReturn(List.of(rating));
        when(rating.getMediaId()).thenReturn(media.getId());
        when(rating.getAverageRating()).thenReturn(4.5);
        when(rating.getRatingCount()).thenReturn(8L);
        when(mediaCreditService.summaries(List.of(media))).thenReturn(Map.of(
                media.getId(),
                new MediaCreditService.CreditSummary("An Artist", null, List.of())
        ));
        when(translationResolver.resolveAll(List.of(media), "pt-BR")).thenReturn(Map.of());

        var items = assembler.fromImported(List.of(media));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(media.getId());
            assertThat(item.externalId()).isEqualTo("album-id");
            assertThat(item.source()).isEqualTo(ExternalSource.MUSICBRAINZ);
            assertThat(item.creator()).isEqualTo("An Artist");
            assertThat(item.imported()).isTrue();
            assertThat(item.averageRating()).isEqualTo(4.5);
            assertThat(item.ratingCount()).isEqualTo(8);
        });
    }
}
