package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.request.UpsertRatingRequest;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.*;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.service.UserMediaService;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {
    @Mock RatingRepository ratingRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock MediaRepository mediaRepository;
    @Mock SeriesEpisodeRepository episodeRepository;
    @Mock UserRepository userRepository;
    @Mock UserMediaService userMediaService;
    @Mock MediaConsumptionPolicy mediaConsumptionPolicy;
    @InjectMocks RatingService service;

    @Test
    void createsPublicTrackRatingWithoutLibrarySideEffects() {
        UUID userId = UUID.randomUUID(), mediaId = UUID.randomUUID();
        User user = new User();
        Media track = media(mediaId, MediaType.TRACK);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(track));
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(ratingRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upsert(userId, mediaId, new UpsertRatingRequest(new BigDecimal("4.5")));

        assertThat(response.rating()).isEqualByComparingTo("4.5");
        verify(ratingRepository).saveAndFlush(argThat(rating ->
                rating.getMedia() == track && rating.getUser() == user));
        verifyNoInteractions(userMediaService);
    }

    @Test
    void createsPrimaryMediaRatingAndMarksItCompleted() {
        UUID userId = UUID.randomUUID(), mediaId = UUID.randomUUID();
        User user = new User();
        Media album = media(mediaId, MediaType.ALBUM);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(album));
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(ratingRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upsert(userId, mediaId,
                new UpsertRatingRequest(new BigDecimal("4.0")));

        assertThat(response.rating()).isEqualByComparingTo("4.0");
        verify(userMediaService).markCompleted(user, album);
    }

    @Test
    void rejectsInvalidSteps() {
        assertThatThrownBy(() -> service.upsert(UUID.randomUUID(), UUID.randomUUID(),
                new UpsertRatingRequest(new BigDecimal("4.2"))))
                .isInstanceOf(ApiException.class).hasMessageContaining("meia estrela");
    }

    @Test
    void findsTheUsersCurrentRating() {
        UUID userId = UUID.randomUUID(), mediaId = UUID.randomUUID();
        Rating rating = new Rating();
        rating.setValue(new BigDecimal("3.5"));
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId))
                .thenReturn(Optional.of(rating));

        assertThat(service.find(userId, mediaId))
                .get().extracting(response -> response.rating())
                .isEqualTo(new BigDecimal("3.5"));
    }

    @Test
    void rejectsFutureEpisodeButAllowsMissingAirDate() {
        UUID userId = UUID.randomUUID(), mediaId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media(mediaId, MediaType.EPISODE)));
        SeriesEpisode future = new SeriesEpisode();
        future.setAirDate(LocalDate.now().plusDays(1));
        when(episodeRepository.findByEpisodeMediaId(mediaId)).thenReturn(Optional.of(future));
        assertThatThrownBy(() -> service.upsert(userId, mediaId,
                new UpsertRatingRequest(new BigDecimal("5.0"))))
                .isInstanceOf(ApiException.class).hasMessageContaining("ainda não foi exibido");

        future.setAirDate(null);
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(ratingRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.upsert(userId, mediaId,
                new UpsertRatingRequest(new BigDecimal("5.0"))).rating()).isEqualByComparingTo("5.0");
    }

    @Test
    void rejectsRatingAnUnreleasedTrack() {
        UUID userId = UUID.randomUUID(), mediaId = UUID.randomUUID();
        User user = new User();
        Media track = media(mediaId, MediaType.TRACK);
        track.setReleaseDate(LocalDate.now().plusDays(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(track));
        doThrow(new ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "MEDIA_NOT_RELEASED",
                "A obra ainda não foi lançada"
        )).when(mediaConsumptionPolicy).ensureReleased(track);

        assertThatThrownBy(() -> service.upsert(
                userId, mediaId, new UpsertRatingRequest(new BigDecimal("4.0"))))
                .isInstanceOf(ApiException.class)
                .hasMessage("A obra ainda não foi lançada");
        verify(ratingRepository, never()).saveAndFlush(any());
    }

    @Test
    void deletingMissingRatingIsIdempotent() {
        UUID userId = UUID.randomUUID(), mediaId = UUID.randomUUID();
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        service.delete(userId, mediaId);
        verify(ratingRepository, never()).delete(any());
    }

    @Test
    void deletingARatingKeepsAndUnlinksTheCanonicalReview() {
        UUID userId = UUID.randomUUID(), mediaId = UUID.randomUUID();
        Rating rating = new Rating();
        rating.setId(UUID.randomUUID());
        Review review = new Review();
        review.setRatingEntity(rating);
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId))
                .thenReturn(Optional.of(rating));
        when(reviewRepository.findByRatingId(rating.getId())).thenReturn(Optional.of(review));

        service.delete(userId, mediaId);

        assertThat(review.getRatingEntity()).isNull();
        verify(reviewRepository).saveAndFlush(review);
        verify(ratingRepository).delete(rating);
    }

    private Media media(UUID id, MediaType type) {
        Media media = new Media();
        media.setId(id);
        media.setType(type);
        return media;
    }
}
