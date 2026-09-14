package com.scriptles.cabinet.catalog.api;
import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus; import com.scriptles.cabinet.catalog.franchise.FranchiseType; import jakarta.validation.constraints.*; import java.util.UUID;
public record UpsertFranchiseRequest(@NotBlank @Size(max=180) String slug,@NotBlank @Size(max=300) String name,String originalName,String description,@NotNull FranchiseType type,CatalogEntityStatus status,String posterUrl,String backdropUrl,UUID parentId) {}
