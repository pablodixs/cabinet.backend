package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.request.UpsertRatingRequest;
import com.scriptles.cabinet.media.dto.response.RatingResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.media.validation.RatingValue;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.UserMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingService {
    private final RatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;
    private final MediaRepository mediaRepository;
    private final SeriesEpisodeRepository seriesEpisodeRepository;
    private final UserRepository userRepository;
    private final UserMediaService userMediaService;
    private final MediaConsumptionPolicy mediaConsumptionPolicy;
    private final MediaCommunityCacheInvalidator communityCacheInvalidator;

    @Transactional(readOnly = true)
    public Optional<RatingResponse> find(UUID userId, UUID mediaId) {
        return ratingRepository.findByUserIdAndMediaId(userId, mediaId)
                .map(rating -> new RatingResponse(mediaId, rating.getValue()));
    }

    @Transactional
    public RatingResponse upsert(UUID userId, UUID mediaId, UpsertRatingRequest request) {
        BigDecimal value = RatingValue.normalize(request.rating());
        User user = userRepository.findById(userId).orElseThrow(() -> notFound("USER_NOT_FOUND", "Usuário não encontrado"));
        Media media = mediaRepository.findById(mediaId).orElseThrow(() -> notFound("MEDIA_NOT_FOUND", "Mídia não encontrada"));
        return upsertResolved(userId, user, media, value);
    }

    public RatingResponse upsertResolved(User user, Media media, BigDecimal requestedValue) {
        return upsertResolved(user.getId(), user, media, RatingValue.normalize(requestedValue));
    }

    private RatingResponse upsertResolved(
            UUID userId,
            User user,
            Media media,
            BigDecimal value
    ) {
        UUID mediaId = media.getId();
        if (media.getType() == MediaType.EPISODE) validateEpisodeDate(mediaId);
        mediaConsumptionPolicy.ensureReleased(media);

        Rating rating = ratingRepository.findByUserIdAndMediaId(userId, mediaId).orElseGet(() -> {
            Rating created = new Rating();
            created.setUser(user);
            created.setMedia(media);
            created.setVisibility(Visibility.PUBLIC);
            return created;
        });
        rating.setValue(value);
        rating.setRatedAt(Instant.now());
        Rating saved = ratingRepository.saveAndFlush(rating);
        reviewRepository.findByUserIdAndMediaId(userId, mediaId).ifPresent(review -> {
            if (review.getRatingEntity() != saved) {
                review.setRatingEntity(saved);
                reviewRepository.saveAndFlush(review);
            }
        });
        if (media.getType() != MediaType.TRACK && media.getType() != MediaType.EPISODE) {
            userMediaService.markCompleted(user, media);
        }
        communityCacheInvalidator.evict(media);
        return new RatingResponse(
                mediaId,
                saved.getValue(),
                media.getCatalogStatus(),
                media.getCatalogStatus() != com.scriptles.cabinet.media.enums.CatalogStatus.READY
        );
    }

    @Transactional
    public void delete(UUID userId, UUID mediaId) {
        ratingRepository.findByUserIdAndMediaId(userId, mediaId).ifPresent(rating -> {
            var review = reviewRepository.findByRatingId(rating.getId()).orElse(null);
            if (review != null) {
                review.setRatingEntity(null);
                reviewRepository.saveAndFlush(review);
            }
            ratingRepository.delete(rating);
            communityCacheInvalidator.evict(rating.getMedia());
        });
    }

    private void validateEpisodeDate(UUID mediaId) {
        SeriesEpisode episode = seriesEpisodeRepository.findByEpisodeMediaId(mediaId)
                .orElseThrow(() -> notFound("EPISODE_NOT_FOUND", "Episódio não encontrado"));
        if (episode.getAirDate() != null && episode.getAirDate().isAfter(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EPISODE_NOT_AIRED",
                    "O episódio ainda não foi exibido");
        }
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
