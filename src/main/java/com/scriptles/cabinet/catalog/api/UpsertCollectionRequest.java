package com.scriptles.cabinet.catalog.api;
import com.scriptles.cabinet.catalog.collection.*; import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus; import jakarta.validation.constraints.*;
public record UpsertCollectionRequest(@NotBlank @Size(max=180) String slug,@NotBlank @Size(max=300) String title,String originalTitle,String description,@NotNull CollectionType type,@NotNull CollectionSourceMode sourceMode,CatalogEntityStatus status,String posterUrl,String backdropUrl) {}
