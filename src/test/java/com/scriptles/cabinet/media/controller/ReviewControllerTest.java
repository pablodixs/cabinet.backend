package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.PopularReviewResponse;
import com.scriptles.cabinet.media.dto.response.ReviewResponse;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.ReviewService;
import com.scriptles.cabinet.security.SecurityConfig;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import(SecurityConfig.class)
class ReviewControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void returnsPopularReviewsWithMediaWithoutAuthentication() throws Exception {
        UUID mediaId = UUID.randomUUID();
        ReviewResponse review = new ReviewResponse(
                UUID.randomUUID(), mediaId, new BigDecimal("4.5"), "Excelente.", false,
                Visibility.PUBLIC, Instant.parse("2026-07-19T12:00:00Z"),
                Instant.parse("2026-07-19T12:00:00Z"), 18, false, List.of(),
                new ReviewResponse.AuthorResponse(UUID.randomUUID(), "ana", "Ana", null)
        );
        MediaSearchItemResponse media = new MediaSearchItemResponse(
                mediaId, "123", ExternalSource.TMDB, MediaType.MOVIE, "Central do Brasil",
                "Walter Salles", null, null, null, true, 4.6, 42
        );
        when(reviewService.findGloballyPopular(null, 12))
                .thenReturn(List.of(new PopularReviewResponse(review, media)));

        mockMvc.perform(get("/v1/reviews/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].review.content").value("Excelente."))
                .andExpect(jsonPath("$[0].review.likeCount").value(18))
                .andExpect(jsonPath("$[0].media.title").value("Central do Brasil"));

        verify(reviewService).findGloballyPopular(null, 12);
    }
}
