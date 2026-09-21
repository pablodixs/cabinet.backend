package com.scriptles.cabinet.catalog.api;

import java.util.UUID;

public record CatalogOperationAcceptedResponse(
        UUID operationId,
        String status,
        String provider,
        String externalId
) {
}
