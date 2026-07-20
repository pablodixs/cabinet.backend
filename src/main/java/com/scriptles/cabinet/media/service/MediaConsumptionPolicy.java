package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Media;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class MediaConsumptionPolicy {
    public void ensureReleased(Media media) {
        if (media.getReleaseDate() != null && media.getReleaseDate().isAfter(LocalDate.now())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MEDIA_NOT_RELEASED",
                    "A obra ainda não foi lançada"
            );
        }
    }
}
