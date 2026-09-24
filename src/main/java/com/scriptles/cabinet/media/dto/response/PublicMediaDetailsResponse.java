package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.CatalogStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.scriptles.cabinet.catalog.api.CollectionSummaryResponse;
import com.scriptles.cabinet.catalog.api.FranchiseSummaryResponse;

public record PublicMediaDetailsResponse(
        UUID id,
        String externalId,
        ExternalSource source,
        MediaType type,
        String title,
        String originalTitle,
        String creator,
        String description,
        String tagline,
        String coverUrl,
        String backdropUrl,
        String logoUrl,
        String externalUrl,
        LocalDate releaseDate,
        String originalLanguage,
        String countryCode,
        String wikidataId,
        Map<String, String> externalReferences,
        List<ExternalMediaDetailsResponse.GenreResponse> genres,
        boolean imported,
        Object details,
        String requestedLocale,
        String resolvedLocale,
        boolean translationFallback,
        CatalogStatus catalogStatus,
        List<CollectionSummaryResponse> collections,
        List<FranchiseSummaryResponse> franchises
) {
    public PublicMediaDetailsResponse(
            UUID id,String externalId,ExternalSource source,MediaType type,String title,String originalTitle,String creator,
            String description,String tagline,String coverUrl,String backdropUrl,String logoUrl,String externalUrl,
            LocalDate releaseDate,String originalLanguage,String countryCode,String wikidataId,Map<String,String> externalReferences,
            List<ExternalMediaDetailsResponse.GenreResponse> genres,
            boolean imported,Object details,String requestedLocale,String resolvedLocale,boolean translationFallback,CatalogStatus catalogStatus) {
        this(id,externalId,source,type,title,originalTitle,creator,description,tagline,coverUrl,backdropUrl,logoUrl,externalUrl,
                releaseDate,originalLanguage,countryCode,wikidataId,externalReferences,genres,imported,details,
                requestedLocale,resolvedLocale,translationFallback,catalogStatus,List.of(),List.of());
    }

    public PublicMediaDetailsResponse(
            UUID id,
            String externalId,
            ExternalSource source,
            MediaType type,
            String title,
            String originalTitle,
            String creator,
            String description,
            String tagline,
            String coverUrl,
            String backdropUrl,
            String logoUrl,
            String externalUrl,
            LocalDate releaseDate,
            String originalLanguage,
            String countryCode,
            String wikidataId,
            Map<String, String> externalReferences,
            List<ExternalMediaDetailsResponse.GenreResponse> genres,
            boolean imported,
            Object details
    ) {
        this(id, externalId, source, type, title, originalTitle, creator, description, tagline,
                coverUrl, backdropUrl, logoUrl, externalUrl, releaseDate, originalLanguage, countryCode,
                wikidataId, externalReferences, genres, imported, details,
                "pt-BR", "pt-BR", false, CatalogStatus.READY, List.of(), List.of());
    }
}
