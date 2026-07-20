package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Media;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaConsumptionPolicyTest {
    private final MediaConsumptionPolicy policy = new MediaConsumptionPolicy();

    @Test
    void rejectsWorksReleasedAfterToday() {
        Media media = new Media();
        media.setReleaseDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> policy.ensureReleased(media))
                .isInstanceOf(ApiException.class)
                .hasMessage("A obra ainda não foi lançada")
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("MEDIA_NOT_RELEASED");
    }

    @Test
    void allowsWorksReleasedTodayOrWithUnknownDate() {
        Media releasedToday = new Media();
        releasedToday.setReleaseDate(LocalDate.now());
        Media unknownDate = new Media();

        assertThatCode(() -> policy.ensureReleased(releasedToday)).doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureReleased(unknownDate)).doesNotThrowAnyException();
    }
}
