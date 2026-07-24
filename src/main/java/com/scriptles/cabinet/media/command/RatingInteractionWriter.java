package com.scriptles.cabinet.media.command;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.catalog.CatalogMaterializationService;
import com.scriptles.cabinet.media.catalog.CatalogResolver;
import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.dto.response.RatingResponse;
import com.scriptles.cabinet.media.enrichment.CatalogOutboxPublisher;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.CatalogStatus;
import com.scriptles.cabinet.media.service.RatingService;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingInteractionWriter {
    private final UserRepository userRepository;
    private final CatalogMaterializationService materializationService;
    private final RatingService ratingService;
    private final CatalogOutboxPublisher outboxPublisher;

    @Transactional
    public RatingResponse upsert(
            UUID userId,
            MediaTarget target,
            CatalogResolver.Resolution resolution,
            BigDecimal rating
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado"));
        Media media = materializationService.findOrCreateCore(target, resolution);
        RatingResponse saved = ratingService.upsertResolved(user, media, rating);
        if (target.source() != null && media.getCatalogStatus() != CatalogStatus.READY) {
            outboxPublisher.publishCoreReady(
                    media.getId(),
                    target.source(),
                    target.externalId(),
                    target.mediaType(),
                    resolution.locale()
            );
        }
        return new RatingResponse(
                media.getId(),
                saved.rating(),
                media.getCatalogStatus(),
                media.getCatalogStatus() != CatalogStatus.READY
        );
    }
}
