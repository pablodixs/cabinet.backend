package com.scriptles.cabinet.status;

import java.time.Instant;
import java.util.List;

public record PublicStatusResponse(
        ProductHealth status,
        Instant updatedAt,
        List<ComponentStatus> components
) {
    public enum ProductHealth { OPERATIONAL, DEGRADED, DELAYED, OUTAGE }
    public enum ComponentKey { CATALOG, SEARCH, IMPORTS, METADATA_SYNC, EXTERNAL_PROVIDERS }
    public record ComponentStatus(ComponentKey key, ProductHealth status) {}
}
