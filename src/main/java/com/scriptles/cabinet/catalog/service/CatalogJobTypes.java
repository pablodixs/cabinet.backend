package com.scriptles.cabinet.catalog.service;

public final class CatalogJobTypes {
    public static final String TMDB_COLLECTION_HYDRATE = "TMDB_COLLECTION_HYDRATE";
    public static final String COLLECTION_ITEM_MATERIALIZE = "COLLECTION_ITEM_MATERIALIZE";
    public static final String COLLECTION_SYNC = "COLLECTION_SYNC";
    public static final String EXTERNAL_CATALOG_INDEX = "EXTERNAL_CATALOG_INDEX";
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String COMPLETED_WITH_WARNINGS = "COMPLETED_WITH_WARNINGS";
    public static final String FAILED = "FAILED";
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String RETRY = "RETRY";
    public static final String DEAD = "DEAD";
    public static final String CANCELLED = "CANCELLED";
    public static final int PRIORITY_DIRECT_DEMAND = 100;
    public static final int PRIORITY_RELATIONSHIP_DISCOVERY = 80;
    public static final int PRIORITY_STALE_REFRESH = 50;
    public static final int PRIORITY_BACKFILL = 20;

    private CatalogJobTypes() {
    }
}
