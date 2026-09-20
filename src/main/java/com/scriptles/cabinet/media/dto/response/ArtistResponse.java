package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;

import java.util.List;
import java.util.UUID;
import com.scriptles.cabinet.catalog.api.CollectionSummaryResponse;

public record ArtistResponse(
        UUID id,
        String name,
        String biography,
        String imageUrl,
        ExternalSource source,
        String externalId,
        long workCount,
        List<CreditRole> roles,
        List<CollectionSummaryResponse> discographies,
        Double averageRating
) {
    public ArtistResponse(UUID id,String name,String biography,String imageUrl,ExternalSource source,String externalId,long workCount,List<CreditRole> roles) {
        this(id,name,biography,imageUrl,source,externalId,workCount,roles,List.of(),null);
    }

    public ArtistResponse(UUID id, String name, String biography, String imageUrl, ExternalSource source,
                          String externalId, long workCount, List<CreditRole> roles,
                          List<CollectionSummaryResponse> discographies) {
        this(id, name, biography, imageUrl, source, externalId, workCount, roles, discographies, null);
    }
}
