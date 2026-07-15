package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.UpsertReviewRequest;
import com.scriptles.cabinet.media.dto.response.ReviewResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.UserMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final BigDecimal MIN_RATING = new BigDecimal("0.5");
    private static final BigDecimal MAX_RATING = new BigDecimal("5.0");
    private static final BigDecimal RATING_STEP = new BigDecimal("0.5");

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final UserMediaService userMediaService;

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> findPublic(UUID mediaId, int page, int size) {
        if (!mediaRepository.existsById(mediaId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada");
        }

        Page<ReviewResponse> reviews = reviewRepository.findByMediaIdAndVisibility(
                        mediaId,
                        Visibility.PUBLIC,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.DESC, "createdAt")
                                        .and(Sort.by(Sort.Direction.DESC, "id"))
                        )
                )
                .map(ReviewResponse::from);
        return PageResponse.from(reviews);
    }

    @Transactional(readOnly = true)
    public Optional<ReviewResponse> findMine(UUID userId, UUID mediaId) {
        return reviewRepository.findByUserIdAndMediaId(userId, mediaId)
                .map(ReviewResponse::from);
    }

    @Transactional
    public ReviewResponse upsert(UUID userId, UUID mediaId, UpsertReviewRequest request) {
        BigDecimal rating = validateRating(request.rating());
        validateVisibility(request.visibility());

        User user = findUser(userId);
        Media media = findMedia(mediaId);
        Review review = reviewRepository.findByUserIdAndMediaId(userId, mediaId)
                .orElseGet(() -> newReview(user, media));

        String content = normalizeContent(request.content());
        review.setRating(rating);
        review.setContent(content);
        review.setContainsSpoilers(content != null && Boolean.TRUE.equals(request.containsSpoilers()));
        review.setVisibility(request.visibility());

        Review saved = reviewRepository.saveAndFlush(review);
        userMediaService.markCompleted(user, media);
        return ReviewResponse.from(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID mediaId) {
        reviewRepository.findByUserIdAndMediaId(userId, mediaId)
                .ifPresent(reviewRepository::delete);
    }

    private Review newReview(User user, Media media) {
        Review review = new Review();
        review.setUser(user);
        review.setMedia(media);
        return review;
    }

    private BigDecimal validateRating(BigDecimal rating) {
        if (rating == null
                || rating.compareTo(MIN_RATING) < 0
                || rating.compareTo(MAX_RATING) > 0
                || rating.remainder(RATING_STEP).compareTo(BigDecimal.ZERO) != 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RATING",
                    "A nota deve estar entre 0,5 e 5, em intervalos de meia estrela"
            );
        }
        return rating.setScale(1, RoundingMode.UNNECESSARY);
    }

    private void validateVisibility(Visibility visibility) {
        if (visibility != Visibility.PUBLIC && visibility != Visibility.PRIVATE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REVIEW_VISIBILITY",
                    "A review deve ser pública ou privada"
            );
        }
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return content.trim();
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Usuário não encontrado"
        ));
    }

    private Media findMedia(UUID mediaId) {
        return mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Mídia não encontrada"
        ));
    }
}
