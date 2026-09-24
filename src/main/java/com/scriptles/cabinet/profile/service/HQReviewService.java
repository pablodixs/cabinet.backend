package com.scriptles.cabinet.profile.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.profile.entity.HQReview;
import com.scriptles.cabinet.profile.enums.HQClaimStatus;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import com.scriptles.cabinet.profile.repository.HQProfileRepository;
import com.scriptles.cabinet.profile.repository.HQReviewRepository;
import com.scriptles.cabinet.security.AuthenticatedHQ;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class HQReviewService {
    private final HQReviewRepository reviews;
    private final HQProfileRepository hqs;
    private final MediaRepository media;

    public record ReviewInput(@NotBlank @Size(max = 20000) String content, boolean containsSpoilers, BigDecimal rating) {}
    public record ReviewView(UUID id, UUID mediaId, String mediaTitle, String mediaCoverUrl, String content,
                             boolean containsSpoilers, BigDecimal rating, Instant createdAt, Instant updatedAt) {}

    @Transactional(readOnly = true)
    public List<ReviewView> mine(AuthenticatedHQ actor) {
        return reviews.findByHqProfileIdOrderByCreatedAtDesc(actor.hqId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewView> publicReviews(String handle) {
        var hq = hqs.findByProfileHandleIgnoreCase(handle).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "HQ não encontrada"));
        return reviews.findByHqProfileIdOrderByCreatedAtDesc(hq.getId()).stream().map(this::view).toList();
    }

    @Transactional
    public ReviewView upsert(AuthenticatedHQ actor, UUID mediaId, ReviewInput input) {
        if (actor.role() != HQMemberRole.OWNER && actor.role() != HQMemberRole.ADMIN && actor.role() != HQMemberRole.EDITOR) {
            throw error(HttpStatus.FORBIDDEN, "HQ_FORBIDDEN", "Permissão insuficiente");
        }
        var hq = hqs.findById(actor.hqId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "HQ não encontrada"));
        if (hq.getClaimStatus() != HQClaimStatus.CLAIMED) throw error(HttpStatus.FORBIDDEN, "HQ_UNAVAILABLE", "HQ indisponível para publicação");
        if (input.rating() != null && (input.rating().compareTo(new BigDecimal("0.5")) < 0 || input.rating().compareTo(new BigDecimal("5.0")) > 0 || input.rating().multiply(new BigDecimal("2")).remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_RATING", "A nota deve variar de 0,5 a 5 em intervalos de 0,5");
        }
        var selected = media.findById(mediaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Obra não encontrada"));
        HQReview review = reviews.findByHqProfileIdAndMediaId(hq.getId(), mediaId).orElseGet(() -> {
            HQReview fresh = new HQReview(); fresh.setHqProfile(hq); fresh.setMedia(selected); return fresh;
        });
        review.setContent(input.content().trim()); review.setContainsSpoilers(input.containsSpoilers()); review.setRating(input.rating());
        return view(reviews.save(review));
    }

    @Transactional
    public void delete(AuthenticatedHQ actor, UUID mediaId) {
        if (actor.role() != HQMemberRole.OWNER && actor.role() != HQMemberRole.ADMIN) throw error(HttpStatus.FORBIDDEN, "HQ_FORBIDDEN", "Permissão insuficiente");
        reviews.findByHqProfileIdAndMediaId(actor.hqId(), mediaId).ifPresent(reviews::delete);
    }

    private ReviewView view(HQReview review) {
        return new ReviewView(review.getId(), review.getMedia().getId(), review.getMedia().getTitle(), review.getMedia().getCoverUrl(),
                review.getContent(), review.isContainsSpoilers(), review.getRating(), review.getCreatedAt(), review.getUpdatedAt());
    }
    private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
