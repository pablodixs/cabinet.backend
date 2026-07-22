package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.TrendingMediaResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.MediaRelation;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.MediaRelationType;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.MediaRelationRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.service.MediaRankingService;
import com.scriptles.cabinet.media.service.MediaSearchItemAssembler;
import com.scriptles.cabinet.user.enums.InterestTargetType;
import com.scriptles.cabinet.user.enums.RecommendationReasonType;
import com.scriptles.cabinet.user.enums.RecommendationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock InterestGraphService interestGraphService;
    @Mock MediaRepository mediaRepository;
    @Mock MediaCreditRepository mediaCreditRepository;
    @Mock MediaRelationRepository mediaRelationRepository;
    @Mock RatingRepository ratingRepository;
    @Mock MediaSearchItemAssembler mediaSearchItemAssembler;
    @Mock MediaRankingService mediaRankingService;

    @Spy InterestScoringPolicy policy = new InterestScoringPolicy();

    @InjectMocks RecommendationService service;

    @BeforeEach
    void supportedTypes() {
        when(interestGraphService.supportedMediaTypes())
                .thenReturn(Set.of(MediaType.MOVIE, MediaType.SERIES, MediaType.ALBUM, MediaType.BOOK));
    }

    @Test
    void ranksPositiveMatchesAndUsesNegativePeopleAsStrongPenalty() {
        UUID userId = UUID.randomUUID();
        Media preferred = media("Preferred", "Drama");
        Media penalized = media("Penalized", "Drama");
        Person disliked = person("Disliked actor");
        MediaCredit credit = credit(penalized, disliked);

        var genreKey = new InterestGraphService.InterestKey(InterestTargetType.GENRE, "drama");
        var personKey = new InterestGraphService.InterestKey(
                InterestTargetType.PERSON, disliked.getId().toString());
        var profile = new InterestGraphService.InterestProfile(Map.of(
                genreKey, new InterestGraphService.InterestNode(genreKey, "Drama", 4, 10, null),
                personKey, new InterestGraphService.InterestNode(personKey, disliked.getName(), -4, -10, null)
        ), Set.of());
        when(interestGraphService.build(userId)).thenReturn(profile);
        when(mediaRepository.findInterestCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of(preferred, penalized));
        when(mediaRepository.findAllWithGenresByIdIn(any())).thenReturn(List.of(preferred, penalized));
        when(mediaCreditRepository.findAllByMediaIdInOrderByPositionAsc(any())).thenReturn(List.of(credit));
        when(ratingRepository.summarizeRatings(any(), any())).thenReturn(List.of());
        when(mediaSearchItemAssembler.fromImported(List.of(preferred), userId))
                .thenReturn(List.of(response(preferred)));

        var result = service.recommendations(userId, MediaType.MOVIE, 1);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().media().title()).isEqualTo("Preferred");
        assertThat(result.items().getFirst().source()).isEqualTo(RecommendationSource.PERSONALIZED);
        assertThat(result.items().getFirst().reasons()).extracting(reason -> reason.targetType())
                .containsExactly(RecommendationReasonType.GENRE);
        verify(mediaRankingService, never()).trending(any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void returnsViewerAwareTrendingWhenProfileHasNoCandidates() {
        UUID userId = UUID.randomUUID();
        Media trending = media("Trending", "Thriller");
        when(interestGraphService.build(userId)).thenReturn(
                new InterestGraphService.InterestProfile(Map.of(), Set.of()));
        when(mediaRepository.findInterestCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(mediaRankingService.trending(MediaType.MOVIE, 7, 200))
                .thenReturn(new TrendingMediaResponse(List.of(response(trending)), 7));
        when(mediaRepository.findAllById(List.of(trending.getId()))).thenReturn(List.of(trending));
        when(mediaSearchItemAssembler.fromImported(List.of(trending), userId))
                .thenReturn(List.of(response(trending)));

        var result = service.recommendations(userId, MediaType.MOVIE, 1);

        assertThat(result.items().getFirst().source()).isEqualTo(RecommendationSource.TRENDING);
        assertThat(result.items().getFirst().reasons().getFirst().targetType())
                .isEqualTo(RecommendationReasonType.TRENDING);
    }

    @Test
    void recommendsAConnectedWorkAndExplainsTheSeedMedia() {
        UUID userId = UUID.randomUUID();
        Media seed = media("Original", "Drama");
        Media adaptation = media("Adaptation", "Adventure");
        var mediaKey = new InterestGraphService.InterestKey(
                InterestTargetType.MEDIA, seed.getId().toString());
        var mediaNode = new InterestGraphService.InterestNode(
                mediaKey, seed.getTitle(), 4, 10, null);
        when(interestGraphService.build(userId)).thenReturn(
                new InterestGraphService.InterestProfile(Map.of(mediaKey, mediaNode), Set.of(seed.getId())));
        when(mediaRepository.findInterestCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        MediaRelation relation = new MediaRelation();
        relation.setSourceMedia(seed);
        relation.setTargetMedia(adaptation);
        relation.setRelationType(MediaRelationType.ADAPTED_AS);
        when(mediaRelationRepository.findAllConnectedTo(Set.of(seed.getId())))
                .thenReturn(List.of(relation));
        when(mediaRepository.findAllWithGenresByIdIn(Set.of(adaptation.getId())))
                .thenReturn(List.of(adaptation));
        when(mediaCreditRepository.findAllByMediaIdInOrderByPositionAsc(Set.of(adaptation.getId())))
                .thenReturn(List.of());
        when(ratingRepository.summarizeRatings(any(), any())).thenReturn(List.of());
        when(mediaSearchItemAssembler.fromImported(List.of(adaptation), userId))
                .thenReturn(List.of(response(adaptation)));

        var result = service.recommendations(userId, MediaType.MOVIE, 1);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.media().id()).isEqualTo(adaptation.getId());
            assertThat(item.reasons()).extracting(reason -> reason.targetType())
                    .containsExactly(RecommendationReasonType.MEDIA);
        });
    }

    private Media media(String title, String genre) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(MediaType.MOVIE);
        media.setTitle(title);
        media.setGenres(new LinkedHashSet<>(Set.of(genre)));
        return media;
    }

    private Person person(String name) {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName(name);
        person.setExternalSource(ExternalSource.MANUAL);
        return person;
    }

    private MediaCredit credit(Media media, Person person) {
        MediaCredit credit = new MediaCredit();
        credit.setMedia(media);
        credit.setPerson(person);
        credit.setRole(CreditRole.ACTOR);
        credit.setPosition(0);
        return credit;
    }

    private MediaSearchItemResponse response(Media media) {
        return new MediaSearchItemResponse(media.getId(), media.getId().toString(), ExternalSource.MANUAL,
                media.getType(), media.getTitle(), null, null, null, null, true, null, 0);
    }
}
