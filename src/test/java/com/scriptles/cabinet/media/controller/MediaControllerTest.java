package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.request.ImportExternalMediaRequest;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.MediaCommunityUserResponse;
import com.scriptles.cabinet.media.dto.response.RelatedMediaResponse;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.ExternalMediaService;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
@Import({SecurityConfig.class, CatalogLocaleResolver.class})
class MediaControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExternalMediaService externalMediaService;

    @Test
    void returnsCommunityStatsInMediaDetails() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(externalMediaService.findDetails(
                ExternalSource.TMDB, MediaType.MOVIE, "550", "pt-BR"))
                .thenReturn(new ExternalMediaDetailsResponse(
                        mediaId,
                        "550",
                        ExternalSource.TMDB,
                        MediaType.MOVIE,
                        "Fight Club",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        List.of(),
                        List.of(new ExternalMediaDetailsResponse.CreditResponse(
                                UUID.randomUUID(),
                                "David Fincher",
                                CreditRole.DIRECTOR,
                                null,
                                0,
                                null,
                                ExternalSource.TMDB,
                                "7467"
                        )),
                        true,
                        12,
                        List.of(new MediaCommunityUserResponse(
                                UUID.randomUUID(), "ana", "https://example.com/ana.jpg")),
                        4.25,
                        List.of(
                                new ExternalMediaDetailsResponse.RatingDistributionBucket(4.0, 1),
                                new ExternalMediaDetailsResponse.RatingDistributionBucket(5.0, 3)
                        ),
                        7,
                        31,
                        List.of(new MediaCommunityUserResponse(
                                UUID.randomUUID(), "bia", "https://example.com/bia.jpg")),
                        new ExternalMediaDetailsResponse.MovieDetails(null, null, null, null)
                ));

        mockMvc.perform(get("/v1/media/external/TMDB/MOVIE/550"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Language", "pt-BR"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeaders("Vary")).contains("Accept-Language"))
                .andExpect(jsonPath("$.likeCount").value(12))
                .andExpect(jsonPath("$.recentLikers[0].username").value("ana"))
                .andExpect(jsonPath("$.recentLikers[0].avatarUrl")
                        .value("https://example.com/ana.jpg"))
                .andExpect(jsonPath("$.averageRating").value(4.25))
                .andExpect(jsonPath("$.ratingDistribution[0].rating").value(4.0))
                .andExpect(jsonPath("$.ratingDistribution[0].count").value(1))
                .andExpect(jsonPath("$.ratingDistribution[1].rating").value(5.0))
                .andExpect(jsonPath("$.ratingDistribution[1].count").value(3))
                .andExpect(jsonPath("$.credits[0].name").value("David Fincher"))
                .andExpect(jsonPath("$.credits[0].role").value("DIRECTOR"))
                .andExpect(jsonPath("$.listCount").value(7))
                .andExpect(jsonPath("$.completedCount").value(31))
                .andExpect(jsonPath("$.recentCompleters[0].username").value("bia"))
                .andExpect(jsonPath("$.recentCompleters[0].avatarUrl")
                        .value("https://example.com/bia.jpg"));
    }

    @Test
    void usesAcceptLanguageForExternalDetailsAndExplicitLanguageOverridesIt() throws Exception {
        when(externalMediaService.findDetails(
                ExternalSource.TMDB, MediaType.MOVIE, "550", "en-US"))
                .thenReturn(null);

        mockMvc.perform(get("/v1/media/external/TMDB/MOVIE/550")
                        .header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Language", "en-US"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeaders("Vary")).contains("Accept-Language"));
        verify(externalMediaService).findDetails(
                ExternalSource.TMDB, MediaType.MOVIE, "550", "en-US");

        when(externalMediaService.findDetails(
                ExternalSource.TMDB, MediaType.MOVIE, "550", "pt-BR"))
                .thenReturn(null);
        mockMvc.perform(get("/v1/media/external/TMDB/MOVIE/550")
                        .param("language", "pt-BR")
                        .header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Language", "pt-BR"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeaders("Vary")).doesNotContain("Accept-Language"));
        verify(externalMediaService).findDetails(
                ExternalSource.TMDB, MediaType.MOVIE, "550", "pt-BR");
    }

    @Test
    void returnsRelatedMediaWithDefaults() throws Exception {
        when(externalMediaService.findRelations(
                ExternalSource.TMDB, MediaType.MOVIE, "34584", "pt-BR", 12))
                .thenReturn(new RelatedMediaResponse(ExternalSource.WIKIDATA, false, List.of()));

        mockMvc.perform(get("/v1/media/external/TMDB/MOVIE/34584/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("WIKIDATA"))
                .andExpect(jsonPath("$.incomplete").value(false))
                .andExpect(jsonPath("$.items").isArray());

        verify(externalMediaService).findRelations(
                ExternalSource.TMDB, MediaType.MOVIE, "34584", "pt-BR", 12);
    }

    @Test
    void validatesRelationLanguageAndLimit() throws Exception {
        mockMvc.perform(get("/v1/media/external/TMDB/MOVIE/34584/relations")
                        .param("language", "portuguese")
                        .param("maxResults", "41"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importsUsingAcceptLanguageLocale() throws Exception {
        mockMvc.perform(post("/v1/media/external/import")
                        .with(user("reader"))
                        .with(csrf())
                        .header("Accept-Language", "en-US")
                        .contentType("application/json")
                        .content("""
                                {
                                  "source": "TMDB",
                                  "externalId": "550",
                                  "mediaType": "MOVIE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Content-Language", "en-US"));

        verify(externalMediaService).importMedia(
                new ImportExternalMediaRequest(ExternalSource.TMDB, "550", MediaType.MOVIE),
                "en-US"
        );
    }
}
