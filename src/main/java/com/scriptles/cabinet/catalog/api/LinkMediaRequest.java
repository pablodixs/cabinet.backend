package com.scriptles.cabinet.catalog.api;
import com.scriptles.cabinet.catalog.franchise.FranchiseMediaRelationType; import com.scriptles.cabinet.media.enums.ExternalSource; import jakarta.validation.constraints.NotNull; import java.util.UUID;
public record LinkMediaRequest(@NotNull UUID mediaId,Integer position,FranchiseMediaRelationType relationType,Boolean sourceManaged,ExternalSource sourceProvider) {}
