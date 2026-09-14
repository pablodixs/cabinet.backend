package com.scriptles.cabinet.catalog.api;
import java.util.UUID;
public record FranchiseSummaryResponse(UUID id,String slug,String name,String type) {}
