package com.scriptles.cabinet.catalog.api;
import java.util.UUID;
public record CollectionSummaryResponse(UUID id,String slug,String title,String type,Integer position,Integer itemCount) {}
