package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.MediaLike;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.user.entity.UserInterestPreference;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.dto.request.UpsertInterestPreferenceRequest;
import com.scriptles.cabinet.user.enums.InterestPreference;
import com.scriptles.cabinet.user.enums.InterestTargetType;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserInterestPreferenceRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InterestGraphServiceTest {
    @Mock UserInterestPreferenceRepository preferenceRepository;
    @Mock RatingRepository ratingRepository;
    @Mock MediaLikeRepository mediaLikeRepository;
    @Mock UserMediaRepository userMediaRepository;
    @Mock UserRepository userRepository;
    @Mock MediaRepository mediaRepository;
    @Mock MediaCreditRepository mediaCreditRepository;
    @Mock PersonRepository personRepository;

    @Spy InterestScoringPolicy policy = new InterestScoringPolicy();

    @InjectMocks InterestGraphService service;

    @Test
    void buildsSignalsAndLetsExplicitGenreOverrideTheInference() {
        UUID userId = UUID.randomUUID();
        Media media = media("Solaris", "Drama", "Science Fiction");
        Rating rating = new Rating();
        rating.setMedia(media);
        rating.setValue(new BigDecimal("5.0"));
        MediaLike like = new MediaLike();
        like.setMedia(media);
        UserMedia library = new UserMedia();
        library.setMedia(media);
        library.setStatus(UserMediaStatus.COMPLETED);

        Person director = new Person();
        director.setId(UUID.randomUUID());
        director.setName("Andrei Tarkovsky");
        director.setExternalSource(ExternalSource.MANUAL);
        MediaCredit credit = new MediaCredit();
        credit.setMedia(media);
        credit.setPerson(director);
        credit.setRole(CreditRole.DIRECTOR);

        UserInterestPreference explicit = new UserInterestPreference();
        explicit.setTargetType(InterestTargetType.GENRE);
        explicit.setGenreKey("drama");
        explicit.setGenreLabel("Drama");
        explicit.setPreference(InterestPreference.NEGATIVE);

        when(ratingRepository.findAllByUserId(userId)).thenReturn(List.of(rating));
        when(mediaLikeRepository.findAllByUserId(userId)).thenReturn(List.of(like));
        when(userMediaRepository.findAllByUserId(userId)).thenReturn(List.of(library));
        when(preferenceRepository.findAllByUserId(userId)).thenReturn(List.of(explicit));
        when(mediaRepository.findAllWithGenresByIdIn(Set.of(media.getId()))).thenReturn(List.of(media));
        when(mediaCreditRepository.findAllByMediaIdInOrderByPositionAsc(Set.of(media.getId())))
                .thenReturn(List.of(credit));

        var profile = service.build(userId);
        var drama = profile.nodes().get(new InterestGraphService.InterestKey(
                InterestTargetType.GENRE, "drama"));
        var person = profile.nodes().get(new InterestGraphService.InterestKey(
                InterestTargetType.PERSON, director.getId().toString()));
        var work = profile.nodes().get(new InterestGraphService.InterestKey(
                InterestTargetType.MEDIA, media.getId().toString()));

        assertThat(work.inferredScore()).isEqualTo(6.0);
        assertThat(drama.inferredScore()).isPositive();
        assertThat(drama.effectiveScore()).isEqualTo(-10.0);
        assertThat(person.effectiveScore()).isPositive();
        assertThat(profile.interactedMediaIds()).containsExactly(media.getId());
    }

    @Test
    void upsertsAndClearsAnExplicitGenreOverride() {
        UUID userId = UUID.randomUUID();
        User user = User.create("reader@example.com", "reader", "Reader", "hash");
        user.setId(userId);
        UserInterestPreference existing = new UserInterestPreference();
        existing.setUser(user);
        existing.setTargetType(InterestTargetType.GENRE);
        existing.setGenreKey("science fiction");
        existing.setGenreLabel("Science Fiction");
        existing.setPreference(InterestPreference.POSITIVE);

        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(mediaRepository.findGenreLabels(
                org.mockito.ArgumentMatchers.eq("science fiction"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of("Science Fiction"));
        when(preferenceRepository.findByUserIdAndTargetTypeAndGenreKey(
                userId, InterestTargetType.GENRE, "science fiction"))
                .thenReturn(java.util.Optional.of(existing));
        when(preferenceRepository.findAllByUserId(userId)).thenReturn(List.of(existing));
        when(ratingRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(mediaLikeRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(userMediaRepository.findAllByUserId(userId)).thenReturn(List.of());

        var response = service.upsert(userId, new UpsertInterestPreferenceRequest(
                InterestTargetType.GENRE, " Science   Fiction ", InterestPreference.NEGATIVE));

        assertThat(response.explicitPreference()).isEqualTo(InterestPreference.NEGATIVE);
        assertThat(response.label()).isEqualTo("Science Fiction");
        verify(preferenceRepository).saveAndFlush(existing);

        service.delete(userId, InterestTargetType.GENRE, "science fiction");
        verify(preferenceRepository).delete(existing);
        verify(preferenceRepository).flush();
    }

    private Media media(String title, String... genres) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(MediaType.MOVIE);
        media.setTitle(title);
        media.setGenres(new LinkedHashSet<>(List.of(genres)));
        return media;
    }
}
