package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.AwardPageResponse;
import com.scriptles.cabinet.media.dto.response.MediaExternalInfoResponse;
import com.scriptles.cabinet.media.dto.response.MediaCommunityResponse;
import com.scriptles.cabinet.media.dto.response.MoreByResponse;
import com.scriptles.cabinet.media.dto.response.SeasonEpisodesResponse;
import com.scriptles.cabinet.media.dto.response.PublicMediaDetailsResponse;
import com.scriptles.cabinet.media.enums.CatalogStatus;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.SupportedLocale;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalInfoSectionState;
import com.scriptles.cabinet.media.enums.AwardSectionState;
import com.scriptles.cabinet.media.enums.AwardSubjectType;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MoreByState;
import com.scriptles.cabinet.media.service.MediaExternalInfoService;
import com.scriptles.cabinet.media.service.MediaQueryService;
import com.scriptles.cabinet.media.service.MoreByService;
import com.scriptles.cabinet.media.service.SeasonEpisodeService;
import com.scriptles.cabinet.media.service.AwardQueryService;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaQueryController.class)
@Import(SecurityConfig.class)
class MediaQueryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaQueryService mediaQueryService;

    @MockitoBean
    private MediaExternalInfoService mediaExternalInfoService;

    @MockitoBean
    private MoreByService moreByService;

    @MockitoBean
    private SeasonEpisodeService seasonEpisodeService;

    @MockitoBean
    private AwardQueryService awardQueryService;

    @MockitoBean
    private CatalogLocaleResolver catalogLocaleResolver;

    @Test
    void returnsChildRatingsInCommunityContract() throws Exception {
        UUID mediaId = UUID.randomUUID();
        List<ExternalMediaDetailsResponse.RatingDistributionBucket> distribution =
                java.util.stream.IntStream.rangeClosed(1, 10)
                        .mapToObj(step -> new ExternalMediaDetailsResponse.RatingDistributionBucket(
                                step / 2.0, step == 9 ? 3 : 0))
                        .toList();
        when(mediaQueryService.findCommunity(mediaId)).thenReturn(new MediaCommunityResponse(
                0,
                List.of(),
                4.0,
                distribution,
                new MediaCommunityResponse.ChildRatingsResponse(
                        MediaType.TRACK, 4.5, 3, distribution),
                0,
                0,
                List.of()
        ));

        mockMvc.perform(get("/v1/media/{mediaId}/community", mediaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.childRatings.itemType").value("TRACK"))
                .andExpect(jsonPath("$.childRatings.averageRating").value(4.5))
                .andExpect(jsonPath("$.childRatings.ratingCount").value(3))
                .andExpect(jsonPath("$.childRatings.ratingDistribution[8].rating").value(4.5))
                .andExpect(jsonPath("$.childRatings.ratingDistribution[8].count").value(3));
    }

    @Test
    void resolvesAcceptLanguageAndReturnsRepresentationHeaders() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(catalogLocaleResolver.resolve(null, "en-US,en;q=0.9"))
                .thenReturn(SupportedLocale.EN_US);
        when(mediaQueryService.findDetails(mediaId, "en-US"))
                .thenReturn(details(mediaId, "en-US", "en-US", false));

        mockMvc.perform(get("/v1/media/{mediaId}", mediaId)
                        .header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Language", "en-US"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeaders("Vary")).contains("Accept-Language"))
                .andExpect(jsonPath("$.requestedLocale").value("en-US"))
                .andExpect(jsonPath("$.resolvedLocale").value("en-US"))
                .andExpect(jsonPath("$.translationFallback").value(false));

        verify(mediaQueryService).findDetails(mediaId, "en-US");
    }

    @Test
    void explicitLocaleOverridesHeaderAndDoesNotVaryByHeader() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(catalogLocaleResolver.resolve("pt-BR", "en-US")).thenReturn(SupportedLocale.PT_BR);
        when(mediaQueryService.findDetails(mediaId, "pt-BR"))
                .thenReturn(details(mediaId, "pt-BR", "pt-BR", false));

        mockMvc.perform(get("/v1/media/{mediaId}", mediaId)
                        .param("locale", "pt-BR")
                        .header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Language", "pt-BR"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeaders("Vary")).doesNotContain("Accept-Language"));
    }

    @Test
    void returnsSeasonEpisodesWithoutAuthentication() throws Exception {
        UUID seriesId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        var response = new SeasonEpisodesResponse(
                seriesId, "1396", seasonId, 1, 4.25, 8, null, 0, 10,
                List.of(new SeasonEpisodesResponse.EpisodeResponse(
                        episodeId, "62085", 1, "Piloto", null, null,
                        null, 58, 4.5, 6, null, true, false, 0)));
        when(seasonEpisodeService.find(seriesId, 1, "pt-BR", null)).thenReturn(response);

        mockMvc.perform(get("/v1/media/{seriesId}/seasons/{seasonNumber}/episodes", seriesId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seriesId").value(seriesId.toString()))
                .andExpect(jsonPath("$.averageRating").value(4.25))
                .andExpect(jsonPath("$.myRating").doesNotExist())
                .andExpect(jsonPath("$.episodes[0].id").value(episodeId.toString()))
                .andExpect(jsonPath("$.episodes[0].myRating").doesNotExist());

        verify(seasonEpisodeService).find(seriesId, 1, "pt-BR", null);
    }

    private PublicMediaDetailsResponse details(
            UUID mediaId,
            String requestedLocale,
            String resolvedLocale,
            boolean fallback
    ) {
        return new PublicMediaDetailsResponse(
                mediaId,
                mediaId.toString(),
                ExternalSource.MANUAL,
                MediaType.MOVIE,
                "Title",
                "Title",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "en",
                null,
                null,
                java.util.Map.of(),
                List.of(),
                List.of(),
                true,
                null,
                requestedLocale,
                resolvedLocale,
                fallback,
                CatalogStatus.READY
        );
    }

    @Test
    void returnsPublicPaginatedActorsWithDefaultPagination() throws Exception {
        UUID mediaId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        var actor = new ExternalMediaDetailsResponse.CreditResponse(
                personId,
                "Brad Pitt",
                CreditRole.ACTOR,
                "Tyler Durden",
                0,
                "https://image.tmdb.org/t/p/w500/pitt.jpg",
                ExternalSource.TMDB,
                "287"
        );
        when(mediaQueryService.findCredits(mediaId, CreditRole.ACTOR, 0, 20))
                .thenReturn(new PageResponse<>(List.of(actor), 0, 20, 1, 1));

        mockMvc.perform(get("/v1/media/{mediaId}/credits", mediaId)
                        .param("role", "ACTOR")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].personId").value(personId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Brad Pitt"))
                .andExpect(jsonPath("$.items[0].characterName").value("Tyler Durden"))
                .andExpect(jsonPath("$.items[0].imageUrl").value(
                        "https://image.tmdb.org/t/p/w500/pitt.jpg"))
                .andExpect(jsonPath("$.items[0].role").value("ACTOR"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(mediaQueryService).findCredits(mediaId, CreditRole.ACTOR, 0, 20);
    }

    @Test
    void validatesRoleAndPagination() throws Exception {
        UUID mediaId = UUID.randomUUID();

        mockMvc.perform(get("/v1/media/{mediaId}/credits", mediaId)
                        .param("role", "ACTOR")
                        .param("page", "-1")
                        .param("limit", "41"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsPublicMoreByWithDefaults() throws Exception {
        UUID mediaId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        when(moreByService.find(mediaId, "pt-BR", 12)).thenReturn(new MoreByResponse(
                MoreByState.EMPTY,
                CreditRole.DIRECTOR,
                new MoreByResponse.PersonResponse(personId, "David Fincher", null),
                false,
                List.of()
        ));

        mockMvc.perform(get("/v1/media/{mediaId}/more-by", mediaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EMPTY"))
                .andExpect(jsonPath("$.role").value("DIRECTOR"))
                .andExpect(jsonPath("$.person.id").value(personId.toString()))
                .andExpect(jsonPath("$.incomplete").value(false));

        verify(moreByService).find(mediaId, "pt-BR", 12);
    }

    @Test
    void validatesMoreByLanguageAndLimit() throws Exception {
        mockMvc.perform(get("/v1/media/{mediaId}/more-by", UUID.randomUUID())
                        .param("language", "portuguese")
                        .param("limit", "41"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsUnsupportedMoreByState() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(moreByService.find(mediaId, "pt-BR", 12)).thenReturn(new MoreByResponse(
                MoreByState.UNSUPPORTED, null, null, false, List.of()));

        mockMvc.perform(get("/v1/media/{mediaId}/more-by", mediaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.person").doesNotExist())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void returnsMediaNotFoundFromMoreBy() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(moreByService.find(mediaId, "pt-BR", 12)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada"));

        mockMvc.perform(get("/v1/media/{mediaId}/more-by", mediaId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void returnsAcceptedWhileExternalInfoIsLoading() throws Exception {
        UUID mediaId = UUID.randomUUID();
        MediaExternalInfoResponse response = new MediaExternalInfoResponse(
                mediaId,
                "BR",
                new MediaExternalInfoResponse.AvailabilitySection(
                        ExternalInfoSectionState.PENDING, null, null, List.of(), List.of()),
                new MediaExternalInfoResponse.RatingSection(
                        ExternalInfoSectionState.NOT_CONFIGURED, null, null, List.of())
        );
        when(mediaExternalInfoService.find(mediaId, "br")).thenReturn(response);

        mockMvc.perform(get("/v1/media/{mediaId}/external-info", mediaId)
                        .param("country", "br"))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Retry-After", "2"))
                .andExpect(jsonPath("$.countryCode").value("BR"))
                .andExpect(jsonPath("$.availability.state").value("PENDING"))
                .andExpect(jsonPath("$.ratings.state").value("NOT_CONFIGURED"));

        verify(mediaExternalInfoService).find(mediaId, "br");
    }

    @Test
    void returnsAcceptedWhileMediaAwardsAreLoading() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(awardQueryService.findMedia(mediaId, null, 0, 20)).thenReturn(new AwardPageResponse(
                mediaId, AwardSubjectType.MEDIA, AwardSectionState.PENDING,
                null, null, 0, 0, List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/v1/media/{mediaId}/awards", mediaId))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.subjectType").value("MEDIA"))
                .andExpect(jsonPath("$.state").value("PENDING"));
    }
}
